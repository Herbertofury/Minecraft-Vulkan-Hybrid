package com.axalotl.async.vulkanruntime;

import com.axalotl.async.common.gpu.vulkan.VulkanCollisionBackend;
import org.lwjgl.vulkan.VK;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Simulates Minecraft/another mod owning LWJGL's process-global Vulkan function
 * provider before HMT starts. HMT must destroy only its own instance/device and
 * leave that shared provider untouched across integrated-server stop/restart.
 */
public final class VulkanLoaderOwnershipProbe {
    private VulkanLoaderOwnershipProbe() { }

    private static void verifyCompute(LwjglVulkanBackend backend, byte[] spirv) {
        if (!backend.initialize(spirv)) {
            throw new AssertionError("backend initialize failed: " + backend.lastError());
        }
        float[] minX = {0.0f, 0.5f, 10.0f};
        float[] minY = {0.0f, 0.5f, 10.0f};
        float[] minZ = {0.0f, 0.5f, 10.0f};
        float[] maxX = {1.0f, 1.5f, 11.0f};
        float[] maxY = {1.0f, 1.5f, 11.0f};
        float[] maxZ = {1.0f, 1.5f, 11.0f};
        VulkanCollisionBackend.Result result = backend.compute(
                minX, minY, minZ, maxX, maxY, maxZ, 3, 16);
        if (result.overflow()
                || result.pairCount() != 1
                || result.pairsA().length != 1
                || result.pairsB().length != 1
                || result.pairsA()[0] != 0
                || result.pairsB()[0] != 1) {
            throw new AssertionError("expected exact conservative pair (0,1), got pairCount="
                    + result.pairCount());
        }
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            throw new IllegalArgumentException("usage: VulkanLoaderOwnershipProbe <shader.spv>");
        }
        System.setProperty("harimt.vulkan.allowCpuDevice", "true");
        byte[] spirv = Files.readAllBytes(Path.of(args[0]));

        // Referencing VK may auto-initialize the global provider unless LWJGL was
        // launched in explicit-init mode. Adopt that provider when present; only
        // create it ourselves when it is genuinely absent.
        Object externalProvider;
        try {
            externalProvider = VK.getFunctionProvider();
        } catch (IllegalStateException notInitialized) {
            externalProvider = null;
        }
        if (externalProvider == null) {
            VK.create();
            externalProvider = VK.getFunctionProvider();
        }
        if (externalProvider == null) {
            throw new AssertionError("external Vulkan provider was not created");
        }
        System.out.println("VULKAN_EXTERNAL_PROVIDER_READY");

        try {
            LwjglVulkanBackend first = new LwjglVulkanBackend();
            try {
                verifyCompute(first, spirv);
            } finally {
                first.shutdown();
            }
            if (VK.getFunctionProvider() != externalProvider) {
                throw new AssertionError("HMT shutdown destroyed/replaced external Vulkan provider");
            }
            System.out.println("VULKAN_EXTERNAL_PROVIDER_SURVIVED_FIRST_SHUTDOWN");

            // Integrated-server style restart in the same JVM must still work.
            LwjglVulkanBackend second = new LwjglVulkanBackend();
            try {
                verifyCompute(second, spirv);
            } finally {
                second.shutdown();
            }
            if (VK.getFunctionProvider() != externalProvider) {
                throw new AssertionError("HMT restart/shutdown destroyed/replaced external Vulkan provider");
            }
            System.out.println("VULKAN_BACKEND_RESTART_WITH_SHARED_PROVIDER_PASS");
            System.out.println("VULKAN_LOADER_OWNERSHIP_PASS");
        } finally {
            // The standalone probe process is the simulated external owner, so it
            // alone may release the process-global provider at process exit.
            VK.destroy();
        }
    }
}
