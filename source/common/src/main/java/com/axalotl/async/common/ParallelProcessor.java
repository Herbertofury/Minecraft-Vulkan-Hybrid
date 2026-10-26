package com.axalotl.async.common;

import com.axalotl.async.common.config.AsyncConfig;
import com.axalotl.async.common.gpu.GpuCollisionDispatcher;
import com.axalotl.async.common.gpu.GpuEntityModule;
import com.axalotl.async.common.gpu.GpuPushBatch;
import com.axalotl.async.common.mixin.accessor.EntityAccessor;
import com.axalotl.async.common.parallelised.utils.AsyncCompatible;
import com.axalotl.async.common.utils.EntityTickCircuitBreaker;
import com.axalotl.async.common.utils.TickStats;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Consumer;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.monster.Shulker;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.vehicle.AbstractMinecart;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.chunk.LevelChunk;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class ParallelProcessor {
    public static final Logger LOGGER = LogManager.getLogger(ParallelProcessor.class);
    private static MinecraftServer server;
    public static final AtomicInteger currentEntities = new AtomicInteger();
    private static final AtomicInteger threadPoolID = new AtomicInteger();
    public static ExecutorService tickPool;
    private static final Set<UUID> blacklistedEntity = ConcurrentHashMap.newKeySet();
    private static final Map<String, Set<WeakReference<Thread>>> mcThreadTracker = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<ResourceKey<Level>, Object> ENTITY_OPERATION_LOCKS = new ConcurrentHashMap<>();
    private static volatile boolean isShuttingDown = false;

    // --- NEW: Circuit breaker for per-entity-type crash isolation ---
    private static final EntityTickCircuitBreaker circuitBreaker = new EntityTickCircuitBreaker();

    // --- NEW: Telemetry counters ---
    private static final LongAdder totalAsyncTicks = new LongAdder();
    private static final LongAdder totalAsyncFailures = new LongAdder();
    private static final LongAdder totalTimeoutWarnings = new LongAdder();
    private static final AtomicInteger lastWorkerCount = new AtomicInteger(0);

    // --- NEW: GPU collision pre-compute ---
    private static final LongAdder gpuCollisionTicks = new LongAdder();
    private static final LongAdder gpuCollisionSavedMs = new LongAdder();

    // --- REMOVED: global ENTITY_ADD_LOCK (now per-dimension in ServerLevelMixin) ---

    public static final Set<Class<?>> BLOCKED_ENTITIES = Set.of(
            FallingBlockEntity.class,
            Shulker.class,
            Boat.class,
            EnderDragon.class);

    private static final Map<UUID, Integer> portalTickSyncMap = new ConcurrentHashMap<>();

    public static EntityTickCircuitBreaker getCircuitBreaker() {
        return circuitBreaker;
    }

    public static int getTotalAsyncTicks() {
        return totalAsyncTicks.intValue();
    }

    public static int getTotalAsyncFailures() {
        return totalAsyncFailures.intValue();
    }

    public static int getTotalTimeoutWarnings() {
        return totalTimeoutWarnings.intValue();
    }

    public static int getLastWorkerCount() {
        return lastWorkerCount.get();
    }

    public static void setupThreadPool(int parallelism, Class<?> asyncClass) {
        isShuttingDown = false;
        ThreadFactory threadFactory = runnable -> {
            Thread thread = new AsyncWorkerThread(runnable, "Async-Tick-Pool-Thread-" + threadPoolID.getAndIncrement());
            registerThread("Async-Tick", thread);
            thread.setDaemon(true);
            thread.setPriority(Thread.NORM_PRIORITY - 1);
            thread.setContextClassLoader(asyncClass.getClassLoader());
            return thread;
        };
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                parallelism,
                parallelism,
                0L, TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>(),
                threadFactory);

        // IMPROVED: CallerRunsPolicy instead of DiscardPolicy
        // If pool is saturated, the caller thread (server) runs the task itself
        // instead of silently dropping it. This is much safer.
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());

        executor.allowCoreThreadTimeOut(false);
        executor.prestartAllCoreThreads();
        tickPool = executor;
        LOGGER.info("Initialized Pool with {} threads (CallerRunsPolicy)", parallelism);

        // Initialize GPU module (non-blocking, graceful fallback)
        GpuEntityModule.initialize();
    }

    public static void registerThread(String poolName, Thread thread) {
        mcThreadTracker.computeIfAbsent(poolName, key -> ConcurrentHashMap.newKeySet())
                .add(new WeakReference<>(thread));
    }

    private static boolean isThreadInPool(Thread thread) {
        if (thread instanceof AsyncWorkerThread) return true;
        return mcThreadTracker.getOrDefault("Async-Tick", Set.of()).stream()
                .map(WeakReference::get)
                .filter(java.util.Objects::nonNull)
                .anyMatch(thread::equals);
    }

    public static boolean isServerExecutionThread() {
        return isThreadInPool(Thread.currentThread());
    }

    public static int getPoolSize() {
        if (tickPool instanceof ThreadPoolExecutor pool) {
            return pool.getCorePoolSize();
        }
        return 0;
    }

    /**
     * Runs arbitrary independent work on HMT's managed pool and does not return
     * until every item has finished. Small batches stay on the caller thread.
     * While waiting, waitForFutures keeps pumping server chunk tasks, avoiding
     * worker<->main-thread chunk-future deadlocks.
     */
    @SuppressWarnings("unchecked")
    public static <T> void forEachParallel(ServerLevel waitWorld, List<T> items, Consumer<T> action) {
        if (items == null || items.isEmpty()) return;
        if (items.size() < 64 || tickPool == null || tickPool.isShutdown() || isServerExecutionThread()) {
            for (T item : items) action.accept(item);
            return;
        }

        TaskFailures failures = new TaskFailures();
        IndexedWorkQueue<T> work = new IndexedWorkQueue<>(items);
        int poolSize = Math.max(1, getPoolSize());
        int desired = Math.max(1, (items.size() + 31) / 32);
        int workers = Math.min(poolSize, desired);
        List<Future<Void>> futures = new ArrayList<>(workers);
        for (int i = 0; i < workers; i++) {
            futures.add((Future<Void>) tickPool.submit(() -> {
                T item;
                while (!failures.failed() && (item = work.poll()) != null) {
                    try {
                        action.accept(item);
                    } catch (Throwable t) {
                        failures.record(t);
                    }
                }
            }));
        }
        waitForFutures(futures, waitWorld);
        failures.rethrow();
    }

    /**
     * Calculate optimal worker count based on entity count and pool size.
     * Dynamic scaling: fewer workers when few entities, full pool when many.
     */
    private static int calculateWorkerCount(int entityCount, int poolSize) {
        if (poolSize <= 0) return 1;
        int entitiesPerWorker = AsyncConfig.entitiesPerWorker.getValue();
        if (entitiesPerWorker <= 0) entitiesPerWorker = 25;
        int desired = (entityCount + entitiesPerWorker - 1) / entitiesPerWorker;
        return Math.max(1, Math.min(poolSize, desired));
    }

    /**
     * Main entity tick dispatch method.
     * IMPROVED: Pre-splits sync/async, uses work stealing, integrates circuit breaker.
     */
    @SuppressWarnings("unchecked")
    public static void callEntityTickBatch(ServerLevel world, List<Entity> entities) {
        if (entities.isEmpty()) return;

        if (AsyncConfig.disabled.getValue() || tickPool == null || tickPool.isShutdown()) {
            entities.forEach(e -> tickEntity(world, e, false));
            TickStats.RECORDING_TICKS_LEFT.decrementAndGet();
            return;
        }

        // IMPROVED: Pre-split entities into sync and async lists
        // This avoids redundant classification inside each worker
        List<Entity> syncEntities = new ArrayList<>();
        List<Entity> asyncEntities = new ArrayList<>(entities.size());

        for (Entity entity : entities) {
            if (shouldTickSynchronously(entity)) {
                syncEntities.add(entity);
            } else {
                asyncEntities.add(entity);
            }
        }

        List<Future<Void>> futures = Collections.emptyList();
        boolean pushBatchActive = !asyncEntities.isEmpty();
        boolean workersJoined = false;
        TaskFailures batchFailures = new TaskFailures();
        if (pushBatchActive) GpuPushBatch.beginBatch(world);

        try {

        if (!asyncEntities.isEmpty()) {
            int poolSize = getPoolSize();
            int workerCount = calculateWorkerCount(asyncEntities.size(), poolSize);
            lastWorkerCount.set(workerCount);

            if (AsyncConfig.enableAffinityRouting.getValue()) {
                futures = dispatchWithAffinity(world, asyncEntities, workerCount);
            } else {
                futures = dispatchWithWorkStealing(world, asyncEntities, workerCount);
            }
        }

        // Tick sync entities on main thread (runs concurrently with async workers)
        for (Entity e : syncEntities) {
            tickEntity(world, e, false);
        }

            // The world batch remains active through synchronous fallback ticks and
            // every async worker. Only after the join may vanilla crowding replay.
            waitForFutures(futures, world);
            workersJoined = true;
        } catch (Throwable failure) {
            batchFailures.record(failure);
        } finally {
            if (pushBatchActive) {
                if (!workersJoined) {
                    try {
                        waitForFutures(futures, world);
                    } catch (Throwable failure) {
                        batchFailures.record(failure);
                    }
                }
                GpuPushBatch.endBatch(world);
                if (batchFailures.failed()) GpuPushBatch.discard(world);
                else GpuPushBatch.flush(world);
            }
        }
        batchFailures.rethrow();
        TickStats.RECORDING_TICKS_LEFT.decrementAndGet();
    }

    /**
     * Snapshot-indexed work stealing without per-entity linked nodes.
     * Each worker pulls entities one at a time from the shared queue.
     * Fast workers naturally process more entities, eliminating batch imbalance.
     */
    private static List<Future<Void>> dispatchWithWorkStealing(
            ServerLevel world, List<Entity> asyncEntities, int workerCount) {
        IndexedWorkQueue<Entity> workQueue = new IndexedWorkQueue<>(asyncEntities);
        List<Future<Void>> futures = new ArrayList<>(workerCount);

        for (int w = 0; w < workerCount; w++) {
            futures.add((Future<Void>) tickPool.submit(() -> {
                Entity entity;
                while ((entity = workQueue.poll()) != null) {
                    tickEntity(world, entity, true);
                }
            }));
        }

        return futures;
    }

    /**
     * IMPROVED: Affinity routing with work stealing fallback.
     * Entities are routed to per-worker lanes by chunk position,
     * giving cache locality when entities access the same chunk data.
     * Workers drain their own lane first, then steal from others.
     */
    private static List<Future<Void>> dispatchWithAffinity(
            ServerLevel world, List<Entity> asyncEntities, int workerCount) {
        // Create per-worker lanes
        List<List<Entity>> laneItems = new ArrayList<>(workerCount);
        for (int i = 0; i < workerCount; i++) {
            laneItems.add(new ArrayList<>());
        }

        // Route entities to lanes by chunk position (XOR hash for better distribution)
        for (Entity entity : asyncEntities) {
            long chunkKey = entity.chunkPosition().toLong();
            int laneIndex = (int) (((chunkKey ^ (chunkKey >>> 32)) & 0x7FFFFFFFFFFFFFFFL) % workerCount);
            laneItems.get(laneIndex).add(entity);
        }

        List<IndexedWorkQueue<Entity>> lanes = new ArrayList<>(workerCount);
        for (List<Entity> lane : laneItems) lanes.add(new IndexedWorkQueue<>(lane));
        List<Future<Void>> futures = new ArrayList<>(workerCount);

        for (int w = 0; w < workerCount; w++) {
            final int myLane = w;
            futures.add((Future<Void>) tickPool.submit(() -> {
                // Phase 1: Drain own lane first (cache locality)
                Entity entity;
                while ((entity = lanes.get(myLane).poll()) != null) {
                    tickEntity(world, entity, true);
                }
                // Phase 2: Steal from other lanes (work stealing)
                for (int i = 0; i < workerCount; i++) {
                    if (i == myLane) continue;
                    while ((entity = lanes.get(i).poll()) != null) {
                        tickEntity(world, entity, true);
                    }
                }
            }));
        }

        return futures;
    }

    /**
     * IMPROVED: Wait for futures with stale task timeout detection.
     * Logs warnings when ticks exceed the timeout threshold,
     * but always waits for completion to avoid data corruption.
     */
    private static void waitForFutures(List<Future<Void>> futures, ServerLevel waitWorld) {
        if (futures.isEmpty()) return;

        long startTime = System.nanoTime();
        long timeoutNs = TimeUnit.MILLISECONDS.toNanos(
                Math.max(50, AsyncConfig.staleTaskTimeoutMs.getValue()));
        if (AsyncCommon.HARICHUNK && waitWorld != null) {
            C2meTaskWaiter.await(waitWorld, futures, timeoutNs, elapsed -> {
                totalTimeoutWarnings.increment();
                LOGGER.warn("Async entity tick batch exceeded {}ms timeout ({}ms elapsed), still waiting...",
                        AsyncConfig.staleTaskTimeoutMs.getValue(), TimeUnit.NANOSECONDS.toMillis(elapsed));
            });
            return;
        }
        boolean timeoutWarned = false;

        boolean interrupted = false;
        boolean allDone;
        do {
            if (Thread.interrupted()) interrupted = true;
            allDone = true;
            for (Future<?> future : futures) {
                if (!future.isDone()) { allDone = false; break; }
            }
            if (!allDone) {
                long elapsed = System.nanoTime() - startTime;
                if (!timeoutWarned && elapsed > timeoutNs) {
                    timeoutWarned = true;
                    totalTimeoutWarnings.increment();
                    long elapsedMs = TimeUnit.NANOSECONDS.toMillis(elapsed);
                    LOGGER.warn("Async entity tick batch exceeded {}ms timeout ({}ms elapsed), still waiting...",
                            AsyncConfig.staleTaskTimeoutMs.getValue(), elapsedMs);
                }

                boolean pumped = waitWorld != null && waitWorld.getChunkSource().pollTask();
                if (!pumped) java.util.concurrent.locks.LockSupport.parkNanos(25_000L);
            }
        } while (!allDone);

        if (interrupted) Thread.currentThread().interrupt();
        TaskFailures.joinCompleted(futures);
    }

    public static boolean shouldTickSynchronously(Entity entity) {
        if (isShuttingDown || entity.level().isClientSide()) return true;

        UUID entityId = entity.getUUID();

        if (AsyncConfig.disabled.getValue()
                || entitySupportsAsyncApi(entity)
                || entity instanceof Projectile
                || entity instanceof AbstractMinecart
                || entity instanceof ServerPlayer
                || BLOCKED_ENTITIES.contains(entity.getClass())
                || blacklistedEntity.contains(entityId)
                || AsyncConfig.isEntitySynchronized(EntityType.getKey(entity.getType()))) {
            return true;
        }

        // NEW: Circuit breaker check - if open for this entity type, tick synchronously
        if (AsyncConfig.enableCircuitBreaker.getValue()
                && !circuitBreaker.shouldTickAsync(entity.getType())) {
            return true;
        }

        if (portalTickSyncMap.containsKey(entityId)) {
            int ticksLeft = portalTickSyncMap.get(entityId);
            if (ticksLeft > 0) {
                portalTickSyncMap.put(entityId, ticksLeft - 1);
                return true;
            }
            portalTickSyncMap.remove(entityId);
        }

        if (isPortalTickRequired(entity)) {
            portalTickSyncMap.put(entityId, 39);
            return true;
        }

        return false;
    }

    public static boolean entitySupportsAsyncApi(Entity entity) {
        return !"minecraft".equals(EntityType.getKey(entity.getType()).getNamespace())
                && !entity.getClass().isAnnotationPresent(AsyncCompatible.class);
    }

    private static boolean isPortalTickRequired(Entity entity) {
        if (entity instanceof EntityAccessor accessor) {
            return accessor.isInsidePortal();
        }
        return false;
    }

    /**
     * IMPROVED: tickEntity now integrates circuit breaker feedback.
     */
    private static void tickEntity(ServerLevel world, Entity entity, boolean async) {
        long start = System.nanoTime();
        currentEntities.incrementAndGet();
        EntityType<?> type = entity.getType();

        try {
            world.tickNonPassenger(entity);

            if (async) {
                totalAsyncTicks.increment();
                if (AsyncConfig.enableCircuitBreaker.getValue()) {
                    circuitBreaker.recordSuccess(type);
                }
            }
        } catch (Exception e) {
            LOGGER.error("Error during {} tick. Entity: {}, UUID: {}",
                    async ? "async" : "sync", type, entity.getUUID(), e);

            if (async) {
                totalAsyncFailures.increment();
                if (AsyncConfig.enableCircuitBreaker.getValue()) {
                    circuitBreaker.recordFailure(type, e);
                }
            }
            if (e instanceof RuntimeException runtime) throw runtime;
            throw new IllegalStateException("Entity tick failed", e);
        } finally {
            currentEntities.decrementAndGet();
            if (TickStats.RECORDING_TICKS_LEFT.get() > 0) {
                long elapsed = System.nanoTime() - start;

                if (async) {
                    TickStats.ASYNC_TICK_TIME_NS.computeIfAbsent(type, k -> new LongAdder()).add(elapsed);
                    TickStats.ASYNC_TICK_COUNT.computeIfAbsent(type, k -> new LongAdder()).increment();
                } else {
                    TickStats.TICK_TIME_NS.computeIfAbsent(type, k -> new LongAdder()).add(elapsed);
                    TickStats.TICK_COUNT.computeIfAbsent(type, k -> new LongAdder()).increment();
                }
            }
        }
    }

    /** Returns the shared entity-operation monitor for one dimension. */
    public static Object getEntityOperationLock(Level level) {
        if (level == null) return ParallelProcessor.class;
        return ENTITY_OPERATION_LOCKS.computeIfAbsent(level.dimension(), ignored -> new Object());
    }

    @Deprecated
    public static void asyncSpawnForChunk(ServerLevel level, LevelChunk chunk, NaturalSpawner.SpawnState spawnState,
            boolean spawnAnimals, boolean spawnMonsters, boolean rareSpawn) {
        NaturalSpawner.spawnForChunk(level, chunk, spawnState, spawnAnimals, spawnMonsters, rareSpawn);
    }

    public static void postEntityTick(ServerLevel world) {
        if (world != null) {
            world.getChunkSource().pollTask();
        }
    }

    public static void stop() {
        isShuttingDown = true;
        if (tickPool != null) {
            LOGGER.info("Waiting for Async tickPool to shutdown...");
            tickPool.shutdown();
            try {
                if (!tickPool.awaitTermination(60L, TimeUnit.SECONDS)) {
                    LOGGER.warn("Async pool did not terminate in 60 seconds");
                    tickPool.shutdownNow();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                LOGGER.warn("Interrupted while waiting for thread pool shutdown", e);
                tickPool.shutdownNow();
            }
        }
        AsyncConfig.clearCaches();
        blacklistedEntity.clear();
        portalTickSyncMap.clear();
        ENTITY_OPERATION_LOCKS.clear();
        circuitBreaker.reset();
        totalAsyncTicks.reset();
        totalAsyncFailures.reset();
        totalTimeoutWarnings.reset();
        lastWorkerCount.set(0);
        gpuCollisionTicks.reset();
        gpuCollisionSavedMs.reset();
        GpuEntityModule.shutdown();
        GpuPushBatch.clear();
        currentEntities.set(0);
        mcThreadTracker.clear();
        tickPool = null;
        server = null;
        TickStats.resetEntityTickStats();
    }

    public static MinecraftServer getServer() {
        return server;
    }

    public static void setServer(MinecraftServer newServer) {
        server = newServer;
    }
}
