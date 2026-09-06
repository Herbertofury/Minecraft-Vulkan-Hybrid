package com.axalotl.async.common.gpu;

import com.axalotl.async.common.gpu.safety.CrashGuard;
import com.axalotl.async.common.gpu.vulkan.VkRuntime;
import com.axalotl.async.common.gpu.vulkan.VulkanCollisionBackend;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;

/** Parent-side collision dispatcher. All LWJGL/Vulkan work lives in the isolated backend JAR. */
public final class GpuCollisionDispatcher {
    private static final Logger LOGGER = LoggerFactory.getLogger("HariMT/GpuCollision");
    private static final int INITIAL_ENTITY_CAPACITY = 512;
    private static final int MAX_COLLISION_PAIRS = 16_384;
    private static final String SHADER = "/assets/async/shaders/collision_broadphase.comp.spv";

    public record CollisionPair(Entity a, Entity b) {}

    private final CrashGuard crashGuard = new CrashGuard("EntityCollision", 3, 30_000);
    private volatile VulkanCollisionBackend backend;

    private float[] minX = new float[INITIAL_ENTITY_CAPACITY];
    private float[] minY = new float[INITIAL_ENTITY_CAPACITY];
    private float[] minZ = new float[INITIAL_ENTITY_CAPACITY];
    private float[] maxX = new float[INITIAL_ENTITY_CAPACITY];
    private float[] maxY = new float[INITIAL_ENTITY_CAPACITY];
    private float[] maxZ = new float[INITIAL_ENTITY_CAPACITY];
    private Entity[] entityIndexMap = new Entity[INITIAL_ENTITY_CAPACITY];
    private int capacity = INITIAL_ENTITY_CAPACITY;

    private volatile long gpuCollisionCount;
    private volatile long cpuFallbackCount;
    private volatile long overflowFallbackCount;

    public synchronized void initialize() {
        if (isOperational()) return;
        shutdown();
        try {
            byte[] spirv = loadShaderBytes();
            VulkanCollisionBackend candidate = VkRuntime.createBackend();
            if (!candidate.initialize(spirv) || !candidate.isOperational()) {
                String error = candidate.lastError();
                candidate.shutdown();
                LOGGER.info("Vulkan backend unavailable; vanilla collision fallback active{}",
                        error == null || error.isBlank() ? "" : ": " + error);
                return;
            }
            backend = candidate;
            LOGGER.info("Compile-checked Vulkan collision backend initialized on {} (maxWG={}, sharedMem={}KB)",
                    candidate.deviceName(), candidate.maxWorkGroupInvocations(), candidate.maxSharedMemoryBytes() / 1024L);
        } catch (Throwable t) {
            LOGGER.info("Vulkan backend initialization failed; vanilla fallback active: {}", t.toString());
            LOGGER.debug("Vulkan backend initialization details", t);
            backend = null;
        }
    }

    public synchronized void shutdown() {
        VulkanCollisionBackend current = backend;
        backend = null;
        if (current != null) {
            try { current.shutdown(); } catch (Throwable t) {
                LOGGER.debug("Vulkan backend shutdown failure", t);
            }
        }
    }

    public Optional<List<CollisionPair>> computeGpuOnly(List<Entity> entities) {
        if (entities == null || entities.size() < 16 || !isOperational()) return Optional.empty();
        ensureCapacity(entities.size());
        extractBounds(entities);
        final int count = entities.size();
        VulkanCollisionBackend current = backend;
        if (current == null) return Optional.empty();

        Callable<VulkanCollisionBackend.Result> compute =
                () -> current.compute(minX, minY, minZ, maxX, maxY, maxZ, count, MAX_COLLISION_PAIRS);
        VulkanCollisionBackend.Result result = crashGuard.execute(compute, null);
        if (result == null || !current.isOperational()) return Optional.empty();
        if (result.overflow()) {
            overflowFallbackCount++;
            LOGGER.warn("Vulkan broad-phase output overflow ({} > {} pairs); using complete vanilla fallback",
                    result.pairCount(), MAX_COLLISION_PAIRS);
            return Optional.empty();
        }

        int pairCount = result.pairCount();
        int[] a = result.pairsA();
        int[] b = result.pairsB();
        if (pairCount > a.length || pairCount > b.length) {
            LOGGER.warn("Vulkan backend returned inconsistent pair buffers; using vanilla fallback");
            return Optional.empty();
        }

        List<CollisionPair> pairs = new ArrayList<>(pairCount);
        for (int i = 0; i < pairCount; i++) {
            int ia = a[i];
            int ib = b[i];
            if (ia < 0 || ib < 0 || ia >= count || ib >= count || ia >= ib) {
                LOGGER.warn("Vulkan backend returned invalid pair ({},{}); using vanilla fallback", ia, ib);
                return Optional.empty();
            }
            Entity ea = entityIndexMap[ia];
            Entity eb = entityIndexMap[ib];
            if (ea == null || eb == null) {
                LOGGER.warn("Vulkan backend referenced a missing entity; using vanilla fallback");
                return Optional.empty();
            }
            pairs.add(new CollisionPair(ea, eb));
        }
        gpuCollisionCount++;
        return Optional.of(pairs);
    }

    /** Compatibility API for callers outside the deferred-push integration. */
    public List<CollisionPair> computeBroadPhase(List<Entity> entities) {
        if (entities == null || entities.isEmpty()) return Collections.emptyList();
        Optional<List<CollisionPair>> gpu = computeGpuOnly(entities);
        return gpu.orElseGet(() -> cpuFallback(entities));
    }

    private void extractBounds(List<Entity> entities) {
        for (int i = 0; i < entities.size(); i++) {
            Entity entity = entities.get(i);
            AABB box = entity.getBoundingBox();
            // Float narrowing is rounded outward: false positives are allowed,
            // precision-induced false negatives are not.
            minX[i] = Math.nextDown((float) box.minX);
            minY[i] = Math.nextDown((float) box.minY);
            minZ[i] = Math.nextDown((float) box.minZ);
            maxX[i] = Math.nextUp((float) box.maxX);
            maxY[i] = Math.nextUp((float) box.maxY);
            maxZ[i] = Math.nextUp((float) box.maxZ);
            entityIndexMap[i] = entity;
        }
    }

    private synchronized void ensureCapacity(int needed) {
        if (needed <= capacity) return;
        int next = capacity;
        while (next < needed) next = Math.multiplyExact(next, 2);
        minX = new float[next];
        minY = new float[next];
        minZ = new float[next];
        maxX = new float[next];
        maxY = new float[next];
        maxZ = new float[next];
        entityIndexMap = new Entity[next];
        capacity = next;
    }

    private List<CollisionPair> cpuFallback(List<Entity> entities) {
        cpuFallbackCount++;
        List<CollisionPair> pairs = new ArrayList<>();
        for (int i = 0; i < entities.size(); i++) {
            Entity a = entities.get(i);
            if (a == null || a.isRemoved()) continue;
            AABB box = a.getBoundingBox();
            for (int j = i + 1; j < entities.size(); j++) {
                Entity b = entities.get(j);
                if (b != null && !b.isRemoved() && box.intersects(b.getBoundingBox())) {
                    pairs.add(new CollisionPair(a, b));
                }
            }
        }
        return pairs;
    }

    private byte[] loadShaderBytes() throws IOException {
        try (InputStream in = GpuCollisionDispatcher.class.getResourceAsStream(SHADER)) {
            if (in == null) throw new IOException("Missing packaged SPIR-V shader: " + SHADER);
            byte[] bytes = in.readAllBytes();
            if (bytes.length < 4 || bytes[0] != 0x03 || bytes[1] != 0x02 || bytes[2] != 0x23 || bytes[3] != 0x07) {
                throw new IOException("Packaged collision shader is not valid SPIR-V");
            }
            return bytes;
        }
    }

    public boolean isOperational() {
        VulkanCollisionBackend current = backend;
        return current != null && current.isOperational() && !current.isDeviceLost();
    }

    public String getDeviceName() {
        VulkanCollisionBackend current = backend;
        return current == null ? "Unavailable" : current.deviceName();
    }

    public int getMaxWorkGroupInvocations() {
        VulkanCollisionBackend current = backend;
        return current == null ? 0 : current.maxWorkGroupInvocations();
    }

    public long getMaxSharedMemoryBytes() {
        VulkanCollisionBackend current = backend;
        return current == null ? 0L : current.maxSharedMemoryBytes();
    }

    public long getBackendDispatchCount() {
        VulkanCollisionBackend current = backend;
        return current == null ? 0L : current.totalDispatches();
    }

    public double getLastDispatchMillis() {
        VulkanCollisionBackend current = backend;
        return current == null ? 0.0 : current.lastDispatchNanos() / 1_000_000.0;
    }

    public String getBackendError() {
        VulkanCollisionBackend current = backend;
        return current == null ? "" : current.lastError();
    }

    public long getGpuCollisionCount() { return gpuCollisionCount; }
    public long getCpuFallbackCount() { return cpuFallbackCount; }
    public long getOverflowFallbackCount() { return overflowFallbackCount; }
    public CrashGuard.CrashGuardStats getCrashGuardStats() { return crashGuard.getStats(); }
}
