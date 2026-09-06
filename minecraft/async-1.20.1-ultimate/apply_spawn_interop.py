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

# Negative/positive assertions so future HMT source changes cannot silently drop
# the rollback semantics while leaving the build green.
server_text = file(server_chunk).read_text(encoding="utf-8")
if "SpawnChunkContext.runWith(chunk" not in server_text:
    raise SystemExit("scheduled spawn chunk context was not wired into ServerChunkCacheMixin")
if "CompletableFuture.runAsync" in server_text:
    raise SystemExit("fire-and-forget spawn/random tick work reappeared")

mixins_text = file(mixins).read_text(encoding="utf-8")
if mixins_text.count('"spawn.NaturalSpawnerScheduledChunkMixin"') != 1:
    raise SystemExit("scheduled-chunk NaturalSpawner mixin registration missing/duplicated")

print("Latest Async scheduled-chunk spawn interop applied successfully")
