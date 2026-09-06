#!/usr/bin/env python3
from pathlib import Path
import sys

if len(sys.argv) != 2:
    raise SystemExit("usage: apply_interop.py <merged-upstream-root>")

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
        raise SystemExit(f"source drift in {rel}: expected {count}, found {found}: {old[:150]!r}")
    p.write_text(text.replace(old, new, count), encoding="utf-8")

# C2ME Forge exposes both c2meforge and a c2me compatibility alias today. Keep
# both IDs so forks that drop the alias still receive HMT's lighting interop.
replace(
    "common/src/main/java/com/axalotl/async/common/AsyncCommon.java",
    'public static boolean HARICHUNK = PlatformUtils.isModLoaded("harichunk") || PlatformUtils.isModLoaded("c2me");',
    'public static boolean HARICHUNK = PlatformUtils.isModLoaded("harichunk") || PlatformUtils.isModLoaded("c2me") || PlatformUtils.isModLoaded("c2meforge");'
)

parallel = "common/src/main/java/com/axalotl/async/common/ParallelProcessor.java"

# Under DimThread SAFE-INTEROP, each dimension temporarily owns its own logical
# main-thread pointer. While HMT waits for entity workers in that dimension, it
# must only pump that dimension's chunk queue. Polling every ServerLevel here can
# execute another dimension's chunk tasks from the wrong DimThread worker.
replace(
    parallel,
    "private static void waitForFutures(List<Future<Void>> futures) {",
    "private static void waitForFutures(List<Future<Void>> futures, ServerLevel waitWorld) {"
)
replace(
    parallel,
'''                boolean pumped = false;
                if (server != null) {
                    for (ServerLevel lvl : server.getAllLevels()) {
                        pumped |= lvl.getChunkSource().pollTask();
                    }
                }
                if (!pumped) Thread.onSpinWait();''',
'''                boolean pumped = waitWorld != null && waitWorld.getChunkSource().pollTask();
                if (!pumped) java.util.concurrent.locks.LockSupport.parkNanos(25_000L);'''
)
replace(
    parallel,
'''        waitForFutures(futures);
        GpuPushBatch.flush(world);''',
'''        waitForFutures(futures, world);
        GpuPushBatch.flush(world);'''
)
replace(
    parallel,
    "public static <T> void forEachParallel(List<T> items, Consumer<T> action) {",
    "public static <T> void forEachParallel(ServerLevel waitWorld, List<T> items, Consumer<T> action) {"
)
# The helper has one remaining barrier call after the entity-tick call above was patched.
replace(parallel, "        waitForFutures(futures);", "        waitForFutures(futures, waitWorld);", count=1)

# The upstream post-batch hook still polls every dimension after each ServerLevel
# entity tick. That defeats the dimension-scoped wait fix above: with DimThread,
# one dimension worker can execute another dimension's chunk task after the HMT
# barrier. Keep the hook, but scope it to the ServerLevel that just completed.
replace(
    parallel,
'''    public static void postEntityTick() {
        if (server != null) {
            for (ServerLevel world : server.getAllLevels()) {
                world.getChunkSource().pollTask();
            }
        }
    }''',
'''    public static void postEntityTick(ServerLevel world) {
        if (world != null) {
            world.getChunkSource().pollTask();
        }
    }'''
)

# Exact callers that know their dimension pass it; createState has no ServerLevel
# parameter, so its immutable result-building batch does not pump chunk tasks.
replace(
    "common/src/main/java/com/axalotl/async/common/mixin/spawn/NaturalSpawnerMixin.java",
    "ParallelProcessor.forEachParallel(entityList, entity -> {",
    "ParallelProcessor.forEachParallel(null, entityList, entity -> {"
)
replace(
    "common/src/main/java/com/axalotl/async/common/mixin/world/ServerLevelMixin.java",
    "ParallelProcessor.forEachParallel(toDespawnCheck, Entity::checkDespawn);",
    "ParallelProcessor.forEachParallel(this.getLevel(), toDespawnCheck, Entity::checkDespawn);"
)
replace(
    "common/src/main/java/com/axalotl/async/common/mixin/world/ServerLevelMixin.java",
    "ParallelProcessor.postEntityTick();",
    "ParallelProcessor.postEntityTick(this.getLevel());"
)
replace(
    "common/src/main/java/com/axalotl/async/common/mixin/server/ServerChunkCacheMixin.java",
    "ParallelProcessor.forEachParallel(chunks, chunk -> {",
    "ParallelProcessor.forEachParallel(this.level, chunks, chunk -> {"
)
replace(
    "common/src/main/java/com/axalotl/async/common/mixin/server/ServerChunkCacheMixin.java",
    "ParallelProcessor.forEachParallel(tasks, Runnable::run);",
    "ParallelProcessor.forEachParallel(this.level, tasks, Runnable::run);"
)

# Fail closed if another cross-dimension task-pump loop is introduced into the
# core processor later; stats/commands may enumerate worlds, the tick engine may not.
if "server.getAllLevels()" in file(parallel).read_text(encoding="utf-8"):
    raise SystemExit("interop invariant failed: ParallelProcessor still pumps all ServerLevels")

print("HariMultiThread Ultimate C2ME/DimThread interop hardening applied successfully")
