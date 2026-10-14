#!/usr/bin/env python3
from pathlib import Path
import sys

if len(sys.argv) != 2:
    raise SystemExit("usage: apply_hardening.py <merged-upstream-root>")

root = Path(sys.argv[1]).resolve()


def file(rel):
    p = root / rel
    if not p.is_file():
        raise SystemExit(f"missing merged file: {rel}")
    return p


def replace(rel, old, new, count=1):
    p = file(rel)
    text = p.read_text(encoding="utf-8")
    found = text.count(old)
    if found != count:
        raise SystemExit(f"source drift in {rel}: expected {count}, found {found}: {old[:140]!r}")
    p.write_text(text.replace(old, new, count), encoding="utf-8")


def replace_between(rel, start_marker, end_marker, replacement):
    p = file(rel)
    text = p.read_text(encoding="utf-8")
    start = text.find(start_marker)
    if start < 0:
        raise SystemExit(f"source drift in {rel}: start marker missing: {start_marker[:120]!r}")
    end = text.find(end_marker, start)
    if end < 0:
        raise SystemExit(f"source drift in {rel}: end marker missing: {end_marker[:120]!r}")
    p.write_text(text[:start] + replacement + text[end:], encoding="utf-8")


# ---------------------------------------------------------------------------
# Vulkan: use six conservative runtime AABB bounds + three output SSBOs.
# ---------------------------------------------------------------------------
replace(
    "common/src/main/java/com/axalotl/async/common/gpu/vulkan/VkComputePipeline.java",
    "private static final int PUSH_SIZE = 16, BINDING_COUNT = 8;",
    "private static final int PUSH_SIZE = 16, BINDING_COUNT = 9;"
)

dispatcher = "common/src/main/java/com/axalotl/async/common/gpu/GpuCollisionDispatcher.java"
replace(dispatcher,
        "import net.minecraft.world.entity.Entity;",
        "import net.minecraft.world.entity.Entity;\nimport net.minecraft.world.phys.AABB;")
replace(dispatcher,
        "    private float[] heights;",
        "    private float[] heights;\n    private float[] maxZ;")
replace(dispatcher,
        "    private VkBufferManager.BufferHandle heightsBuffer;",
        "    private VkBufferManager.BufferHandle heightsBuffer;\n    private VkBufferManager.BufferHandle maxZBuffer;")
replace(dispatcher,
        "        heights = new float[INITIAL_ENTITY_CAPACITY];",
        "        heights = new float[INITIAL_ENTITY_CAPACITY];\n        maxZ = new float[INITIAL_ENTITY_CAPACITY];")
replace(dispatcher,
        "        VkBufferManager.uploadFloats(heightsBuffer, heights, 0, entityCount);",
        "        VkBufferManager.uploadFloats(heightsBuffer, heights, 0, entityCount);\n        VkBufferManager.uploadFloats(maxZBuffer, maxZ, 0, entityCount);")
replace(dispatcher,
'''            posX[i] = (float) entity.getX();
            posY[i] = (float) entity.getY();
            posZ[i] = (float) entity.getZ();
            halfWidths[i] = entity.getBbWidth() * 0.5f;
            heights[i] = entity.getBbHeight();
            entityIndexMap[i] = entity;''',
'''            // Use the actual runtime bounding box, not nominal width/height. Float
            // conversion is rounded outward so GPU broad phase may add candidates
            // but can never lose a vanilla overlap due to precision narrowing.
            AABB box = entity.getBoundingBox();
            posX[i] = Math.nextDown((float) box.minX);
            posY[i] = Math.nextDown((float) box.minY);
            posZ[i] = Math.nextDown((float) box.minZ);
            halfWidths[i] = Math.nextUp((float) box.maxX);
            heights[i] = Math.nextUp((float) box.maxY);
            maxZ[i] = Math.nextUp((float) box.maxZ);
            entityIndexMap[i] = entity;''')
replace(dispatcher,
        "        heights = new float[newCapacity];",
        "        heights = new float[newCapacity];\n        maxZ = new float[newCapacity];")
replace(dispatcher,
'''        heightsBuffer = VkBufferManager.createHostVisibleSSBO(entityFloatSize);
        pairsABuffer = VkBufferManager.createHostVisibleSSBO(pairIntSize);
        pairsBBuffer = VkBufferManager.createHostVisibleSSBO(pairIntSize);
        pairCounterBuffer = VkBufferManager.createHostVisibleSSBO(counterSize);
        pipeline.updateBufferBinding(0, posXBuffer);
        pipeline.updateBufferBinding(1, posYBuffer);
        pipeline.updateBufferBinding(2, posZBuffer);
        pipeline.updateBufferBinding(3, halfWidthsBuffer);
        pipeline.updateBufferBinding(4, heightsBuffer);
        pipeline.updateBufferBinding(5, pairsABuffer);
        pipeline.updateBufferBinding(6, pairsBBuffer);
        pipeline.updateBufferBinding(7, pairCounterBuffer);''',
'''        heightsBuffer = VkBufferManager.createHostVisibleSSBO(entityFloatSize);
        maxZBuffer = VkBufferManager.createHostVisibleSSBO(entityFloatSize);
        pairsABuffer = VkBufferManager.createHostVisibleSSBO(pairIntSize);
        pairsBBuffer = VkBufferManager.createHostVisibleSSBO(pairIntSize);
        pairCounterBuffer = VkBufferManager.createHostVisibleSSBO(counterSize);
        pipeline.updateBufferBinding(0, posXBuffer);
        pipeline.updateBufferBinding(1, posYBuffer);
        pipeline.updateBufferBinding(2, posZBuffer);
        pipeline.updateBufferBinding(3, halfWidthsBuffer);
        pipeline.updateBufferBinding(4, heightsBuffer);
        pipeline.updateBufferBinding(5, maxZBuffer);
        pipeline.updateBufferBinding(6, pairsABuffer);
        pipeline.updateBufferBinding(7, pairsBBuffer);
        pipeline.updateBufferBinding(8, pairCounterBuffer);''')
replace(dispatcher,
        "        VkBufferManager.destroyBuffer(heightsBuffer);",
        "        VkBufferManager.destroyBuffer(heightsBuffer);\n        VkBufferManager.destroyBuffer(maxZBuffer);")
replace(dispatcher,
        "        heightsBuffer = null;",
        "        heightsBuffer = null;\n        maxZBuffer = null;")

# CPU compatibility fallback should use vanilla's runtime AABB as well.
replace_between(
    dispatcher,
    "    private List<CollisionPair> cpuFallback(List<Entity> entities) {",
    "    // ------------------------------------------------------------------ //\n    //  Shader loading",
'''    private List<CollisionPair> cpuFallback(List<Entity> entities) {
        cpuFallbackCount++;
        int size = entities.size();
        if (size < 2) return Collections.emptyList();

        List<CollisionPair> results = new ArrayList<>(Math.min(size * 2, 256));
        for (int i = 0; i < size; i++) {
            Entity a = entities.get(i);
            if (a == null || !a.isAlive()) continue;
            AABB aabb = a.getBoundingBox();
            for (int j = i + 1; j < size; j++) {
                Entity b = entities.get(j);
                if (b == null || !b.isAlive()) continue;
                if (aabb.intersects(b.getBoundingBox())) results.add(new CollisionPair(a, b));
            }
        }
        return results;
    }

''')

# ---------------------------------------------------------------------------
# Managed batching: reserve one logical CPU by default, block Ender Dragon from
# async ticking, and expose a same-tick work-stealing helper for spawn/despawn.
# ---------------------------------------------------------------------------
parallel = "common/src/main/java/com/axalotl/async/common/ParallelProcessor.java"
replace(parallel,
        "import java.util.concurrent.atomic.LongAdder;",
        "import java.util.concurrent.atomic.LongAdder;\nimport java.util.function.Consumer;")
replace(parallel,
        "import net.minecraft.world.entity.item.FallingBlockEntity;",
        "import net.minecraft.world.entity.boss.enderdragon.EnderDragon;\nimport net.minecraft.world.entity.item.FallingBlockEntity;")
replace(parallel,
'''    public static final Set<Class<?>> BLOCKED_ENTITIES = Set.of(
            FallingBlockEntity.class,
            Shulker.class,
            Boat.class);''',
'''    public static final Set<Class<?>> BLOCKED_ENTITIES = Set.of(
            FallingBlockEntity.class,
            Shulker.class,
            Boat.class,
            EnderDragon.class);''')

insert_marker = '''    /**
     * Calculate optimal worker count based on entity count and pool size.'''
helper = '''    /**
     * Runs arbitrary independent work on HMT's managed pool and does not return
     * until every item has finished. Small batches stay on the caller thread.
     * While waiting, waitForFutures keeps pumping server chunk tasks, avoiding
     * worker<->main-thread chunk-future deadlocks.
     */
    @SuppressWarnings("unchecked")
    public static <T> void forEachParallel(List<T> items, Consumer<T> action) {
        if (items == null || items.isEmpty()) return;
        if (items.size() < 64 || tickPool == null || tickPool.isShutdown() || isServerExecutionThread()) {
            for (T item : items) action.accept(item);
            return;
        }

        ConcurrentLinkedQueue<T> work = new ConcurrentLinkedQueue<>(items);
        int poolSize = Math.max(1, getPoolSize());
        int desired = Math.max(1, (items.size() + 31) / 32);
        int workers = Math.min(poolSize, desired);
        List<Future<Void>> futures = new ArrayList<>(workers);
        for (int i = 0; i < workers; i++) {
            futures.add((Future<Void>) tickPool.submit(() -> {
                T item;
                while ((item = work.poll()) != null) {
                    try {
                        action.accept(item);
                    } catch (Throwable t) {
                        LOGGER.error("Error during parallel batch item", t);
                    }
                }
            }));
        }
        waitForFutures(futures);
    }

'''
replace(parallel, insert_marker, helper + insert_marker)

# Remove the obsolete fire-and-forget spawn entry point. Keep a synchronous
# compatibility shim in case an external mixin calls the old public method.
replace_between(
    parallel,
    "    public static void asyncSpawnForChunk(ServerLevel level, LevelChunk chunk, NaturalSpawner.SpawnState spawnState,",
    "    public static void postEntityTick() {",
'''    @Deprecated
    public static void asyncSpawnForChunk(ServerLevel level, LevelChunk chunk, NaturalSpawner.SpawnState spawnState,
            boolean spawnAnimals, boolean spawnMonsters, boolean rareSpawn) {
        NaturalSpawner.spawnForChunk(level, chunk, spawnState, spawnAnimals, spawnMonsters, rareSpawn);
    }

''')

config = "common/src/main/java/com/axalotl/async/common/config/AsyncConfig.java"
replace(config,
'''        if (maxThreads.getValue() <= 0) {
            return Runtime.getRuntime().availableProcessors();
        }''',
'''        if (maxThreads.getValue() <= 0) {
            // Leave one logical processor for the server/render/driver side instead
            // of saturating every hardware thread with entity workers.
            return Math.max(1, Runtime.getRuntime().availableProcessors() - 1);
        }''')

# ---------------------------------------------------------------------------
# Spawn-state construction: use the managed pool, never ForkJoin commonPool.
# ---------------------------------------------------------------------------
natural = "common/src/main/java/com/axalotl/async/common/mixin/spawn/NaturalSpawnerMixin.java"
replace(natural,
        "import com.axalotl.async.common.config.AsyncConfig;",
        "import com.axalotl.async.common.ParallelProcessor;\nimport com.axalotl.async.common.config.AsyncConfig;")
replace(natural,
        "        entityList.parallelStream().forEach(entity -> {",
        "        ParallelProcessor.forEachParallel(entityList, entity -> {")

# ---------------------------------------------------------------------------
# Despawn checks use the same managed batch helper instead of invokeAll on every
# core. The GPU query hook inserted by the first overlay stays intact.
# ---------------------------------------------------------------------------
server_level = "common/src/main/java/com/axalotl/async/common/mixin/world/ServerLevelMixin.java"
replace(server_level, "import java.util.concurrent.Callable;\n", "")
replace(server_level, "import java.util.concurrent.ThreadPoolExecutor;\n", "")
replace_between(
    server_level,
    "        if (!toDespawnCheck.isEmpty()) {",
    "        profiler.push(\"tick\");",
'''        ParallelProcessor.forEachParallel(toDespawnCheck, Entity::checkDespawn);

''')

# ---------------------------------------------------------------------------
# Mixin set: stop replacing vanilla spawn collection identities; use the
# synchronized vanilla SpawnState replacement from the overlay. Add palette lock.
# ---------------------------------------------------------------------------
mixins = "common/src/main/resources/harimt.common.mixins.json"
replace(mixins, '    "entity.spawn.LocalMobCapCalculatorMixin",\n', '')
replace(mixins, '    "entity.spawn.PotentialCalculatorMixin",\n', '')
replace(mixins,
        '    "world.LevelTicksMixin",',
        '    "world.LevelTicksMixin",\n    "world.PalettedContainerMixin",')

print("HariMultiThread Ultimate hardening pass applied successfully")
