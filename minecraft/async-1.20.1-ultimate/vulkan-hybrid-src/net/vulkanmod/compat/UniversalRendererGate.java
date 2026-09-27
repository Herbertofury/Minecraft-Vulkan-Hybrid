package net.vulkanmod.compat;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

/**
 * Fail-closed session renderer selector for HariMultiThread Ultimate.
 *
 * <p>Auto mode enables Vulkan only when no mutually-exclusive renderer is installed and every
 * direct LWJGL OpenGL method reference found in installed mods is covered by the merged VulkanMod
 * compatibility mixins. Any unknown owner/method selects Hari's proven OpenGL fallback before
 * Vulkan mixins apply. Results are cached by the mods-folder signature.</p>
 */
public final class UniversalRendererGate {
    public static final String MODE_PROPERTY = "harimt.vulkan.mode"; // auto | force | off
    public static final String CACHE_PROPERTY = "harimt.vulkan.compatCache"; // default true
    private static final String CACHE_SCHEMA = "2.4.0-vulkan-gate-v6";

    private static final Pattern MOD_ID = Pattern.compile("(?m)^\\s*modId\\s*=\\s*[\\\"']([^\\\"']+)[\\\"']");
    private static final Pattern FABRIC_ID = Pattern.compile("\\\"id\\\"\\\\s*:\\\\s*\\\"([^\\\"]+)\\\"");
    private static final String GL_PREFIX = "org/lwjgl/opengl/";
    private static final String CONTRACT_RESOURCE = "/assets/vulkanmod/compat/harimt_supported_gl_methods.properties";

    private static final Set<String> RENDERER_CONFLICTS = Set.of(
            "embeddium", "rubidium", "sodium", "oculus", "iris", "optifine", "optifabric",
            "canvas", "distanthorizons", "immediatelyfast", "exordium", "lazurite", "indium",
            "continuity"
    );

    private static final AtomicBoolean LOGGED = new AtomicBoolean();
    private static volatile Decision decision;

    private UniversalRendererGate() {}

    public static boolean vulkanRendererEnabled() { return decision().enabled(); }
    public static String reason() { return decision().reason(); }

    public static Decision decision() {
        Decision current = decision;
        if (current == null) {
            synchronized (UniversalRendererGate.class) {
                if (decision == null) decision = evaluate();
                current = decision;
            }
        }
        if (LOGGED.compareAndSet(false, true)) {
            System.out.println("[Hari/Vulkan] renderer=" + (current.enabled() ? "VULKAN" : "OPENGL_FALLBACK")
                    + " reason=" + current.reason());
        }
        return current;
    }

    private static Decision evaluate() {
        String mode = System.getProperty(MODE_PROPERTY, "auto").trim().toLowerCase(Locale.ROOT);
        if ("off".equals(mode)) return new Decision(false, "forced off");
        if ("force".equals(mode)) return new Decision(true, "forced on; compatibility gate bypassed");
        if (!"auto".equals(mode)) return new Decision(false, "invalid harimt.vulkan.mode=" + mode);

        Map<String, Set<String>> contracts = loadContracts();
        if (contracts.isEmpty()) return new Decision(false, "OpenGL translation contract missing");

        Set<String> forgeLoadedIds = forgeLoadedModIds();
        boolean indigoOnClasspath = indigoRendererResourcePresent();
        TreeSet<String> earlyConflicts = rendererConflicts(forgeLoadedIds);
        if (indigoOnClasspath) earlyConflicts.add("fabric-renderer-indigo");

        Path mods = Path.of(System.getProperty("user.dir", ".")).toAbsolutePath().normalize().resolve("mods");
        if (!Files.isDirectory(mods)) {
            if (!earlyConflicts.isEmpty()) {
                return new Decision(false, "mutually-exclusive renderer(s): " + String.join(",", earlyConflicts));
            }
            return new Decision(true, "no external mods directory and no loaded renderer conflicts");
        }

        List<Path> jars;
        try (var stream = Files.list(mods)) {
            jars = stream.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".jar"))
                    .sorted().toList();
        } catch (IOException e) {
            return new Decision(false, "cannot inspect mods directory: " + e.getClass().getSimpleName());
        }

        String signature = signature(jars, forgeLoadedIds, indigoOnClasspath);
        boolean useCache = Boolean.parseBoolean(System.getProperty(CACHE_PROPERTY, "true"));
        if (useCache) {
            Decision cached = readCache(signature);
            if (cached != null) return cached;
        }

        TreeSet<String> conflicts = new TreeSet<>(earlyConflicts);
        TreeSet<String> unsupported = new TreeSet<>();
        TreeSet<String> unreadable = new TreeSet<>();

        for (Path jar : jars) {
            try (ZipFile zip = new ZipFile(jar.toFile())) {
                Set<String> ids = readModIds(zip);
                if (ids.contains("harimt") || ids.contains("vulkanmod")) continue;
                for (String id : ids) if (RENDERER_CONFLICTS.contains(id)) conflicts.add(id);

                // Forgified Fabric API's Indigo renderer decides whether to load from static
                // mod metadata during its Mixin config phase. Because Hari selects Vulkan
                // dynamically after inspecting the pack, claiming that metadata unconditionally
                // would break the OpenGL fallback. If Indigo is present (including Jar-in-Jar),
                // fail closed to Hari's OpenGL renderer instead of racing two FRAPI renderers.
                if (containsIndigoRenderer(zip)) {
                    conflicts.add("fabric-renderer-indigo");
                }
                if (!Collections.disjoint(ids, RENDERER_CONFLICTS)
                        || conflicts.contains("fabric-renderer-indigo")) continue;

                var entries = zip.entries();
                while (entries.hasMoreElements()) {
                    ZipEntry entry = entries.nextElement();
                    if (entry.isDirectory()) continue;
                    if (entry.getName().endsWith(".class")) {
                        try (InputStream input = zip.getInputStream(entry)) {
                            collectUnsupported(openGlMethodRefs(input), contracts, unsupported, jar.getFileName().toString());
                        }
                    } else if (entry.getName().endsWith(".jar")) {
                        try (InputStream input = zip.getInputStream(entry)) {
                            scanNestedJar(input, jar.getFileName() + "!" + entry.getName(), contracts, unsupported, 0);
                        }
                    }
                }
            } catch (IOException | RuntimeException e) {
                unreadable.add(jar.getFileName() + ":" + e.getClass().getSimpleName());
            }
        }

        Decision result;
        if (!conflicts.isEmpty()) {
            result = new Decision(false, "mutually-exclusive renderer(s): " + String.join(",", conflicts));
        } else if (!unsupported.isEmpty()) {
            result = new Decision(false, "untranslated OpenGL call(s): " + summarize(unsupported, 12));
        } else if (!unreadable.isEmpty()) {
            result = new Decision(false, "unreadable mod jar(s): " + summarize(unreadable, 8));
        } else {
            result = new Decision(true, "all discovered direct OpenGL calls are translated");
        }
        if (useCache) writeCache(signature, result);
        return result;
    }

    private static String summarize(Set<String> values, int max) {
        List<String> all = new ArrayList<>(values);
        if (all.size() <= max) return String.join(",", all);
        return String.join(",", all.subList(0, max)) + ",...+" + (all.size() - max);
    }

    private static Map<String, Set<String>> loadContracts() {
        Properties properties = new Properties();
        try (InputStream input = UniversalRendererGate.class.getResourceAsStream(CONTRACT_RESOURCE)) {
            if (input == null) return Map.of();
            properties.load(input);
        } catch (IOException e) {
            return Map.of();
        }
        Map<String, Set<String>> result = new HashMap<>();
        for (String owner : properties.stringPropertyNames()) {
            HashSet<String> methods = new HashSet<>();
            for (String method : properties.getProperty(owner, "").split(",")) {
                if (!method.isBlank()) methods.add(method.trim());
            }
            result.put(owner, Collections.unmodifiableSet(methods));
        }
        return Collections.unmodifiableMap(result);
    }

    private static TreeSet<String> rendererConflicts(Set<String> ids) {
        TreeSet<String> conflicts = new TreeSet<>();
        for (String id : ids) {
            if (RENDERER_CONFLICTS.contains(id)) conflicts.add(id);
        }
        return conflicts;
    }

    /**
     * Forge exposes the loading list during Mixin config evaluation (Indigo itself relies
     * on the same phase). Reflection keeps the standalone GateProbe independent of Forge.
     */
    private static Set<String> forgeLoadedModIds() {
        TreeSet<String> ids = new TreeSet<>();
        try {
            Class<?> loadingModList = Class.forName("net.minecraftforge.fml.loading.LoadingModList");
            Object list = loadingModList.getMethod("get").invoke(null);
            Object mods = loadingModList.getMethod("getMods").invoke(list);
            Class<?> modInfoType = Class.forName("net.minecraftforge.forgespi.language.IModInfo");
            var getModId = modInfoType.getMethod("getModId");
            if (mods instanceof Iterable<?> iterable) {
                for (Object info : iterable) {
                    try {
                        Object id = getModId.invoke(info);
                        if (id != null) ids.add(String.valueOf(id).toLowerCase(Locale.ROOT));
                    } catch (ReflectiveOperationException ignored) {
                        // Fail closed is handled by physical mod scanning; one malformed
                        // metadata record should not discard other discoverable IDs.
                    }
                }
            }
        } catch (Throwable ignored) {
            // Expected in the standalone compile gate. Real Forge runs provide this class.
        }
        return ids;
    }

    private static boolean indigoRendererResourcePresent() {
        ClassLoader loader = UniversalRendererGate.class.getClassLoader();
        return loader != null && loader.getResource(
                "net/fabricmc/fabric/impl/client/indigo/IndigoMixinConfigPlugin.class") != null;
    }

    private static Set<String> readModIds(ZipFile zip) throws IOException {
        ZipEntry entry = zip.getEntry("META-INF/mods.toml");
        if (entry == null) return Set.of();
        String toml;
        try (InputStream input = zip.getInputStream(entry)) {
            toml = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }

        HashSet<String> ids = new HashSet<>();
        boolean insideModsTable = false;
        for (String rawLine : toml.split("\\R")) {
            String line = rawLine.trim();
            if (line.startsWith("[[")) {
                insideModsTable = line.equalsIgnoreCase("[[mods]]");
                continue;
            }
            if (!insideModsTable) continue;
            Matcher matcher = MOD_ID.matcher(line);
            if (matcher.find()) {
                ids.add(matcher.group(1).toLowerCase(Locale.ROOT));
            }
        }
        ZipEntry fabricEntry = zip.getEntry("fabric.mod.json");
        if (fabricEntry != null) {
            String json;
            try (InputStream input = zip.getInputStream(fabricEntry)) {
                json = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            }
            Matcher matcher = FABRIC_ID.matcher(json);
            if (matcher.find()) {
                ids.add(matcher.group(1).toLowerCase(Locale.ROOT));
            }
        }
        return ids;
    }

    private static final String INDIGO_PLUGIN_CLASS =
            "net/fabricmc/fabric/impl/client/indigo/IndigoMixinConfigPlugin.class";
    private static final String INDIGO_MIXINS = "fabric-renderer-indigo.mixins.json";

    private static boolean containsIndigoRenderer(ZipFile zip) throws IOException {
        var entries = zip.entries();
        while (entries.hasMoreElements()) {
            ZipEntry entry = entries.nextElement();
            if (entry.isDirectory()) continue;
            String name = entry.getName();
            if (INDIGO_PLUGIN_CLASS.equals(name) || name.endsWith("/" + INDIGO_PLUGIN_CLASS)
                    || INDIGO_MIXINS.equals(name) || name.endsWith("/" + INDIGO_MIXINS)) {
                return true;
            }
            if (name.endsWith(".jar")) {
                try (InputStream input = zip.getInputStream(entry)) {
                    if (containsIndigoRenderer(input, 0)) return true;
                }
            }
        }
        return false;
    }

    private static boolean containsIndigoRenderer(InputStream raw, int depth) throws IOException {
        if (depth > 3) return false;
        try (ZipInputStream zin = new ZipInputStream(raw)) {
            ZipEntry entry;
            while ((entry = zin.getNextEntry()) != null) {
                if (entry.isDirectory()) continue;
                String name = entry.getName();
                if (INDIGO_PLUGIN_CLASS.equals(name) || name.endsWith("/" + INDIGO_PLUGIN_CLASS)
                        || INDIGO_MIXINS.equals(name) || name.endsWith("/" + INDIGO_MIXINS)) {
                    return true;
                }
                if (name.endsWith(".jar")) {
                    byte[] nested = zin.readAllBytes();
                    if (containsIndigoRenderer(new ByteArrayInputStream(nested), depth + 1)) return true;
                }
            }
        }
        return false;
    }

    private static void collectUnsupported(List<MethodRef> refs, Map<String, Set<String>> contracts,
                                           Set<String> unsupported, String source) {
        for (MethodRef ref : refs) {
            Set<String> methods = contracts.get(ref.owner());
            if (methods == null || !methods.contains(ref.name())) {
                unsupported.add(ref.owner() + "#" + ref.name() + "@" + source);
            }
        }
    }

    private static void scanNestedJar(InputStream raw, String source, Map<String, Set<String>> contracts,
                                      Set<String> unsupported, int depth) throws IOException {
        if (depth > 3) {
            unsupported.add("nested-jar-depth@" + source);
            return;
        }
        try (ZipInputStream zin = new ZipInputStream(raw)) {
            ZipEntry entry;
            while ((entry = zin.getNextEntry()) != null) {
                if (entry.isDirectory()) continue;
                if (entry.getName().endsWith(".class")) {
                    collectUnsupported(openGlMethodRefs(zin), contracts, unsupported, source + "!" + entry.getName());
                } else if (entry.getName().endsWith(".jar")) {
                    byte[] nested = zin.readAllBytes();
                    scanNestedJar(new ByteArrayInputStream(nested), source + "!" + entry.getName(),
                            contracts, unsupported, depth + 1);
                }
            }
        }
    }

    /** Minimal class-file constant-pool reader for direct Methodref/InterfaceMethodref entries. */
    private static List<MethodRef> openGlMethodRefs(InputStream raw) throws IOException {
        DataInputStream in = new DataInputStream(raw);
        if (in.readInt() != 0xCAFEBABE) return List.of();
        in.readUnsignedShort();
        in.readUnsignedShort();
        int count = in.readUnsignedShort();
        byte[] tag = new byte[count];
        int[] a = new int[count];
        int[] b = new int[count];
        String[] utf = new String[count];

        for (int i = 1; i < count; i++) {
            int t = in.readUnsignedByte();
            tag[i] = (byte) t;
            switch (t) {
                case 1 -> utf[i] = in.readUTF();
                case 3, 4 -> in.skipBytes(4);
                case 5, 6 -> { in.skipBytes(8); i++; }
                case 7, 8, 16, 19, 20 -> a[i] = in.readUnsignedShort();
                case 9, 10, 11, 12, 17, 18 -> { a[i] = in.readUnsignedShort(); b[i] = in.readUnsignedShort(); }
                case 15 -> { in.readUnsignedByte(); in.readUnsignedShort(); }
                default -> throw new IOException("unknown classfile constant tag " + t);
            }
        }

        ArrayList<MethodRef> refs = new ArrayList<>();
        for (int i = 1; i < count; i++) {
            if (tag[i] != 10 && tag[i] != 11) continue;
            int classIndex = a[i];
            int nameTypeIndex = b[i];
            if (classIndex <= 0 || classIndex >= count || tag[classIndex] != 7) continue;
            int ownerUtf = a[classIndex];
            if (ownerUtf <= 0 || ownerUtf >= count) continue;
            String owner = utf[ownerUtf];
            if (owner == null || !owner.startsWith(GL_PREFIX)) continue;
            if (nameTypeIndex <= 0 || nameTypeIndex >= count || tag[nameTypeIndex] != 12) continue;
            int nameUtf = a[nameTypeIndex];
            if (nameUtf <= 0 || nameUtf >= count || utf[nameUtf] == null) continue;
            refs.add(new MethodRef(owner.substring(GL_PREFIX.length()), utf[nameUtf]));
        }
        return refs;
    }

    private static String signature(List<Path> jars, Set<String> forgeLoadedIds, boolean indigoOnClasspath) {
        StringBuilder builder = new StringBuilder(CACHE_SCHEMA).append(';');
        builder.append("forgeIds=");
        for (String id : new TreeSet<>(forgeLoadedIds)) builder.append(id).append(',');
        builder.append(";indigoClasspath=").append(indigoOnClasspath).append(';');
        for (Path path : jars) {
            try {
                builder.append(path.getFileName()).append(':').append(Files.size(path)).append(':')
                        .append(Files.getLastModifiedTime(path).toMillis()).append(';');
            } catch (IOException e) {
                builder.append(path.getFileName()).append(":?:?;");
            }
        }
        return Integer.toHexString(builder.toString().hashCode()) + ":" + builder.length();
    }

    private static Path cachePath() {
        return Path.of(System.getProperty("user.dir", ".")).toAbsolutePath().normalize()
                .resolve("config").resolve("harimt-vulkan-compat.properties");
    }

    private static Decision readCache(String signature) {
        Path path = cachePath();
        if (!Files.isRegularFile(path)) return null;
        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(path)) { properties.load(input); }
        catch (IOException e) { return null; }
        if (!signature.equals(properties.getProperty("signature"))) return null;
        String enabled = properties.getProperty("enabled");
        String reason = properties.getProperty("reason");
        if (enabled == null || reason == null) return null;
        return new Decision(Boolean.parseBoolean(enabled), "cached: " + reason);
    }

    private static void writeCache(String signature, Decision decision) {
        Path path = cachePath();
        Properties properties = new Properties();
        properties.setProperty("signature", signature);
        properties.setProperty("enabled", Boolean.toString(decision.enabled()));
        properties.setProperty("reason", decision.reason());
        try {
            Files.createDirectories(path.getParent());
            try (var output = Files.newOutputStream(path)) {
                properties.store(output, "Hari Vulkan compatibility decision");
            }
        } catch (IOException ignored) {}
    }

    private record MethodRef(String owner, String name) {}
    public record Decision(boolean enabled, String reason) {}
}
