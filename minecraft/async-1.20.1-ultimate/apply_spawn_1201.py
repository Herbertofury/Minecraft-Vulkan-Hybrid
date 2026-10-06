#!/usr/bin/env python3
from pathlib import Path
import sys

if len(sys.argv) != 2:
    raise SystemExit("usage: apply_spawn_1201.py <merged-upstream-root>")

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

server_chunk = "common/src/main/java/com/axalotl/async/common/mixin/server/ServerChunkCacheMixin.java"
mixins = "common/src/main/resources/harimt.common.mixins.json"

# The July 2026 Async rollback targeted a later spawn implementation which can
# re-request the current chunk from inside NaturalSpawner. Forge/Minecraft 1.20.1
# already passes the scheduled ChunkAccess directly through
# spawnForChunk -> spawnCategoryForChunk -> spawnCategoryForPosition. There is no
# ServerLevel#getChunk(int,int) invocation in that target method, so the ported
# redirect cannot match and crashes production Mixin application. Keep the safe
# 1.20.1 shape: the managed spawn task captures and passes the exact LevelChunk
# directly, with no redundant ThreadLocal/mixin shim.
replace(server_chunk, "import com.axalotl.async.common.spawn.SpawnChunkContext;\n", "")
replace(
    server_chunk,
    '''        harimt$spawnTasks.add(() -> SpawnChunkContext.runWith(chunk, () ->
                NaturalSpawner.spawnForChunk(
                        level, chunk, spawnState, spawnAnimals, spawnMonsters, rareSpawn)));''',
    '''        harimt$spawnTasks.add(() -> NaturalSpawner.spawnForChunk(
                level, chunk, spawnState, spawnAnimals, spawnMonsters, rareSpawn));'''
)
replace(mixins, '    "spawn.NaturalSpawnerScheduledChunkMixin",\n', "")

for rel, marker in (
    ("common/src/main/java/com/axalotl/async/common/mixin/spawn/NaturalSpawnerScheduledChunkMixin.java", "harimt$reuseScheduledChunk"),
    ("common/src/main/java/com/axalotl/async/common/spawn/SpawnChunkContext.java", "ThreadLocal<LevelChunk>"),
):
    p = file(rel)
    text = p.read_text(encoding="utf-8")
    if marker not in text:
        raise SystemExit(f"source drift in {rel}: expected marker {marker!r}")
    p.unlink()

server_text = file(server_chunk).read_text(encoding="utf-8")
if "SpawnChunkContext" in server_text:
    raise SystemExit("1.20.1 spawn path still uses later-version ThreadLocal context")
if "harimt$spawnTasks.add(() -> NaturalSpawner.spawnForChunk(" not in server_text:
    raise SystemExit("direct scheduled LevelChunk spawn call missing")
if "NaturalSpawnerScheduledChunkMixin" in file(mixins).read_text(encoding="utf-8"):
    raise SystemExit("invalid 1.20.1 scheduled-chunk redirect still registered")

print("Forge 1.20.1 direct scheduled-chunk spawn adaptation applied successfully")
