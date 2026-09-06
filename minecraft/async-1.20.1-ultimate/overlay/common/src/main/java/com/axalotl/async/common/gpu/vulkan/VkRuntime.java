package com.axalotl.async.common.gpu.vulkan;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Loads LWJGL Vulkan without exposing it to Forge/ModLauncher's module layer.
 *
 * Minecraft clients already provide LWJGL core + platform natives but do not
 * normally provide lwjgl-vulkan. Dedicated Forge servers may provide neither.
 * The release JAR therefore embeds ordinary dependency JAR resources (not
 * JarJar modules). This parent-first child loader reuses Minecraft's LWJGL when
 * present and supplies the embedded core/native runtime when it is not.
 *
 * Keeping the dependency off the module layer is important: lwjgl-vulkan's
 * module descriptor requires org.lwjgl, which caused a packaged dedicated
 * server to fail before mod initialization when only lwjgl-vulkan was JarJar'd.
 */
public final class VkRuntime {
    private static final Logger LOGGER = LoggerFactory.getLogger("HariMT/VkRuntime");
    private static final String VERSION = "3.3.1";
    private static final String RESOURCE_ROOT = "META-INF/harimt-libs/";

    private static volatile ClassLoader isolatedLoader;
    private static volatile Path extractionDir;

    private VkRuntime() {}

    public static Class<?> load(String className) throws ClassNotFoundException {
        ClassLoader parent = VkRuntime.class.getClassLoader();

        // Parent-first: on a normal Minecraft client this reuses the launcher's
        // already-selected LWJGL core/natives and avoids duplicate core classes.
        try {
            return Class.forName(className, true, parent);
        } catch (ClassNotFoundException | NoClassDefFoundError ignored) {
            // Dedicated server / missing Vulkan module: activate isolated runtime.
        }

        ClassLoader loader = ensureIsolatedLoader();
        return Class.forName(className, true, loader);
    }

    public static boolean isUsingEmbeddedRuntime() {
        return isolatedLoader != null;
    }

    private static synchronized ClassLoader ensureIsolatedLoader() throws ClassNotFoundException {
        if (isolatedLoader != null) return isolatedLoader;

        String nativeClassifier = nativeClassifier();
        if (nativeClassifier == null) {
            throw new ClassNotFoundException("Unsupported LWJGL native platform: "
                    + System.getProperty("os.name") + " / " + System.getProperty("os.arch"));
        }

        List<String> jars = List.of(
                "lwjgl-" + VERSION + ".jar",
                "lwjgl-vulkan-" + VERSION + ".jar",
                "lwjgl-" + VERSION + "-" + nativeClassifier + ".jar"
        );

        try {
            Path dir = Files.createTempDirectory("harimt-lwjgl-" + VERSION + "-");
            dir.toFile().deleteOnExit();
            List<URL> urls = new ArrayList<>(jars.size());

            ClassLoader resourceLoader = VkRuntime.class.getClassLoader();
            for (String jarName : jars) {
                String resource = RESOURCE_ROOT + jarName;
                Path output = dir.resolve(jarName);
                try (InputStream in = resourceLoader.getResourceAsStream(resource)) {
                    if (in == null) {
                        throw new IOException("Missing embedded runtime resource " + resource);
                    }
                    Files.copy(in, output, StandardCopyOption.REPLACE_EXISTING);
                }
                output.toFile().deleteOnExit();
                urls.add(output.toUri().toURL());
            }

            isolatedLoader = new URLClassLoader(urls.toArray(URL[]::new), resourceLoader);
            extractionDir = dir;
            LOGGER.info("Embedded LWJGL {} Vulkan runtime activated for {} ({})",
                    VERSION, nativeClassifier, System.getProperty("os.arch"));
            return isolatedLoader;
        } catch (IOException e) {
            throw new ClassNotFoundException("Unable to activate embedded LWJGL Vulkan runtime", e);
        }
    }

    private static String nativeClassifier() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        String arch = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);
        boolean arm64 = arch.contains("aarch64") || arch.contains("arm64");
        boolean arm32 = !arm64 && arch.startsWith("arm");
        boolean x86 = arch.equals("x86") || arch.matches("i[3-6]86");

        if (os.contains("win")) {
            if (arm64) return "natives-windows-arm64";
            if (x86) return "natives-windows-x86";
            return "natives-windows";
        }
        if (os.contains("mac") || os.contains("darwin")) {
            return arm64 ? "natives-macos-arm64" : "natives-macos";
        }
        if (os.contains("linux")) {
            if (arm64) return "natives-linux-arm64";
            if (arm32) return "natives-linux-arm32";
            return "natives-linux";
        }
        return null;
    }
}
