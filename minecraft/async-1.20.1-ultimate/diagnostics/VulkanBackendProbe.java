package com.axalotl.async.vulkanruntime;

import com.axalotl.async.common.gpu.vulkan.VulkanCollisionBackend;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

/**
 * Standalone diagnostic for the isolated Vulkan child backend. It deliberately
 * avoids Minecraft/Forge so descriptor, push-constant, counter, and readback
 * failures can be reproduced in seconds with two known AABBs.
 */
public final class VulkanBackendProbe {
    private VulkanBackendProbe() { }

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            throw new IllegalArgumentException("usage: VulkanBackendProbe <shader.spv>");
        }

        System.setProperty("harimt.vulkan.allowCpuDevice", "true");
        byte[] spirv = Files.readAllBytes(Path.of(args[0]));
        LwjglVulkanBackend backend = new LwjglVulkanBackend();
        try {
            if (!backend.initialize(spirv)) {
                throw new IllegalStateException("initialize failed: " + backend.lastError());
            }

            // A and B overlap. C is far away. Exact conservative result = only (0,1).
            float[] minX = {0.0f, 0.5f, 10.0f};
            float[] minY = {0.0f, 0.5f, 10.0f};
            float[] minZ = {0.0f, 0.5f, 10.0f};
            float[] maxX = {1.0f, 1.5f, 11.0f};
            float[] maxY = {1.0f, 1.5f, 11.0f};
            float[] maxZ = {1.0f, 1.5f, 11.0f};

            VulkanCollisionBackend.Result result = backend.compute(
                    minX, minY, minZ, maxX, maxY, maxZ, 3, 16);
            System.out.println("device=" + backend.deviceName());
            System.out.println("pairCount=" + result.pairCount());
            System.out.println("overflow=" + result.overflow());
            System.out.println("pairsA=" + Arrays.toString(result.pairsA()));
            System.out.println("pairsB=" + Arrays.toString(result.pairsB()));
            System.out.println("dispatches=" + backend.totalDispatches());

            if (result.overflow()
                    || result.pairCount() != 1
                    || result.pairsA().length != 1
                    || result.pairsB().length != 1
                    || result.pairsA()[0] != 0
                    || result.pairsB()[0] != 1) {
                throw new AssertionError("expected exactly conservative pair (0,1)");
            }
            System.out.println("VULKAN_BACKEND_PROBE_PASS");
        } finally {
            backend.shutdown();
        }
    }
}
