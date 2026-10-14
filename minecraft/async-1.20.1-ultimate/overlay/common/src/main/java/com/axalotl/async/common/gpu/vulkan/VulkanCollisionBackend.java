package com.axalotl.async.common.gpu.vulkan;

/**
 * Tiny classloader-safe contract between HariMultiThread and the isolated,
 * compile-checked LWJGL Vulkan backend.
 *
 * Only JDK primitives/arrays/records cross this boundary. Minecraft, Forge and
 * LWJGL classes remain on their owning side of the classloader.
 */
public interface VulkanCollisionBackend {
    /** Initialize the backend using a validated SPIR-V compute shader. */
    boolean initialize(byte[] spirv);

    /** True only after device, pipeline, descriptors, queue and command state are live. */
    boolean isOperational();

    /** True after a Vulkan device-loss result or equivalent fatal GPU state. */
    boolean isDeviceLost();

    /** Human-readable last initialization/dispatch failure, or an empty string. */
    String lastError();

    String deviceName();

    int maxWorkGroupInvocations();

    long maxSharedMemoryBytes();

    /**
     * Compute conservative AABB overlap candidates.
     *
     * The six arrays contain outward-rounded runtime entity AABB bounds.
     * Implementations must never silently truncate. When output capacity is
     * exceeded, return overflow=true so the parent immediately uses vanilla.
     */
    Result compute(
            float[] minX, float[] minY, float[] minZ,
            float[] maxX, float[] maxY, float[] maxZ,
            int count, int maxPairs);

    long totalDispatches();

    long lastDispatchNanos();

    /** Safe to call repeatedly, including after partial initialization. */
    void shutdown();

    record Result(int[] pairsA, int[] pairsB, int pairCount, boolean overflow, long dispatchNanos) {
        public Result {
            if (pairsA == null) pairsA = new int[0];
            if (pairsB == null) pairsB = new int[0];
            if (pairCount < 0) pairCount = 0;
        }
    }
}
