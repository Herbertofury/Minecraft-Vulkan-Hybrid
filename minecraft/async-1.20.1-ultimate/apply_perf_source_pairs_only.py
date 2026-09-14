#!/usr/bin/env python3
from pathlib import Path
import sys

if len(sys.argv) != 2:
    raise SystemExit('usage: apply_perf_source_pairs_only.py <merged-2.1.1-root>')
r = Path(sys.argv[1]).resolve()
D = r/'common/src/main/java/com/axalotl/async/common/gpu/GpuCollisionDispatcher.java'
P = r/'common/src/main/java/com/axalotl/async/common/gpu/GpuPushBatch.java'
I = r/'common/src/main/java/com/axalotl/async/common/gpu/vulkan/VulkanCollisionBackend.java'
B = r/'vulkan-backend/src/main/java/com/axalotl/async/vulkanruntime/LwjglVulkanBackend.java'
S = r/'common/src/main/resources/assets/async/shaders/collision_broadphase.comp'
for path in (D, P, I, B, S):
    if not path.is_file():
        raise SystemExit(f'missing {path}')


def once(text, old, new, label):
    found = text.count(old)
    if found != 1:
        raise SystemExit(f'{label} drift: {found} matches')
    return text.replace(old, new, 1)


# Read and validate everything before writing anything. Reapplication or source
# drift must fail closed without partially mutating the merged tree.
d0 = D.read_text(encoding='utf-8')
p0 = P.read_text(encoding='utf-8')
i0 = I.read_text(encoding='utf-8')
b0 = B.read_text(encoding='utf-8')
s0 = S.read_text(encoding='utf-8')
preexisting = (
    ('dispatcher source entry', 'computeGpuOnlyForSources(', d0),
    ('push source entry', 'computeGpuOnlyForSources(', p0),
    ('interface source entry', 'computeSourcePairs(', i0),
    ('backend source entry', 'computeSourcePairs(', b0),
    ('backend source dispatch', 'dispatchSourcePairs(', b0),
)
for label, marker, text in preexisting:
    if marker in text:
        raise SystemExit(f'{label} already present; refusing reapplication')
if s0.count('bool laneActive = gid < uint(entityCount);') != 1:
    raise SystemExit('released shader lane marker drift')

# Alternate backends remain correctness-first: source-restricted dispatch is an
# optional acceleration and defaults to the released all-pairs implementation.
i = i0
old = '''    Result compute(\n            float[] minX, float[] minY, float[] minZ,\n            float[] maxX, float[] maxY, float[] maxZ,\n            int count, int maxPairs);\n'''
new = old + '''\n    default Result computeSourcePairs(\n            float[] minX, float[] minY, float[] minZ,\n            float[] maxX, float[] maxY, float[] maxZ,\n            int count, int sourceCount, int maxPairs) {\n        if (sourceCount < 0 || sourceCount > count) throw new IllegalArgumentException("Invalid source count");\n        return compute(minX, minY, minZ, maxX, maxY, maxZ, count, maxPairs);\n    }\n'''
i = once(i, old, new, 'interface compute')

# Keep released compute() and dispatch() byte-for-byte unchanged. The mixed-only
# entry points are cloned from those proven methods and specialize only the
# source-count validation plus the number of dispatched workgroups.
b = b0
compute_sig = '''    @Override\n    public synchronized Result compute(\n            float[] minX, float[] minY, float[] minZ,\n            float[] maxX, float[] maxY, float[] maxZ,\n            int count, int maxPairs) {\n'''
compute_start = b.find(compute_sig)
if compute_start < 0:
    raise SystemExit('backend compute signature drift')
compute_end = b.find('\n    private void upload(', compute_start)
if compute_end < 0:
    raise SystemExit('backend compute end marker drift')
released_compute = b[compute_start:compute_end]
source_compute = released_compute
source_compute = once(source_compute, 'public synchronized Result compute(',
                      'public synchronized Result computeSourcePairs(',
                      'backend source compute name')
source_compute = once(source_compute, '            int count, int maxPairs) {\n',
                      '            int count, int sourceCount, int maxPairs) {\n',
                      'backend source compute args')
source_compute = once(
    source_compute,
    '        if (count < 0 || maxPairs <= 0) throw new IllegalArgumentException("Invalid compute sizes");\n',
    '        if (count < 0 || sourceCount <= 0 || sourceCount > count || maxPairs <= 0) throw new IllegalArgumentException("Invalid compute sizes");\n',
    'backend source validation')
source_compute = once(source_compute, '            dispatch(count, maxPairs);\n',
                      '            dispatchSourcePairs(count, sourceCount, maxPairs);\n',
                      'backend source dispatch call')
b = b[:compute_end] + '\n\n' + source_compute + b[compute_end:]

dispatch_sig = '    private void dispatch(int count, int maxPairs) {\n'
dispatch_start = b.find(dispatch_sig)
if dispatch_start < 0:
    raise SystemExit('backend dispatch signature drift')
dispatch_end = b.find('\n    private void destroyBuffers()', dispatch_start)
if dispatch_end < 0:
    raise SystemExit('backend dispatch end marker drift')
released_dispatch = b[dispatch_start:dispatch_end]
source_dispatch = released_dispatch
source_dispatch = once(source_dispatch, dispatch_sig,
                       '    private void dispatchSourcePairs(int count, int sourceCount, int maxPairs) {\n',
                       'backend source dispatch signature')
source_dispatch = once(
    source_dispatch,
    '            vkCmdDispatch(commandBuffer, (count + 63) / 64, 1, 1);\n',
    '            vkCmdDispatch(commandBuffer, (sourceCount + 63) / 64, 1, 1);\n',
    'backend source dispatch groups')
b = b[:dispatch_end] + '\n\n' + source_dispatch + b[dispatch_end:]

# Preserve computeGpuOnly() and executeGpu() byte-for-byte for all-source scenes.
# Mixed scenes reuse GpuPushBatch's already-required identity candidate map as
# the source membership set and allocate only the reordered population list.
d = once(
    d0,
    '''import java.util.ArrayList;\nimport java.util.Collections;\nimport java.util.List;\n''',
    '''import java.util.ArrayList;\nimport java.util.Collections;\nimport java.util.IdentityHashMap;\nimport java.util.List;\n''',
    'dispatcher imports')
method_sig = '    public synchronized Optional<List<CollisionPair>> computeGpuOnly(List<Entity> entities) {\n'
method_start = d.find(method_sig)
if method_start < 0:
    raise SystemExit('dispatcher computeGpuOnly signature drift')
method_end = d.find('\n    private int capacityForCurrentPopulation(', method_start)
if method_end < 0:
    raise SystemExit('dispatcher computeGpuOnly end marker drift')
released_method = d[method_start:method_end]
restricted_method = released_method
restricted_method = once(restricted_method, 'computeGpuOnly(List<Entity> entities)',
                         'computeGpuOnlyRestricted(List<Entity> entities, int sourceCount)',
                         'dispatcher restricted name')
restricted_method = once(
    restricted_method,
    '        if (entities == null || entities.size() < 16 || !isOperational()) return Optional.empty();\n',
    '        if (entities == null || entities.size() < 16 || !isOperational()) return Optional.empty();\n'
    '        if (sourceCount <= 0 || sourceCount >= entities.size()) return Optional.empty();\n',
    'dispatcher restricted validation')
restricted_method = restricted_method.replace(
    'executeGpu(current, count, pairCapacity)',
    'executeGpuSourcePairs(current, count, sourceCount, pairCapacity)')
restricted_method = restricted_method.replace(
    'executeGpu(current, count, retryCapacity)',
    'executeGpuSourcePairs(current, count, sourceCount, retryCapacity)')
if restricted_method.count('executeGpuSourcePairs(') != 2:
    raise SystemExit('dispatcher restricted dispatch drift')

source_entry = '''\n\n    public synchronized Optional<List<CollisionPair>> computeGpuOnlyForSources(\n            List<Entity> entities, IdentityHashMap<Entity, ?> sources) {\n        if (entities == null || sources == null || sources.isEmpty()) return Optional.empty();\n\n        // Fast path: spatial population contains only deferred sources. Minecraft's\n        // entity query yields each live entity once, so equal cardinality plus\n        // identity membership proves set equality without another temporary map.\n        if (entities.size() == sources.size()) {\n            for (Entity entity : entities) {\n                if (entity == null || !sources.containsKey(entity)) return Optional.empty();\n            }\n            return computeGpuOnly(entities);\n        }\n\n        List<Entity> ordered = new ArrayList<>(entities.size());\n        int sourceCount = 0;\n        for (Entity entity : entities) {\n            if (entity != null && sources.containsKey(entity)) {\n                ordered.add(entity);\n                sourceCount++;\n            }\n        }\n        if (sourceCount != sources.size() || sourceCount <= 0 || sourceCount >= entities.size()) {\n            return Optional.empty();\n        }\n        for (Entity entity : entities) {\n            if (entity != null && !sources.containsKey(entity)) ordered.add(entity);\n        }\n        if (ordered.size() != entities.size()) return Optional.empty();\n        return computeGpuOnlyRestricted(ordered, sourceCount);\n    }\n\n'''
d = d[:method_end] + source_entry + restricted_method + d[method_end:]

execute_sig = '''    private VulkanCollisionBackend.Result executeGpu(VulkanCollisionBackend current, int count, int maxPairs) {\n        Callable<VulkanCollisionBackend.Result> compute =\n                () -> current.compute(minX, minY, minZ, maxX, maxY, maxZ, count, maxPairs);\n        return crashGuard.execute(compute, null);\n    }\n'''
execute_pos = d.find(execute_sig)
if execute_pos < 0:
    raise SystemExit('dispatcher executeGpu drift')
execute_end = execute_pos + len(execute_sig)
source_execute = '''\n\n    private VulkanCollisionBackend.Result executeGpuSourcePairs(\n            VulkanCollisionBackend current, int count, int sourceCount, int maxPairs) {\n        Callable<VulkanCollisionBackend.Result> compute =\n                () -> current.computeSourcePairs(minX, minY, minZ, maxX, maxY, maxZ, count, sourceCount, maxPairs);\n        return crashGuard.execute(compute, null);\n    }\n'''
d = d[:execute_end] + source_execute + d[execute_end:]

# Reuse the candidate map that released replay already needs. Moving its creation
# ahead of dispatch removes the old candidate's two temporary IdentityHashMaps.
p = p0
old = '''        long started = System.nanoTime();\n        Optional<List<GpuCollisionDispatcher.CollisionPair>> maybePairs =\n                GpuEntityModule.getCollisionDispatcher().computeGpuOnly(collisionPopulation);\n        GPU_NANOS.add(System.nanoTime() - started);\n        if (maybePairs.isEmpty()) return null;\n\n        IdentityHashMap<Entity, List<Entity>> candidates = new IdentityHashMap<>();\n        for (LivingEntity source : deferred) candidates.put(source, new ArrayList<>());\n'''
new = '''        IdentityHashMap<Entity, List<Entity>> candidates = new IdentityHashMap<>();\n        for (LivingEntity source : deferred) candidates.put(source, new ArrayList<>());\n\n        long started = System.nanoTime();\n        Optional<List<GpuCollisionDispatcher.CollisionPair>> maybePairs =\n                GpuEntityModule.getCollisionDispatcher().computeGpuOnlyForSources(collisionPopulation, candidates);\n        GPU_NANOS.add(System.nanoTime() - started);\n        if (maybePairs.isEmpty()) return null;\n'''
p = once(p, old, new, 'push source membership reuse')

# Invariants before commit-to-disk.
joined = '\n'.join((d, p, b))
for bad in ('CandidateBatch', 'computeGpuCandidates(', 'ensureHostPairCapacity(', 'BatchState', 'AtomicIntegerArray nextIndex'):
    if bad in joined:
        raise SystemExit(f'rejected experiment leaked: {bad}')
for mark in ('return computeGpuOnly(entities);', 'computeGpuOnlyRestricted(ordered, sourceCount)', 'executeGpuSourcePairs('):
    if mark not in d:
        raise SystemExit(f'missing dispatcher invariant: {mark}')
for mark in ('public synchronized Result computeSourcePairs(', 'private void dispatchSourcePairs(',
             'vkCmdDispatch(commandBuffer, (sourceCount + 63) / 64, 1, 1);'):
    if mark not in b:
        raise SystemExit(f'missing backend invariant: {mark}')
if 'computeGpuOnlyForSources(collisionPopulation, candidates)' not in p:
    raise SystemExit('push source-membership invariant missing')
if 'CollisionPair pair : pairs' not in p:
    raise SystemExit('released CollisionPair replay missing')
# Prove the original shader is untouched by this transform.
if s0.count('bool laneActive = gid < uint(entityCount);') != 1:
    raise SystemExit('released shader invariant missing')

# All validation passed. Write the four-file candidate atomically at script level.
I.write_text(i, encoding='utf-8')
B.write_text(b, encoding='utf-8')
D.write_text(d, encoding='utf-8')
P.write_text(p, encoding='utf-8')
print('source-pairs fastpath candidate applied')
