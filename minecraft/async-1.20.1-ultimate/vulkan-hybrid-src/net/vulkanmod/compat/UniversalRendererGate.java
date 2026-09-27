package net.vulkanmod.compat;

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

    private static final Pattern MOD_ID = Pattern.compile("(?m)^\\s*modId\\s*=\\s*[\\\"']([^\\\"']+)[\\\"']");
    private static final String GL_PREFIX = "org/lwjgl/opengl/";
    private static final String CONTRACT_RESOURCE = "/assets/vulkanmod/compat/harimt_supported_gl_methods.properties";

    private static final Set<String> RENDERER_CONFLICTS = Set.of(
            "embeddium", "rubidium", "sodium", "oculus", "iris", "optifine", "optifabric",
            "canvas", "distanthorizons", "immediatelyfast", "exordium", "lazurite", "indium"
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

        Path mods = Path.of(System.getProperty("user.dir", ".")).toAbsolutePath().normalize().resolve("mods");
        if (!Files.isDirectory(mods)) return new Decision(true, "no external mods directory");

        List<Path> jars;
        try (var stream = Files.list(mods)) {
            jars = stream.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".jar"))
                    .sorted().toList();
        } catch (IOException e) {
            return new Decision(false, "cannot inspect mods directory: " + e.getClass().getSimpleName());
        }

        String signature = signature(jars);
        boolean useCache = Boolean.parseBoolean(System.getProperty(CACHE_PROPERTY, "true"));
        if (useCache) {
            Decision cached = readCache(signature);
            if (cached != null) return cached;
        }

        TreeSet<String> conflicts = new TreeSet<>();
        TreeSet<String> unsupported = new TreeSet<>();
        TreeSet<String> unreadable = new TreeSet<>();

        for (Path jar : jars) {
            try (ZipFile zip = new ZipFile(jar.toFile())) {
                Set<String> ids = readModIds(zip);
                if (ids.contains("harimt") || ids.contains("vulkanmod")) continue;
                for (String id : ids) if (RENDERER_CONFLICTS.contains(id)) conflicts.add(id);
                if (!Collections.disjoint(ids, RENDERER_CONFLICTS)) continue;

                var entries = zip.entries();
                while (entries.hasMoreElements()) {
                    ZipEntry entry = entries.nextElement();
                    if (entry.isDirectory() || !entry.getName().endsWith(".class")) continue;
                    try (InputStream input = zip.getInputStream(entry)) {
                        for (MethodRef ref : openGlMethodRefs(input)) {
                            Set<String> methods = contracts.get(ref.owner());
                            if (methods == null || !methods.contains(ref.name())) {
                                unsupported.add(ref.owner() + "#" + ref.name() + "@" + jar.getFileName());
                            }
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
        return ids;
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

    private static String signature(List<Path> jars) {
        StringBuilder builder = new StringBuilder();
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
