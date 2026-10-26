package com.axalotl.async.common.gpu;

import com.axalotl.async.common.gpu.safety.CrashGuard;
import com.axalotl.async.common.gpu.vulkan.VkRuntime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Top-level lifecycle/status owner for optional Vulkan collision acceleration. */
public final class GpuEntityModule {
    private static final Logger LOGGER = LoggerFactory.getLogger("HariMT/GpuEntityModule");
    private static final GpuCollisionDispatcher COLLISIONS = new GpuCollisionDispatcher();

    private static volatile boolean gpuAvailable;
    private static volatile boolean initialized;

    private GpuEntityModule() {}

    public static synchronized void initialize() {
        if (initialized) return;
        try {
            COLLISIONS.initialize();
            gpuAvailable = COLLISIONS.isOperational();
            if (gpuAvailable) {
                LOGGER.info("GPU Entity Module ready on {} (maxWG={}, sharedMem={}KB, isolatedBackend={})",
                        COLLISIONS.getDeviceName(),
                        COLLISIONS.getMaxWorkGroupInvocations(),
                        COLLISIONS.getMaxSharedMemoryBytes() / 1024L,
                        VkRuntime.isUsingEmbeddedRuntime());
            } else {
                LOGGER.info("GPU Entity Module unavailable; deferred vanilla push replay remains active");
            }
        } catch (Throwable t) {
            gpuAvailable = false;
            LOGGER.info("GPU Entity Module initialization failed; vanilla fallback active: {}", t.toString());
            LOGGER.debug("GPU initialization details", t);
        } finally {
            initialized = true;
        }
    }

    public static synchronized void shutdown() {
        COLLISIONS.shutdown();
        gpuAvailable = false;
        initialized = false;
    }

    public static GpuCollisionDispatcher getCollisionDispatcher() {
        return COLLISIONS;
    }

    public static boolean isGpuAvailable() {
        return gpuAvailable && COLLISIONS.isOperational();
    }

    public static boolean isInitialized() {
        return initialized;
    }

    public static String getStatusString() {
        GpuCollisionDispatcher d = COLLISIONS;
        StringBuilder sb = new StringBuilder();
        sb.append("GPU: ").append(isGpuAvailable() ? "Available" : "Unavailable").append('\n');
        sb.append("Device: ").append(d.getDeviceName()).append('\n');
        sb.append("Backend: compile-checked isolated LWJGL 3.3.1").append('\n');
        sb.append("Dispatches: ").append(d.getBackendDispatchCount()).append('\n');
        sb.append("Last dispatch: ").append(String.format("%.3f ms", d.getLastDispatchMillis())).append('\n');
        sb.append("GPU batches: ").append(d.getGpuCollisionCount()).append('\n');
        sb.append("CPU compatibility fallbacks: ").append(d.getCpuFallbackCount()).append('\n');
        sb.append("Overflow fallbacks: ").append(d.getOverflowFallbackCount()).append('\n');
        CrashGuard.CrashGuardStats stats = d.getCrashGuardStats();
        sb.append("CrashGuard: ").append(stats.circuitOpen() ? "tripped" : "healthy")
                .append(" failures=").append(stats.totalFailures())
                .append(" trips=").append(stats.totalCircuitTrips());
        String error = d.getBackendError();
        if (error != null && !error.isBlank()) sb.append('\n').append("Last backend error: ").append(error);
        return sb.toString();
    }

    /** Retained for compatibility; /async gpu test now performs live-world verification. */
    public static String runBenchmark() {
        return "Synthetic GPU benchmark removed; use /async gpu test for live-world GPU-vs-CPU verification.";
    }
}
