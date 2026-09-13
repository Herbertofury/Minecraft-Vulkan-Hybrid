#!/usr/bin/env python3
from pathlib import Path
import sys

if len(sys.argv) != 2:
    raise SystemExit('usage: apply_perf_direct_adjacency.py <merged-upstream-root>')

root = Path(sys.argv[1]).resolve()
dispatcher = root / 'common/src/main/java/com/axalotl/async/common/gpu/GpuCollisionDispatcher.java'
push = root / 'common/src/main/java/com/axalotl/async/common/gpu/GpuPushBatch.java'
for p in (dispatcher, push):
    if not p.is_file():
        raise SystemExit(f'missing transformed source: {p}')

text = dispatcher.read_text(encoding='utf-8')
old_imports = '''import java.util.ArrayList;\nimport java.util.Collections;\nimport java.util.List;\nimport java.util.Optional;\n'''
new_imports = '''import java.util.ArrayList;\nimport java.util.Collections;\nimport java.util.IdentityHashMap;\nimport java.util.List;\nimport java.util.Optional;\n'''
if text.count(old_imports) != 1:
    raise SystemExit('dispatcher import block drift')
text = text.replace(old_imports, new_imports, 1)

old_record = '''    public record CollisionPair(Entity a, Entity b) {}\n'''
new_record = '''    public record CollisionPair(Entity a, Entity b) {}\n    public record CandidateBatch(IdentityHashMap<Entity, List<Entity>> candidates, int pairCount) {}\n'''
if text.count(old_record) != 1:
    raise SystemExit('dispatcher CollisionPair record drift')
text = text.replace(old_record, new_record, 1)

start = text.index('    public synchronized Optional<List<CollisionPair>> computeGpuOnly(List<Entity> entities) {')
end = text.index('    private int capacityForCurrentPopulation(long maxPossiblePairs) {', start)
new_block = r'''    public synchronized Optional<List<CollisionPair>> computeGpuOnly(List<Entity> entities) {
        VulkanCollisionBackend.Result result = computeRawGpuResult(entities);
        if (result == null) return Optional.empty();

        final int count = entities.size();
        int pairCount = result.pairCount();
        int[] a = result.pairsA();
        int[] b = result.pairsB();
        List<CollisionPair> pairs = new ArrayList<>(pairCount);
        for (int i = 0; i < pairCount; i++) {
            int ia = a[i];
            int ib = b[i];
            if (ia < 0 || ib < 0 || ia >= count || ib >= count || ia >= ib) {
                LOGGER.warn("Vulkan backend returned invalid pair ({},{}); using vanilla fallback", ia, ib);
                return Optional.empty();
            }
            Entity ea = entityIndexMap[ia];
            Entity eb = entityIndexMap[ib];
            if (ea == null || eb == null) {
                LOGGER.warn("Vulkan backend referenced a missing entity; using vanilla fallback");
                return Optional.empty();
            }
            pairs.add(new CollisionPair(ea, eb));
        }
        gpuCollisionCount++;
        return Optional.of(pairs);
    }

    /**
     * Deferred push replay consumes candidate adjacency, not standalone pair
     * objects. Populate that adjacency directly from the backend's validated
     * integer pair buffers so dense batches do not allocate one short-lived
     * CollisionPair record for every overlap only to unpack it immediately.
     * The compatibility computeGpuOnly API above remains unchanged for commands
     * and external callers.
     */
    public synchronized Optional<CandidateBatch> computeGpuCandidates(
            List<Entity> entities, List<? extends Entity> sources) {
        if (sources == null || sources.isEmpty()) return Optional.empty();
        VulkanCollisionBackend.Result result = computeRawGpuResult(entities);
        if (result == null) return Optional.empty();

        final int count = entities.size();
        int pairCount = result.pairCount();
        IdentityHashMap<Entity, List<Entity>> candidates =
                new IdentityHashMap<>(Math.max(1, sources.size()));
        for (Entity source : sources) {
            if (source != null) candidates.put(source, null);
        }
        if (candidates.isEmpty()) return Optional.empty();

        // Dense batches used to repeatedly grow hundreds of ArrayLists while the
        // pair records were unpacked. The validated pair count gives us an exact
        // average degree hint, so pre-size once without changing candidate content.
        long averageDegree = pairCount == 0 ? 0L
                : (((long) pairCount * 2L) + candidates.size() - 1L) / candidates.size();
        int expectedCandidates = (int) Math.min(Math.max(0L, averageDegree), Math.max(0, count - 1));
        for (Entity source : sources) {
            if (source != null && candidates.get(source) == null) {
                candidates.put(source, new ArrayList<>(expectedCandidates));
            }
        }

        int[] a = result.pairsA();
        int[] b = result.pairsB();
        for (int i = 0; i < pairCount; i++) {
            int ia = a[i];
            int ib = b[i];
            if (ia < 0 || ib < 0 || ia >= count || ib >= count || ia >= ib) {
                LOGGER.warn("Vulkan backend returned invalid pair ({},{}); using vanilla fallback", ia, ib);
                return Optional.empty();
            }
            Entity ea = entityIndexMap[ia];
            Entity eb = entityIndexMap[ib];
            if (ea == null || eb == null) {
                LOGGER.warn("Vulkan backend referenced a missing entity; using vanilla fallback");
                return Optional.empty();
            }
            List<Entity> aList = candidates.get(ea);
            if (aList != null) aList.add(eb);
            List<Entity> bList = candidates.get(eb);
            if (bList != null) bList.add(ea);
        }
        gpuCollisionCount++;
        return Optional.of(new CandidateBatch(candidates, pairCount));
    }

    private VulkanCollisionBackend.Result computeRawGpuResult(List<Entity> entities) {
        if (entities == null || entities.size() < 16 || !isOperational()) return null;
        ensureCapacity(entities.size());
        extractBounds(entities);
        final int count = entities.size();
        final long maxPossiblePairs = ((long) count * (count - 1L)) / 2L;
        VulkanCollisionBackend current = backend;
        if (current == null) return null;

        int pairCapacity = capacityForCurrentPopulation(maxPossiblePairs);
        VulkanCollisionBackend.Result result = executeGpu(current, count, pairCapacity);
        if (result == null || !current.isOperational()) return null;

        if (result.overflow()) {
            overflowRetryCount++;
            int requiredPairs = result.pairCount();
            if (requiredPairs <= pairCapacity || requiredPairs < 0 || (long) requiredPairs > maxPossiblePairs) {
                overflowFallbackCount++;
                LOGGER.warn("Vulkan broad-phase reported invalid overflow count {} for {} entities; using vanilla fallback",
                        requiredPairs, count);
                return null;
            }

            int retryCapacity = learnPairCapacity(requiredPairs, maxPossiblePairs);
            LOGGER.info("Vulkan broad-phase learned pair capacity {} -> {} after counting {} pairs; future dense batches skip the probe retry",
                    pairCapacity, retryCapacity, requiredPairs);

            result = executeGpu(current, count, retryCapacity);
            if (result == null || !current.isOperational() || result.overflow()) {
                overflowFallbackCount++;
                LOGGER.warn("Vulkan broad-phase adaptive overflow retry failed at {} pairs; using vanilla fallback",
                        retryCapacity);
                return null;
            }
        }

        int pairCount = result.pairCount();
        int[] a = result.pairsA();
        int[] b = result.pairsB();
        if (pairCount < 0 || pairCount > a.length || pairCount > b.length) {
            LOGGER.warn("Vulkan backend returned inconsistent pair buffers; using vanilla fallback");
            return null;
        }
        return result;
    }

'''
text = text[:start] + new_block + text[end:]
dispatcher.write_text(text, encoding='utf-8')

text = push.read_text(encoding='utf-8')
old_import = 'import java.util.Optional;\n'
if text.count(old_import) != 1:
    raise SystemExit('GpuPushBatch Optional import drift')
text = text.replace(old_import, '', 1)
old = '''        long started = System.nanoTime();
        Optional<List<GpuCollisionDispatcher.CollisionPair>> maybePairs =
                GpuEntityModule.getCollisionDispatcher().computeGpuOnly(collisionPopulation);
        GPU_NANOS.add(System.nanoTime() - started);
        if (maybePairs.isEmpty()) return null;

        IdentityHashMap<Entity, List<Entity>> candidates = new IdentityHashMap<>();
        for (LivingEntity source : deferred) candidates.put(source, new ArrayList<>());

        List<GpuCollisionDispatcher.CollisionPair> pairs = maybePairs.get();
        GPU_PAIRS.add(pairs.size());
        for (GpuCollisionDispatcher.CollisionPair pair : pairs) {
            Entity a = pair.a();
            Entity b = pair.b();
            List<Entity> aList = candidates.get(a);
            if (aList != null) aList.add(b);
            List<Entity> bList = candidates.get(b);
            if (bList != null) bList.add(a);
        }
        return new QueryContext(world, candidates, pairs.size());
'''
new = '''        long started = System.nanoTime();
        java.util.Optional<GpuCollisionDispatcher.CandidateBatch> maybeBatch =
                GpuEntityModule.getCollisionDispatcher().computeGpuCandidates(collisionPopulation, deferred);
        GPU_NANOS.add(System.nanoTime() - started);
        if (maybeBatch.isEmpty()) return null;

        GpuCollisionDispatcher.CandidateBatch batch = maybeBatch.get();
        GPU_PAIRS.add(batch.pairCount());
        return new QueryContext(world, batch.candidates(), batch.pairCount());
'''
if text.count(old) != 1:
    raise SystemExit('GpuPushBatch pair materialization block drift')
text = text.replace(old, new, 1)
push.write_text(text, encoding='utf-8')

dispatch_text = dispatcher.read_text(encoding='utf-8')
push_text = push.read_text(encoding='utf-8')
for marker in ('computeGpuOnly(List<Entity> entities)', 'computeGpuCandidates(', 'CandidateBatch'):
    if marker not in dispatch_text:
        raise SystemExit(f'missing dispatcher invariant: {marker}')
if 'computeGpuOnly(collisionPopulation)' in push_text or 'CollisionPair pair : pairs' in push_text:
    raise SystemExit('old deferred-push pair materialization survived')
if 'computeGpuCandidates(collisionPopulation, deferred)' not in push_text:
    raise SystemExit('direct adjacency call missing from GpuPushBatch')

print('HariMultiThread Ultimate allocation-free GPU candidate adjacency applied')
