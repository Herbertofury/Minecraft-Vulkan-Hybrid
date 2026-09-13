#!/usr/bin/env python3
from pathlib import Path
import sys

if len(sys.argv) != 2:
    raise SystemExit("usage: apply_spatial_gpu.py <merged-upstream-root>")

root = Path(sys.argv[1]).resolve()
path = root / "common/src/main/java/com/axalotl/async/common/gpu/GpuPushBatch.java"
if not path.is_file():
    raise SystemExit(f"missing transformed source: {path}")

text = path.read_text(encoding="utf-8")
old = '''        List<Entity> allEntities = new ArrayList<>();
        for (Entity current : world.getAllEntities()) {
            if (current != null && !current.isRemoved() && current.isAlive()) allEntities.add(current);
        }
        if (allEntities.size() < 16) return null;

        long started = System.nanoTime();
        Optional<List<GpuCollisionDispatcher.CollisionPair>> maybePairs =
                GpuEntityModule.getCollisionDispatcher().computeGpuOnly(allEntities);
'''
new = '''        // Only entities intersecting the conservative union of deferred push
        // source AABBs can possibly participate in any replayed pushEntities query.
        // The old path uploaded every live entity in the entire dimension, causing
        // unrelated farms/machines/markers in distant loaded chunks to inflate the
        // O(N^2) Vulkan broad phase. ServerLevel#getEntities uses the vanilla spatial
        // section index and exact AABB intersection, so this removes impossible
        // candidates without changing authoritative collision semantics.
        AABB queryBounds = deferred.get(0).getBoundingBox();
        for (int i = 1; i < deferred.size(); i++) {
            AABB box = deferred.get(i).getBoundingBox();
            queryBounds = new AABB(
                    Math.min(queryBounds.minX, box.minX),
                    Math.min(queryBounds.minY, box.minY),
                    Math.min(queryBounds.minZ, box.minZ),
                    Math.max(queryBounds.maxX, box.maxX),
                    Math.max(queryBounds.maxY, box.maxY),
                    Math.max(queryBounds.maxZ, box.maxZ));
        }

        // Cast null to Entity so Java selects Level#getEntities(Entity, AABB,
        // Predicate) rather than the EntityTypeTest overload.
        List<Entity> collisionPopulation = world.getEntities(
                (Entity) null,
                queryBounds,
                current -> current != null && !current.isRemoved() && current.isAlive());
        if (collisionPopulation.size() < 16) return null;

        long started = System.nanoTime();
        Optional<List<GpuCollisionDispatcher.CollisionPair>> maybePairs =
                GpuEntityModule.getCollisionDispatcher().computeGpuOnly(collisionPopulation);
'''
count = text.count(old)
if count != 1:
    raise SystemExit(f"source drift: expected one world-wide Vulkan population block, found {count}")
text = text.replace(old, new, 1)

# Production invariant: remove the dimension-wide scan from the deferred-push
# Vulkan path while preserving the command verifier's separate bounded scan.
if "for (Entity current : world.getAllEntities())" in text:
    raise SystemExit("world-wide GPU population scan survived spatial optimization")
if "world.getEntities(" not in text or "queryBounds" not in text:
    raise SystemExit("spatial population query missing after optimization")

path.write_text(text, encoding="utf-8")
print("HariMultiThread Ultimate spatial Vulkan population optimization applied")
