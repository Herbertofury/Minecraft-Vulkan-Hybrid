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
 * Creates the isolated compile-checked Vulkan backend without putting LWJGL
 * Vulkan on Forge/ModLauncher's module layer.
 *
 * The isolated loader is parent-first for Minecraft/Hari/LWJGL core, but
 * child-first for org.lwjgl.vulkan.*. This is deliberate: the merged client
 * renderer exposes a newer lwjgl-vulkan Jar-in-Jar to Forge's parent loader,
 * while the collision backend is compiled against the isolated 3.3.1 Vulkan
 * binding. Keeping only Vulkan child-first prevents cross-version binding
 * capture while still reusing Minecraft's launcher-owned LWJGL core on clients.
 * Dedicated servers fall back to the embedded 3.3.1 core/native JARs.
 */
public final class VkRuntime {
    private static final Logger LOGGER = LoggerFactory.getLogger("HariMT/VkRuntime");
    private static final String VERSION = "3.3.1";
    private static final String RESOURCE_ROOT = "META-INF/harimt-libs/";
    private static final String BACKEND_JAR = "harimt-vulkan-backend-" + VERSION + ".jar";
    private static final String BACKEND_CLASS = "com.axalotl.async.vulkanruntime.LwjglVulkanBackend";
    private static final String VULKAN_CHILD_PREFIX = "org.lwjgl.vulkan.";

    private static volatile URLClassLoader isolatedLoader;
    private static volatile Path extractionDir;

    private VkRuntime() {}

    public static VulkanCollisionBackend createBackend() throws Exception {
        ClassLoader loader = ensureIsolatedLoader();
        Class<?> type = Class.forName(BACKEND_CLASS, true, loader);
        Object instance = type.getDeclaredConstructor().newInstance();
        if (!(instance instanceof VulkanCollisionBackend backend)) {
            throw new ClassCastException("Isolated backend does not implement parent VulkanCollisionBackend: "
                    + type.getClassLoader());
        }
        return backend;
    }

    public static boolean isUsingEmbeddedRuntime() {
        return isolatedLoader != null;
    }

    private static synchronized ClassLoader ensureIsolatedLoader() throws IOException {
        if (isolatedLoader != null) return isolatedLoader;

        String nativeClassifier = nativeClassifier();
        if (nativeClassifier == null) {
            throw new IOException("Unsupported LWJGL native platform: "
                    + System.getProperty("os.name") + " / " + System.getProperty("os.arch"));
        }

        List<String> jars = List.of(
                BACKEND_JAR,
                "lwjgl-" + VERSION + ".jar",
                "lwjgl-vulkan-" + VERSION + ".jar",
                "lwjgl-" + VERSION + "-" + nativeClassifier + ".jar"
        );

        Path dir = Files.createTempDirectory("harimt-vulkan-" + VERSION + "-");
        dir.toFile().deleteOnExit();
        List<URL> urls = new ArrayList<>(jars.size());
        ClassLoader parent = VkRuntime.class.getClassLoader();

        try {
            for (String jarName : jars) {
                String resource = RESOURCE_ROOT + jarName;
                Path output = dir.resolve(jarName);
                try (InputStream in = parent.getResourceAsStream(resource)) {
                    if (in == null) throw new IOException("Missing embedded runtime resource " + resource);
                    Files.copy(in, output, StandardCopyOption.REPLACE_EXISTING);
                }
                output.toFile().deleteOnExit();
                urls.add(output.toUri().toURL());
            }
        } catch (IOException failure) {
            for (Path file : safeList(dir)) {
                try { Files.deleteIfExists(file); } catch (IOException ignored) { }
            }
            try { Files.deleteIfExists(dir); } catch (IOException ignored) { }
            throw failure;
        }

        isolatedLoader = new BackendClassLoader(urls.toArray(URL[]::new), parent);
        extractionDir = dir;
        LOGGER.info("Isolated compile-checked LWJGL {} Vulkan backend activated for {} ({})"
                        + " [child-first Vulkan, parent-first core]",
                VERSION, nativeClassifier, System.getProperty("os.arch"));
        return isolatedLoader;
    }

    private static final class BackendClassLoader extends URLClassLoader {
        BackendClassLoader(URL[] urls, ClassLoader parent) {
            super(urls, parent);
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            synchronized (getClassLoadingLock(name)) {
                Class<?> loaded = findLoadedClass(name);
                if (loaded == null && name.startsWith(VULKAN_CHILD_PREFIX)) {
                    try {
                        loaded = findClass(name);
                    } catch (ClassNotFoundException ignored) {
                        // Fall through to normal parent-first resolution if the
                        // isolated runtime does not contain this Vulkan class.
                    }
                }
                if (loaded == null) {
                    loaded = super.loadClass(name, false);
                }
                if (resolve) resolveClass(loaded);
                return loaded;
            }
        }
    }

    private static List<Path> safeList(Path dir) {
        try (var stream = Files.list(dir)) {
            return stream.toList();
        } catch (IOException ignored) {
            return List.of();
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
