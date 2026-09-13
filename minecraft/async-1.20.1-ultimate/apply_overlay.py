#!/usr/bin/env python3
from pathlib import Path
import shutil
import sys

if len(sys.argv) != 3:
    raise SystemExit("usage: apply_overlay.py <upstream-root> <overlay-root>")

root = Path(sys.argv[1]).resolve()
overlay = Path(sys.argv[2]).resolve()


def file(rel):
    p = root / rel
    if not p.is_file():
        raise SystemExit(f"missing upstream file: {rel}")
    return p


def replace(rel, old, new, count=1):
    p = file(rel)
    text = p.read_text(encoding="utf-8")
    found = text.count(old)
    if found != count:
        raise SystemExit(f"source drift in {rel}: expected {count} occurrence(s), found {found}: {old[:120]!r}")
    p.write_text(text.replace(old, new, count), encoding="utf-8")


def replace_between(rel, start_marker, end_marker, replacement):
    p = file(rel)
    text = p.read_text(encoding="utf-8")
    start = text.find(start_marker)
    if start < 0:
        raise SystemExit(f"source drift in {rel}: start marker missing")
    end = text.find(end_marker, start)
    if end < 0:
        raise SystemExit(f"source drift in {rel}: end marker missing")
    p.write_text(text[:start] + replacement + text[end:], encoding="utf-8")

# Release identity / license truthfulness
replace("gradle.properties", "version=2.0", "version=2.1.1-noxviola.1")
replace("gradle.properties", "mod_name=HariMultiThread", "mod_name=HariMultiThread Ultimate")
replace("gradle.properties", "license=CC0-1.0", "license=GPL-3.0-only")
replace("gradle.properties",
        "description=Async entity processing with multi-threading for smoother server performance.",
        "description=Release-hardened asynchronous entity ticking with real optional Vulkan collision broad-phase; based on HariMultiThread and Async")
replace("settings.gradle", 'rootProject.name = "Async"', 'rootProject.name = "HariMultiThread-Ultimate"')

# Vulkan ABI: shader has 8 SSBO bindings and 16 bytes of push constants.
replace("common/src/main/java/com/axalotl/async/common/gpu/vulkan/VkComputePipeline.java",
        "private static final int PUSH_SIZE = 28, BINDING_COUNT = 4;",
        "private static final int PUSH_SIZE = 16, BINDING_COUNT = 8;")

# GPU dispatcher: real descriptors, GPU-only API for vanilla fallback, no silent truncation.
dispatcher = "common/src/main/java/com/axalotl/async/common/gpu/GpuCollisionDispatcher.java"
replace(dispatcher, "import java.util.List;", "import java.util.List;\nimport java.util.Optional;")
replace(dispatcher, "private static final float RANGE_LIMIT = 16.0f;", "private static final float RANGE_LIMIT_SQ = 0.0f;")
replace(dispatcher,
        '            LOGGER.debug("GPU collision init error details:", t);\n            shutdownPipeline();',
        '            LOGGER.debug("GPU collision init error details:", t);\n            shutdownPipeline();\n            freeGpuBuffers();')
start = "    public List<CollisionPair> computeBroadPhase(List<Entity> entities) {"
end = "    // ------------------------------------------------------------------ //\n    //  GPU pipeline"
replacement = '''    public Optional<List<CollisionPair>> computeGpuOnly(List<Entity> entities) {
        if (entities == null || entities.size() < 16 || !isGpuReady()) {
            return Optional.empty();
        }
        final int entityCount = entities.size();
        List<CollisionPair> result = crashGuard.execute(() -> {
            ensureBuffers(entityCount);
            extractEntityData(entities);
            uploadAndDispatch(entityCount);
            return downloadPairs(entityCount);
        }, null);
        if (result == null) return Optional.empty();
        gpuCollisionCount++;
        return Optional.of(result);
    }

    /** Compatibility API: prefers GPU, otherwise performs an exact CPU fallback. */
    public List<CollisionPair> computeBroadPhase(List<Entity> entities) {
        if (entities == null || entities.isEmpty()) return Collections.emptyList();
        Optional<List<CollisionPair>> gpu = computeGpuOnly(entities);
        return gpu.orElseGet(() -> cpuFallback(entities));
    }

'''
replace_between(dispatcher, start, end, replacement)
replace(dispatcher,
        "    private boolean isGpuReady() {\n        return pipeline != null && pipeline.isInitialized() && !pipeline.isDeviceLost();\n    }",
        "    private boolean isGpuReady() {\n        return pipeline != null && pipeline.isInitialized() && !pipeline.isDeviceLost();\n    }\n\n    public boolean isOperational() {\n        return isGpuReady();\n    }")
replace(dispatcher,
'''        // Dispatch compute shader with push constants:
        //   entityCount, maxPairs, rangeLimit (packed as float bits for push constants)
        // Push constant layout: [0]=entityCount(int-as-float), [1]=maxPairs(int-as-float),
        //                        [2]=rangeLimit(float), padded to 7 ints (28 bytes)
        int[] pushData = new int[]{
                entityCount,
                MAX_COLLISION_PAIRS,
                Float.floatToIntBits(RANGE_LIMIT),
                0, 0, 0, 0 // padding to fill 28 bytes (7 ints)
        };''',
'''        // 16-byte push constants; range filtering is disabled for correctness.
        int[] pushData = new int[]{
                entityCount,
                MAX_COLLISION_PAIRS,
                Float.floatToIntBits(RANGE_LIMIT_SQ),
                0
        };''')
replace(dispatcher,
'''        // Clamp to maximum to prevent out-of-bounds reads
        if (pairCount > MAX_COLLISION_PAIRS) {
            LOGGER.warn("GPU collision pair count ({}) exceeded max ({}) -- clamping",
                    pairCount, MAX_COLLISION_PAIRS);
            pairCount = MAX_COLLISION_PAIRS;
        }''',
'''        // A truncated broad-phase result is not authoritative.
        if (pairCount > MAX_COLLISION_PAIRS) {
            LOGGER.warn("GPU collision output overflow: {} pairs > {} capacity; using exact fallback",
                    pairCount, MAX_COLLISION_PAIRS);
            return null;
        }''')
replace(dispatcher,
'''        pairCounterBuffer = VkBufferManager.createHostVisibleSSBO(counterSize);
    }''',
'''        pairCounterBuffer = VkBufferManager.createHostVisibleSSBO(counterSize);
        pipeline.updateBufferBinding(0, posXBuffer);
        pipeline.updateBufferBinding(1, posYBuffer);
        pipeline.updateBufferBinding(2, posZBuffer);
        pipeline.updateBufferBinding(3, halfWidthsBuffer);
        pipeline.updateBufferBinding(4, heightsBuffer);
        pipeline.updateBufferBinding(5, pairsABuffer);
        pipeline.updateBufferBinding(6, pairsBBuffer);
        pipeline.updateBufferBinding(7, pairCounterBuffer);
    }''')
replace(dispatcher,
'''        String[] paths = {
                "/assets/async/shaders/collision_broadphase.comp.spv",
                "/assets/async/shaders/collision_broadphase.spv",
                "/assets/async/shaders/collision_broadphase.comp"
        };''',
'''        String[] paths = {
                "/assets/async/shaders/collision_broadphase.comp.spv",
                "/assets/async/shaders/collision_broadphase.spv"
        };''')

# GPU module status must reflect a working dispatcher, not just a Vulkan driver.
module = "common/src/main/java/com/axalotl/async/common/gpu/GpuEntityModule.java"
replace(module,
'''            // 2. Init collision dispatcher
            collisionDispatcher.initialize();
            gpuAvailable = true;

            LOGGER.info("GPU Entity Module initialized on {} (maxWG={}, sharedMem={}KB)",''',
'''            // 2. Init collision dispatcher and report availability only after the
            // shader, pipeline, buffers, and descriptors are all operational.
            collisionDispatcher.initialize();
            gpuAvailable = collisionDispatcher.isOperational();
            if (!gpuAvailable) {
                LOGGER.info("Vulkan device exists but collision pipeline is unavailable - vanilla fallback active");
                return;
            }

            LOGGER.info("GPU Entity Module initialized on {} (maxWG={}, sharedMem={}KB)",''')
replace(module,
'''        String mode = gpuAvailable ? "GPU" : "CPU";

        return String.format(
                "Benchmark (%s, %d entities, 5 runs): avg=%.1f us, pairs=%d",
                mode, ENTITY_COUNT, avgMicros, totalPairs);''',
'''        return String.format(
                "CPU synthetic baseline (%d entities, 5 runs): avg=%.1f us, pairs=%d; Vulkan operational=%s; live GPU dispatches=%d",
                ENTITY_COUNT, avgMicros, totalPairs, gpuAvailable,
                collisionDispatcher.getGpuCollisionCount());''')

# Remove HMT's unused GPU precompute and flush the real deferred vanilla push phase after workers converge.
parallel = "common/src/main/java/com/axalotl/async/common/ParallelProcessor.java"
replace(parallel, "import com.axalotl.async.common.gpu.GpuEntityModule;",
        "import com.axalotl.async.common.gpu.GpuEntityModule;\nimport com.axalotl.async.common.gpu.GpuPushBatch;")
replace_between(parallel,
        "        // --- GPU broad-phase collision pre-compute ---",
        "        if (!asyncEntities.isEmpty()) {",
        "")
replace(parallel,
'''        // Wait for all async workers
        waitForFutures(futures);
        TickStats.RECORDING_TICKS_LEFT.decrementAndGet();''',
'''        // Wait for all async workers, then replay deferred LivingEntity push logic
        // on the server thread. Vulkan is used only for this stable post-barrier phase.
        waitForFutures(futures);
        GpuPushBatch.flush(world);
        TickStats.RECORDING_TICKS_LEFT.decrementAndGet();''')
replace(parallel, "        return Math.max(2, Math.min(poolSize, desired));",
        "        return Math.max(1, Math.min(poolSize, desired));")

# ServerLevel: only the exact deferred push lookup can consume the GPU pair map.
server_level = "common/src/main/java/com/axalotl/async/common/mixin/world/ServerLevelMixin.java"
replace(server_level, "import com.axalotl.async.common.config.AsyncConfig;",
        "import com.axalotl.async.common.config.AsyncConfig;\nimport com.axalotl.async.common.gpu.GpuPushBatch;")
replace(server_level, "import net.minecraft.world.level.storage.WritableLevelData;",
        "import net.minecraft.world.level.storage.WritableLevelData;\nimport net.minecraft.world.phys.AABB;")
insert_marker = '''    @Inject(method = "tick", at = @At("HEAD"))
    private void async$clearPortalCache(java.util.function.BooleanSupplier hasTimeLeft, CallbackInfo ci) {'''
insert_text = '''    @Override
    public List<Entity> getEntities(@Nullable Entity except, AABB box, Predicate<? super Entity> predicate) {
        List<Entity> gpu = GpuPushBatch.tryQuery(this.getLevel(), except, box, predicate);
        return gpu != null ? gpu : super.getEntities(except, box, predicate);
    }

'''
replace(server_level, insert_marker, insert_text + insert_marker)

# Register new deferred push hook/invoker.
mixins = "common/src/main/resources/harimt.common.mixins.json"
replace(mixins, '    "accessor.LevelChunkAccessor",',
        '    "accessor.LevelChunkAccessor",\n    "accessor.LivingEntityPushInvoker",')
replace(mixins, '    "entity.LivingEntityMixin",',
        '    "entity.LivingEntityMixin",\n    "entity.LivingEntityPushMixin",')

# Apply complete replacement/new files from overlay last.
for src in overlay.rglob("*"):
    if not src.is_file():
        continue
    rel = src.relative_to(overlay)
    dst = root / rel
    dst.parent.mkdir(parents=True, exist_ok=True)
    shutil.copy2(src, dst)

print("HariMultiThread Ultimate overlay applied successfully")
