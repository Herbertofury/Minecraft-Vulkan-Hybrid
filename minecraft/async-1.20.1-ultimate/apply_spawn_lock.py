#!/usr/bin/env python3
from pathlib import Path
import sys

if len(sys.argv) != 2:
    raise SystemExit("usage: apply_spawn_lock.py <merged-upstream-root>")

root = Path(sys.argv[1]).resolve()


def file(rel: str) -> Path:
    p = root / rel
    if not p.is_file():
        raise SystemExit(f"missing merged file: {rel}")
    return p


def replace(rel: str, old: str, new: str, count: int = 1) -> None:
    p = file(rel)
    text = p.read_text(encoding="utf-8")
    found = text.count(old)
    if found != count:
        raise SystemExit(f"source drift in {rel}: expected {count}, found {found}: {old[:180]!r}")
    p.write_text(text.replace(old, new, count), encoding="utf-8")

parallel = "common/src/main/java/com/axalotl/async/common/ParallelProcessor.java"
server_level = "common/src/main/java/com/axalotl/async/common/mixin/world/ServerLevelMixin.java"
server_accessor = "common/src/main/java/com/axalotl/async/common/mixin/spawn/ServerLevelAccessorMixin.java"
projectile = "common/src/main/java/com/axalotl/async/common/mixin/entity/ProjectileEntityMixin.java"

# The old compatibility helper returned new Object() on every call, so callers'
# synchronized blocks never contended with one another. Use one lock per dimension
# and share it across the complete passenger add, ordinary addFreshEntity, and
# projectile setup paths. ResourceKey keeps dimensions independent; stop() clears
# the map so integrated/dedicated server restarts cannot retain stale lock state.
replace(
    parallel,
    "import net.minecraft.server.MinecraftServer;",
    "import net.minecraft.resources.ResourceKey;\nimport net.minecraft.server.MinecraftServer;"
)
replace(
    parallel,
    "import net.minecraft.world.level.NaturalSpawner;",
    "import net.minecraft.world.level.Level;\nimport net.minecraft.world.level.NaturalSpawner;"
)
replace(
    parallel,
    "    private static final Map<String, Set<WeakReference<Thread>>> mcThreadTracker = new ConcurrentHashMap<>();",
    "    private static final Map<String, Set<WeakReference<Thread>>> mcThreadTracker = new ConcurrentHashMap<>();\n"
    "    private static final ConcurrentHashMap<ResourceKey<Level>, Object> ENTITY_OPERATION_LOCKS = new ConcurrentHashMap<>();"
)
replace(
    parallel,
'''    /**
     * @deprecated Per-dimension locks are now handled directly in ServerLevelMixin.
     */
    @Deprecated
    public static Object getEntityAddLock() {
        // Legacy fallback - should not be used anymore
        return new Object();
    }''',
'''    /** Returns the shared entity-operation monitor for one dimension. */
    public static Object getEntityOperationLock(Level level) {
        if (level == null) return ParallelProcessor.class;
        return ENTITY_OPERATION_LOCKS.computeIfAbsent(level.dimension(), ignored -> new Object());
    }'''
)
replace(
    parallel,
    "        portalTickSyncMap.clear();",
    "        portalTickSyncMap.clear();\n        ENTITY_OPERATION_LOCKS.clear();"
)

replace(
    server_level,
'''    @Unique
    private final Object async$entityAddLock = new Object();
''',
    ""
)
replace(
    server_level,
    "        synchronized (this.async$entityAddLock) {",
    "        synchronized (ParallelProcessor.getEntityOperationLock(this.getLevel())) {"
)

replace(
    server_accessor,
    "        Object object = ParallelProcessor.getEntityAddLock();",
    "        Object object = ParallelProcessor.getEntityOperationLock(((ServerLevelAccessor)this).getLevel());"
)
replace(
    projectile,
    "        Object object = ParallelProcessor.getEntityAddLock();",
    "        Object object = ParallelProcessor.getEntityOperationLock(shooter.level());"
)

parallel_text = file(parallel).read_text(encoding="utf-8")
if "getEntityAddLock()" in parallel_text:
    raise SystemExit("broken new-object entity add lock helper remains")
if parallel_text.count("getEntityOperationLock(Level level)") != 1:
    raise SystemExit("per-dimension entity operation lock helper missing/duplicated")
if "ENTITY_OPERATION_LOCKS.clear();" not in parallel_text:
    raise SystemExit("entity operation lock map is not cleared on shutdown")

server_level_text = file(server_level).read_text(encoding="utf-8")
if "async$entityAddLock" in server_level_text:
    raise SystemExit("private addFreshEntity lock still diverges from shared dimension lock")
if "ParallelProcessor.getEntityOperationLock(this.getLevel())" not in server_level_text:
    raise SystemExit("ServerLevel addFreshEntity is not using shared dimension lock")

accessor_text = file(server_accessor).read_text(encoding="utf-8")
if "getEntityOperationLock(((ServerLevelAccessor)this).getLevel())" not in accessor_text:
    raise SystemExit("passenger-group add is not using shared dimension lock")
projectile_text = file(projectile).read_text(encoding="utf-8")
if "getEntityOperationLock(shooter.level())" not in projectile_text:
    raise SystemExit("projectile setup is not using shared dimension lock")

print("HariMultiThread per-dimension entity operation locking applied successfully")
