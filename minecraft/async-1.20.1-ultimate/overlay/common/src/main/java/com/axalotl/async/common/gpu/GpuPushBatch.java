package com.axalotl.async.common.gpu;

import com.axalotl.async.common.ParallelProcessor;
import com.axalotl.async.common.config.AsyncConfig;
import com.axalotl.async.common.mixin.accessor.LivingEntityPushInvoker;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
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
 * Real integration point for the Vulkan collision broad phase.
 *
 * Async LivingEntity#pushEntities calls are deferred until after all entity
 * workers finish. At that point entity positions are stable. Vulkan computes
 * exact overlapping AABB pairs for all live entities in the level. We then
 * replay the original vanilla pushEntities method on the server thread. During
 * that replay only its exact bounding-box entity query is served from the GPU
 * pair map. All predicates and the remainder of vanilla push/cramming logic are
 * still executed by vanilla code.
 *
 * If Vulkan is unavailable, a dispatch fails, output overflows, or a query does
 * not exactly match the deferred-push shape, the context returns null and
 * ServerLevel falls straight through to its normal vanilla entity lookup.
 */
public final class GpuPushBatch {
    private static final Logger LOGGER = LoggerFactory.getLogger("HariMT/GpuPushBatch");
    private static final Map<ResourceKey<Level>, ConcurrentLinkedQueue<LivingEntity>> DEFERRED =
            new ConcurrentHashMap<>();
    private static final ThreadLocal<QueryContext> ACTIVE_QUERY = new ThreadLocal<>();

    private static final LongAdder GPU_BATCHES = new LongAdder();
    private static final LongAdder VANILLA_FALLBACK_BATCHES = new LongAdder();
    private static final LongAdder GPU_PAIRS = new LongAdder();
    private static final LongAdder GPU_NANOS = new LongAdder();

    private GpuPushBatch() {}

    public static boolean shouldDefer(LivingEntity entity) {
        if (entity == null || entity.level().isClientSide()) return false;
        if (!(entity.level() instanceof ServerLevel)) return false;
        if (!ParallelProcessor.isServerExecutionThread()) return false;
        if (AsyncConfig.disabled.getValue() || !AsyncConfig.enableGpuCollision.getValue()) return false;
        return GpuEntityModule.isGpuAvailable();
    }

    public static void defer(LivingEntity entity) {
        if (!(entity.level() instanceof ServerLevel level)) return;
        DEFERRED.computeIfAbsent(level.dimension(), ignored -> new ConcurrentLinkedQueue<>()).add(entity);
    }

    /** Flushes one dimension's deferred push work on the server thread. */
    public static void flush(ServerLevel world) {
        ConcurrentLinkedQueue<LivingEntity> queue = DEFERRED.get(world.dimension());
        if (queue == null || queue.isEmpty()) return;

        // Identity semantics are intentional: entities are mutable runtime objects.
        Set<LivingEntity> unique = Collections.newSetFromMap(new IdentityHashMap<>());
        LivingEntity entity;
        while ((entity = queue.poll()) != null) {
            if (!entity.isRemoved() && entity.level() == world) unique.add(entity);
        }
        if (queue.isEmpty()) DEFERRED.remove(world.dimension(), queue);
        if (unique.isEmpty()) return;

        List<LivingEntity> deferred = new ArrayList<>(unique);
        QueryContext context = buildGpuContext(world, deferred);
        if (context != null) {
            ACTIVE_QUERY.set(context);
            GPU_BATCHES.increment();
        } else {
            VANILLA_FALLBACK_BATCHES.increment();
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
        return new QueryContext(world, candidates);
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

        // LivingEntity#pushEntities uses its current bounding box exactly. Any
        // different query shape is outside the GPU proof and must use vanilla.
        if (!sameBox(box, source.getBoundingBox())) return null;

        List<Entity> result = new ArrayList<>(candidates.size());
        for (Entity candidate : candidates) {
            if (candidate == null || candidate == source || candidate.isRemoved()) continue;
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
    public static double getAverageGpuMillis() {
        long batches = GPU_BATCHES.sum();
        return batches == 0 ? 0.0 : (GPU_NANOS.sum() / 1_000_000.0) / batches;
    }

    public static void clear() {
        DEFERRED.clear();
        ACTIVE_QUERY.remove();
    }

    private static final class QueryContext {
        private final ServerLevel world;
        private final IdentityHashMap<Entity, List<Entity>> candidates;

        private QueryContext(ServerLevel world, IdentityHashMap<Entity, List<Entity>> candidates) {
            this.world = world;
            this.candidates = candidates;
        }
    }
}
