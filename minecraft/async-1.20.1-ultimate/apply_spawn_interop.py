#!/usr/bin/env python3
from pathlib import Path
import sys

if len(sys.argv) != 2:
    raise SystemExit("usage: apply_spawn_interop.py <merged-upstream-root>")

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

# Latest Async (July 28, 2026) kept asynchronous spawning but rolled back broad
# worker-thread chunk lookup handling. Each spawn worker instead carries the exact
# LevelChunk that ServerChunkCache already scheduled. Port that model to 1.20.1.
server_chunk = "common/src/main/java/com/axalotl/async/common/mixin/server/ServerChunkCacheMixin.java"
replace(
    server_chunk,
    "import com.axalotl.async.common.config.AsyncConfig;",
    "import com.axalotl.async.common.config.AsyncConfig;\nimport com.axalotl.async.common.spawn.SpawnChunkContext;"
)
replace(
    server_chunk,
'''        harimt$spawnTasks.add(() -> NaturalSpawner.spawnForChunk(
                level, chunk, spawnState, spawnAnimals, spawnMonsters, rareSpawn));''',
'''        harimt$spawnTasks.add(() -> SpawnChunkContext.runWith(chunk, () ->
                NaturalSpawner.spawnForChunk(
                        level, chunk, spawnState, spawnAnimals, spawnMonsters, rareSpawn)));'''
)

mixins = "common/src/main/resources/harimt.common.mixins.json"
replace(
    mixins,
    '    "spawn.NaturalSpawnerMixin",',
    '    "spawn.NaturalSpawnerMixin",\n    "spawn.NaturalSpawnerScheduledChunkMixin",'
)

# Mixin 0.8.5's annotation processor correctly rejects @Inject handlers declared
# in an interface mixin ("Injector in interface is unsupported"). The target is
# itself a Java interface default method, so use the supported interface @Overwrite
# form instead. Preserve vanilla 1.20.1 exactly when async spawn is disabled and
# preserve HMT's existing synchronized entity-add behavior when it is enabled.
server_level_accessor = "common/src/main/java/com/axalotl/async/common/mixin/spawn/ServerLevelAccessorMixin.java"
replace(
    server_level_accessor,
'''import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;''',
'''import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;'''
)
replace(
    server_level_accessor,
'''    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    @Inject(method={"addFreshEntityWithPassengers"}, at={@At(value="HEAD")}, cancellable=true)
    default public void async$syncAddFreshEntityWithPassengers(Entity entity, CallbackInfo ci) {
        if (AsyncConfig.disabled.getValue().booleanValue() || !AsyncConfig.enableAsyncSpawn.getValue().booleanValue()) {
            return;
        }
        ci.cancel();
        Object object = ParallelProcessor.getEntityAddLock();
        synchronized (object) {
            entity.getSelfAndPassengers().forEach(e -> ((ServerLevelAccessor)this).addFreshEntity(e));
        }
    }''',
'''    /**
     * Replaces the vanilla interface default method so Forge's Mixin AP can
     * generate a production refmap. The disabled path is vanilla 1.20.1's exact
     * self-and-passenger add loop; async-spawn mode retains HMT's add lock.
     */
    @Overwrite
    default void addFreshEntityWithPassengers(Entity entity) {
        if (AsyncConfig.disabled.getValue().booleanValue() || !AsyncConfig.enableAsyncSpawn.getValue().booleanValue()) {
            entity.getSelfAndPassengers().forEach(e -> ((ServerLevelAccessor)this).addFreshEntity(e));
            return;
        }
        Object object = ParallelProcessor.getEntityAddLock();
        synchronized (object) {
            entity.getSelfAndPassengers().forEach(e -> ((ServerLevelAccessor)this).addFreshEntity(e));
        }
    }'''
)

# Negative/positive assertions so future HMT source changes cannot silently drop
# the rollback semantics or reintroduce an AP-invalid interface injector.
server_text = file(server_chunk).read_text(encoding="utf-8")
if "SpawnChunkContext.runWith(chunk" not in server_text:
    raise SystemExit("scheduled spawn chunk context was not wired into ServerChunkCacheMixin")
if "CompletableFuture.runAsync" in server_text:
    raise SystemExit("fire-and-forget spawn/random tick work reappeared")

mixins_text = file(mixins).read_text(encoding="utf-8")
if mixins_text.count('"spawn.NaturalSpawnerScheduledChunkMixin"') != 1:
    raise SystemExit("scheduled-chunk NaturalSpawner mixin registration missing/duplicated")

accessor_text = file(server_level_accessor).read_text(encoding="utf-8")
if "@Inject" in accessor_text or "CallbackInfo" in accessor_text:
    raise SystemExit("AP-invalid injector remains in ServerLevelAccessorMixin")
if accessor_text.count("@Overwrite") != 1:
    raise SystemExit("ServerLevelAccessorMixin must contain exactly one supported @Overwrite")
if "default void addFreshEntityWithPassengers(Entity entity)" not in accessor_text:
    raise SystemExit("ServerLevelAccessor vanilla default-method overwrite missing")
if accessor_text.count("entity.getSelfAndPassengers().forEach(e -> ((ServerLevelAccessor)this).addFreshEntity(e));") != 2:
    raise SystemExit("ServerLevelAccessor vanilla/HMT spawn bodies drifted")

print("Latest Async scheduled-chunk spawn interop + AP-safe ServerLevelAccessor mixin applied successfully")
