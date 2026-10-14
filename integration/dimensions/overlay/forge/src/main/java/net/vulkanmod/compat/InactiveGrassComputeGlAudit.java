package net.vulkanmod.compat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

/** Only an unreachable Iris compute class is excluded; normal grass shaders use Vulkan. */
public final class InactiveGrassComputeGlAudit {
    static final String CLASS = "com/leonardoinc22/shortgrass/client/render/GrassComputeAnimator.class";
    static final String PIN = "91006e8220e65572d7e9e1e54b6058526c4adf908faedc750c9f19f13623df8c";
    private static final List<String> PROVIDERS = List.of(
            "net/irisshaders/iris/api/v0/IrisApi.class",
            "net/coderbot/iris/api/v0/IrisApi.class");
    enum State { ABSENT, PRESENT, UNKNOWN }
    private final State state;
    private final Map<Path, String> hashes;

    private InactiveGrassComputeGlAudit(State state, Map<Path, String> hashes) {
        this.state = state;
        this.hashes = Map.copyOf(hashes);
    }

    public static InactiveGrassComputeGlAudit inspect(List<Path> jars, Set<String> loadedIds) {
        Map<Path, String> hashes = new LinkedHashMap<>();
        for (Path jar : jars) {
            try (ZipFile zip = new ZipFile(jar.toFile())) {
                if (zip.getEntry(CLASS) != null) hashes.put(jar, sha256(jar));
            } catch (IOException | RuntimeException unavailable) {
                // Unreadable input remains subject to the ordinary gate.
            }
        }
        if (hashes.isEmpty()) return new InactiveGrassComputeGlAudit(State.UNKNOWN, hashes);
        return new InactiveGrassComputeGlAudit(providerState(jars, loadedIds), hashes);
    }

    public boolean excludes(Path jar, String classEntry) {
        return allows(hashes.get(jar), classEntry, state);
    }

    static boolean allows(String digest, String classEntry, State state) {
        return state == State.ABSENT && CLASS.equals(classEntry) && PIN.equals(digest);
    }

    public String cacheKey() {
        StringBuilder out = new StringBuilder(state.name());
        hashes.entrySet().stream().sorted(Map.Entry.comparingByKey())
                .forEach(e -> out.append(';').append(e.getKey().getFileName()).append('=').append(e.getValue()));
        return out.toString();
    }

    private static State providerState(List<Path> jars, Set<String> loadedIds) {
        if (loadedIds.contains("oculus") || loadedIds.contains("iris")) return State.PRESENT;
        try {
            ClassLoader loader = InactiveGrassComputeGlAudit.class.getClassLoader();
            for (String provider : PROVIDERS) {
                if (loader.getResource(provider) != null) return State.PRESENT;
                try {
                    Class.forName(provider.substring(0, provider.length() - 6).replace('/', '.'), false, loader);
                    return State.PRESENT;
                } catch (ClassNotFoundException absent) {
                    // The audited IrisCompat tries these exact two APIs.
                }
            }
            for (Path jar : jars) {
                try (ZipFile zip = new ZipFile(jar.toFile())) {
                    for (String provider : PROVIDERS) if (zip.getEntry(provider) != null) return State.PRESENT;
                    var entries = zip.entries();
                    while (entries.hasMoreElements()) {
                        var entry = entries.nextElement();
                        if (!entry.isDirectory() && entry.getName().endsWith(".jar")) {
                            try (InputStream input = zip.getInputStream(entry)) {
                                if (nestedProvider(input, 0)) return State.PRESENT;
                            }
                        }
                    }
                }
            }
            return State.ABSENT;
        } catch (IOException | LinkageError | RuntimeException unavailable) {
            return State.UNKNOWN;
        }
    }

    private static boolean nestedProvider(InputStream input, int depth) throws IOException {
        if (depth > 3) throw new IOException("Shader-provider nesting exceeds audited depth");
        ZipInputStream zip = new ZipInputStream(input);
        java.util.zip.ZipEntry entry;
        while ((entry = zip.getNextEntry()) != null) {
            if (PROVIDERS.contains(entry.getName())) return true;
            if (!entry.isDirectory() && entry.getName().endsWith(".jar") && nestedProvider(zip, depth + 1)) return true;
        }
        return false;
    }

    private static String sha256(Path jar) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream input = Files.newInputStream(jar)) {
                byte[] bytes = new byte[65536];
                int count;
                while ((count = input.read(bytes)) != -1) digest.update(bytes, 0, count);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }
}
