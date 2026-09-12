#!/usr/bin/env python3
from pathlib import Path
import sys

if len(sys.argv) != 2:
    raise SystemExit("usage: apply_perf_spatial_gpu.py <merged-upstream-root>")

root = Path(sys.argv[1]).resolve()
path = root / "common/src/main/java/com/axalotl/async/common/gpu/GpuPushBatch.java"
if not path.is_file():
    raise SystemExit(f"missing transformed source: {path}")

text = path.read_text(encoding="utf-8")

# The transformed release source deliberately has a single, stable buildGpuContext
# method. Replace that whole unit so the performance layer remains auditable and
# source drift fails closed instead of silently half-applying.
start_marker = "    private static QueryContext buildGpuContext(ServerLevel world, List<LivingEntity> deferred) {\n"
end_marker = "\n    /**\n     * Called from the ServerLevel mixin. Null means \"use vanilla\".\n"
start = text.find(start_marker)
end = text.find(end_marker, start + len(start_marker)) if start >= 0 else -1
if start < 0 or end < 0:
    raise SystemExit("source drift: buildGpuContext boundary not found")
if text.find(start_marker, start + 1) >= 0:
    raise SystemExit("source drift: multiple buildGpuContext methods found")

new_method = '''    private static QueryContext buildGpuContext(ServerLevel world, List<LivingEntity> deferred) {
        if (!GpuEntityModule.isGpuAvailable() || !AsyncConfig.enableGpuCollision.getValue()) return null;

        // Partition by 16^3 entity section before touching the GPU. The previous
        // whole-dimension path made two unrelated mob farms multiply each other's
        // O(N^2) broad-phase cost. A single giant union AABB improves far markers
        // but still collapses back into the same problem when the distant entities
        // are LivingEntities that also call pushEntities. Section-local batches
        // keep dense neighborhoods independent while sparse sections stay on the
        // exact vanilla query path and pay no extra spatial lookup.
        Map<SectionKey, List<LivingEntity>> sourceGroups = new HashMap<>();
        IdentityHashMap<LivingEntity, Boolean> seenSources = new IdentityHashMap<>();
        for (LivingEntity source : deferred) {
            if (source == null || source.isRemoved() || source.level() != world) continue;
            if (seenSources.put(source, Boolean.TRUE) != null) continue;
            sourceGroups.computeIfAbsent(SectionKey.of(source), ignored -> new ArrayList<>()).add(source);
        }
        if (sourceGroups.isEmpty()) return null;

        IdentityHashMap<Entity, List<Entity>> candidates = new IdentityHashMap<>();
        int totalPairCount = 0;
        boolean usedGpu = false;

        for (List<LivingEntity> groupSources : sourceGroups.values()) {
            // GPU dispatch has a fixed cost. Sparse neighborhoods are cheaper and
            // safer on vanilla's indexed query, which tryQuery selects by the
            // intentional absence of a candidates entry for that source.
            if (groupSources.size() < MIN_GPU_GROUP_SOURCES) continue;

            AABB queryBounds = groupSources.get(0).getBoundingBox();
            for (int i = 1; i < groupSources.size(); i++) {
                AABB box = groupSources.get(i).getBoundingBox();
                queryBounds = union(queryBounds, box);
            }

            // Use Minecraft's own section index to construct a conservative local
            // population. Any entity that can intersect a deferred source's exact
            // AABB must also intersect this union, so impossible distant entities
            // are removed without changing authoritative collision semantics.
            List<Entity> collisionPopulation = world.getEntities(
                    (Entity) null,
                    queryBounds,
                    current -> current != null && !current.isRemoved() && current.isAlive());
            if (collisionPopulation.size() < MIN_GPU_GROUP_SOURCES) continue;

            long started = System.nanoTime();
            Optional<List<GpuCollisionDispatcher.CollisionPair>> maybePairs =
                    GpuEntityModule.getCollisionDispatcher().computeGpuOnly(collisionPopulation);
            GPU_NANOS.add(System.nanoTime() - started);
            if (maybePairs.isEmpty()) continue;

            IdentityHashMap<Entity, Boolean> groupSourceSet = new IdentityHashMap<>();
            for (LivingEntity source : groupSources) {
                candidates.put(source, new ArrayList<>());
                groupSourceSet.put(source, Boolean.TRUE);
            }

            List<GpuCollisionDispatcher.CollisionPair> pairs = maybePairs.get();
            totalPairCount += pairs.size();
            for (GpuCollisionDispatcher.CollisionPair pair : pairs) {
                Entity a = pair.a();
                Entity b = pair.b();
                if (groupSourceSet.containsKey(a)) candidates.get(a).add(b);
                if (groupSourceSet.containsKey(b)) candidates.get(b).add(a);
            }
            usedGpu = true;
        }

        if (!usedGpu) return null;
        GPU_PAIRS.add(totalPairCount);
        return new QueryContext(world, candidates, totalPairCount);
    }

    private static AABB union(AABB a, AABB b) {
        return new AABB(
                Math.min(a.minX, b.minX),
                Math.min(a.minY, b.minY),
                Math.min(a.minZ, b.minZ),
                Math.max(a.maxX, b.maxX),
                Math.max(a.maxY, b.maxY),
                Math.max(a.maxZ, b.maxZ));
    }
'''
text = text[:start] + new_method + text[end:]

# Add only the collection imports needed by the section partitioner.
imports_anchor = "import java.util.ArrayList;\nimport java.util.IdentityHashMap;\n"
if imports_anchor not in text:
    raise SystemExit("source drift: GpuPushBatch import anchor missing")
text = text.replace(
    imports_anchor,
    "import java.util.ArrayList;\nimport java.util.HashMap;\nimport java.util.IdentityHashMap;\n",
    1,
)
list_anchor = "import java.util.List;\nimport java.util.Optional;\n"
if list_anchor not in text:
    raise SystemExit("source drift: GpuPushBatch List/Optional import anchor missing")
text = text.replace(list_anchor, "import java.util.List;\nimport java.util.Map;\nimport java.util.Optional;\n", 1)

# One threshold and a tiny value key are sufficient; no new global caches or
# lifecycle state are introduced.
logger_anchor = '    private static final Logger LOGGER = LoggerFactory.getLogger("HariMT/GpuPushBatch");\n\n'
if logger_anchor not in text:
    raise SystemExit("source drift: logger anchor missing")
text = text.replace(
    logger_anchor,
    logger_anchor + "    private static final int MIN_GPU_GROUP_SOURCES = 16;\n\n",
    1,
)
context_anchor = "    private static final class QueryContext {\n"
if context_anchor not in text:
    raise SystemExit("source drift: QueryContext anchor missing")
section_key = '''    private record SectionKey(int x, int y, int z) {
        private static SectionKey of(LivingEntity entity) {
            return new SectionKey(
                    ((int) Math.floor(entity.getX())) >> 4,
                    ((int) Math.floor(entity.getY())) >> 4,
                    ((int) Math.floor(entity.getZ())) >> 4);
        }
    }

'''
text = text.replace(context_anchor, section_key + context_anchor, 1)

# Hard release invariants for this layer.
if "world.getAllEntities()" in text:
    raise SystemExit("world-wide GPU population scan survived section-local performance patch")
required = (
    "MIN_GPU_GROUP_SOURCES = 16",
    "Map<SectionKey, List<LivingEntity>> sourceGroups",
    "world.getEntities(",
    "(Entity) null",
    "SectionKey.of(source)",
    "if (groupSources.size() < MIN_GPU_GROUP_SOURCES) continue;",
    "if (!usedGpu) return null;",
)
for needle in required:
    if needle not in text:
        raise SystemExit(f"section-local performance invariant missing: {needle}")

path.write_text(text, encoding="utf-8")
print("HariMultiThread Ultimate section-local Vulkan optimization applied")
