package com.axalotl.async.common.gpu;

import com.axalotl.async.common.config.AsyncConfig;
import com.axalotl.async.common.mixin.accessor.LivingEntityPushInvoker;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Predicate;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Stable post-barrier integration point for the optional Vulkan collision broad phase.
 *
 * When a ServerLevel contains any asynchronously ticking entities, the complete
 * mixed sync/async entity batch is marked active before workers are dispatched.
 * Every LivingEntity#pushEntities invocation in that active batch is queued,
 * including calls made by synchronous fallback entities on the dimension thread.
 * After every worker has joined, the batch is ended and the exact vanilla
 * pushEntities method is replayed. Vulkan may replace only its broad-phase entity
 * lookup; all vanilla predicates, double-precision AABB checks, cramming rules,
 * Forge hooks and doPush calls remain authoritative.
 */
public final class GpuPushBatch {
    private static final Logger LOGGER = LoggerFactory.getLogger("HariMT/GpuPushBatch");

    private static final ConcurrentHashMap<ResourceKey<Level>, AtomicInteger> ACTIVE_BATCH_DEPTHS =
            new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<ResourceKey<Level>, ConcurrentLinkedQueue<LivingEntity>> DEFERRED =
            new ConcurrentHashMap<>();
    private static final ThreadLocal<QueryContext> ACTIVE_QUERY = new ThreadLocal<>();

    private static final AtomicBoolean LOGGED_GPU_ACTIVE = new AtomicBoolean(false);
    private static final AtomicBoolean LOGGED_GPU_SUSTAINED = new AtomicBoolean(false);
    private static final AtomicBoolean LOGGED_FALLBACK_ACTIVE = new AtomicBoolean(false);
    private static final AtomicInteger CONSECUTIVE_GPU_BATCHES = new AtomicInteger();

    private static final LongAdder GPU_BATCHES = new LongAdder();
    private static final LongAdder VANILLA_FALLBACK_BATCHES = new LongAdder();
    private static final LongAdder GPU_PAIRS = new LongAdder();
    private static final LongAdder GPU_NANOS = new LongAdder();

    private GpuPushBatch() {}

    public static void beginBatch(ServerLevel world) {
        if (world == null) return;
        ACTIVE_BATCH_DEPTHS.compute(world.dimension(), (key, depth) -> {
            if (depth == null) return new AtomicInteger(1);
            depth.incrementAndGet();
            return depth;
        });
    }

    public static void endBatch(ServerLevel world) {
        if (world == null) return;
        ACTIVE_BATCH_DEPTHS.computeIfPresent(world.dimension(), (key, depth) ->
                depth.decrementAndGet() <= 0 ? null : depth);
    }

    public static boolean isBatchActive(ServerLevel world) {
        if (world == null) return false;
        AtomicInteger depth = ACTIVE_BATCH_DEPTHS.get(world.dimension());
        return depth != null && depth.get() > 0;
    }

    public static boolean shouldDefer(LivingEntity entity) {
        if (entity == null || entity.level().isClientSide()) return false;
        if (!(entity.level() instanceof ServerLevel level)) return false;
        return !AsyncConfig.disabled.getValue() && isBatchActive(level);
    }

    public static void defer(LivingEntity entity) {
        if (!(entity.level() instanceof ServerLevel level)) return;
        // Preserve every vanilla invocation. Do not deduplicate: a mod may legally
        // call pushEntities more than once in a tick, and each call must replay.
        DEFERRED.computeIfAbsent(level.dimension(), ignored -> new ConcurrentLinkedQueue<>()).add(entity);
    }

    /** Flushes one dimension's deferred vanilla push calls after its batch barrier. */
    public static void flush(ServerLevel world) {
        if (world == null) return;
        if (isBatchActive(world)) {
            LOGGER.error("Refusing to replay deferred pushes while the entity batch is still active for {}",
                    world.dimension().location());
            return;
        }

        ConcurrentLinkedQueue<LivingEntity> queue = DEFERRED.get(world.dimension());
        if (queue == null || queue.isEmpty()) return;

        List<LivingEntity> deferred = new ArrayList<>();
        LivingEntity entity;
        while ((entity = queue.poll()) != null) {
            if (!entity.isRemoved() && entity.level() == world) deferred.add(entity);
        }
        if (queue.isEmpty()) DEFERRED.remove(world.dimension(), queue);
        if (deferred.isEmpty()) return;

        QueryContext context = buildGpuContext(world, deferred);
        if (context != null) {
            ACTIVE_QUERY.set(context);
            GPU_BATCHES.increment();
            LOGGED_FALLBACK_ACTIVE.set(false);
            int consecutive = CONSECUTIVE_GPU_BATCHES.incrementAndGet();
            if (LOGGED_GPU_ACTIVE.compareAndSet(false, true)) {
                LOGGER.info("Vulkan push broad-phase is active: first verified batch produced {} candidate pairs",
                        context.pairCount);
            }
            if (consecutive >= 10 && LOGGED_GPU_SUSTAINED.compareAndSet(false, true)) {
                LOGGER.info("Vulkan push broad-phase sustained: 10 consecutive verified batches completed");
            }
        } else {
            VANILLA_FALLBACK_BATCHES.increment();
            CONSECUTIVE_GPU_BATCHES.set(0);
            if (LOGGED_FALLBACK_ACTIVE.compareAndSet(false, true)) {
                String reason;
                if (!AsyncConfig.enableGpuCollision.getValue()) {
                    reason = "GPU collision is disabled";
                } else if (!GpuEntityModule.isGpuAvailable()) {
                    reason = "Vulkan collision pipeline is unavailable";
                } else {
                    reason = "GPU result was unavailable/incomplete";
                }
                LOGGER.info("Vanilla push replay fallback is active: {}", reason);
            }
        }

        try {
            for (LivingEntity source : deferred) {
                if (source.isRemoved() || source.level() != world) continue;
                ((LivingEntityPushInvoker) source).harimt$invokePushEntities();
            }
        } finally {
            ACTIVE_QUERY.remove();
        }
    }

    private static QueryContext buildGpuContext(ServerLevel world, List<LivingEntity> deferred) {
        if (!GpuEntityModule.isGpuAvailable() || !AsyncConfig.enableGpuCollision.getValue()) return null;

        List<Entity> allEntities = new ArrayList<>();
        for (Entity current : world.getAllEntities()) {
            if (current != null && !current.isRemoved() && current.isAlive()) allEntities.add(current);
        }
        if (allEntities.size() < 16) return null;

        long started = System.nanoTime();
        Optional<List<GpuCollisionDispatcher.CollisionPair>> maybePairs =
                GpuEntityModule.getCollisionDispatcher().computeGpuOnly(allEntities);
        GPU_NANOS.add(System.nanoTime() - started);
        if (maybePairs.isEmpty()) return null;

        IdentityHashMap<Entity, List<Entity>> candidates = new IdentityHashMap<>();
        for (LivingEntity source : deferred) candidates.put(source, new ArrayList<>());

        List<GpuCollisionDispatcher.CollisionPair> pairs = maybePairs.get();
        GPU_PAIRS.add(pairs.size());
        for (GpuCollisionDispatcher.CollisionPair pair : pairs) {
            Entity a = pair.a();
            Entity b = pair.b();
            List<Entity> aList = candidates.get(a);
            if (aList != null) aList.add(b);
            List<Entity> bList = candidates.get(b);
            if (bList != null) bList.add(a);
        }
        return new QueryContext(world, candidates, pairs.size());
    }

    /**
     * Called from the ServerLevel mixin. Null means "use vanilla".
     * A non-null result is valid only during deferred push replay.
     */
    public static List<Entity> tryQuery(ServerLevel world, Entity source, AABB box,
                                        Predicate<? super Entity> predicate) {
        QueryContext context = ACTIVE_QUERY.get();
        if (context == null || context.world != world || source == null || box == null) return null;
        List<Entity> candidates = context.candidates.get(source);
        if (candidates == null) return null;

        if (!sameBox(box, source.getBoundingBox())) return null;

        List<Entity> result = new ArrayList<>(candidates.size());
        for (Entity candidate : candidates) {
            if (candidate == null || candidate == source || candidate.isRemoved()) continue;
            // GPU broad phase is deliberately conservative; vanilla double-precision
            // AABBs remain the authoritative narrow phase.
            if (!candidate.getBoundingBox().intersects(box)) continue;
            if (predicate == null || predicate.test(candidate)) result.add(candidate);
        }
        return result;
    }

    private static boolean sameBox(AABB a, AABB b) {
        return Double.compare(a.minX, b.minX) == 0
                && Double.compare(a.minY, b.minY) == 0
                && Double.compare(a.minZ, b.minZ) == 0
                && Double.compare(a.maxX, b.maxX) == 0
                && Double.compare(a.maxY, b.maxY) == 0
                && Double.compare(a.maxZ, b.maxZ) == 0;
    }

    public static long getGpuBatches() { return GPU_BATCHES.sum(); }
    public static long getVanillaFallbackBatches() { return VANILLA_FALLBACK_BATCHES.sum(); }
    public static long getGpuPairs() { return GPU_PAIRS.sum(); }
    public static int getConsecutiveGpuBatches() { return CONSECUTIVE_GPU_BATCHES.get(); }
    public static double getAverageGpuMillis() {
        long batches = GPU_BATCHES.sum();
        return batches == 0 ? 0.0 : (GPU_NANOS.sum() / 1_000_000.0) / batches;
    }

    public static void clear() {
        ACTIVE_BATCH_DEPTHS.clear();
        DEFERRED.clear();
        ACTIVE_QUERY.remove();
        LOGGED_GPU_ACTIVE.set(false);
        LOGGED_GPU_SUSTAINED.set(false);
        LOGGED_FALLBACK_ACTIVE.set(false);
        CONSECUTIVE_GPU_BATCHES.set(0);
    }

    private static final class QueryContext {
        private final ServerLevel world;
        private final IdentityHashMap<Entity, List<Entity>> candidates;
        private final int pairCount;

        private QueryContext(ServerLevel world, IdentityHashMap<Entity, List<Entity>> candidates, int pairCount) {
            this.world = world;
            this.candidates = candidates;
            this.pairCount = pairCount;
        }
    }
}
