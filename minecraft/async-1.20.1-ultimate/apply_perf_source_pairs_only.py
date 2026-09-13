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
for p in (D,P,I,B,S):
    if not p.is_file(): raise SystemExit(f'missing {p}')

def once(text, old, new, label):
    if text.count(old) != 1: raise SystemExit(f'{label} drift: {text.count(old)} matches')
    return text.replace(old,new,1)

s=I.read_text()
old='''    Result compute(\n            float[] minX, float[] minY, float[] minZ,\n            float[] maxX, float[] maxY, float[] maxZ,\n            int count, int maxPairs);\n'''
new=old+'''\n    default Result computeSourcePairs(\n            float[] minX, float[] minY, float[] minZ,\n            float[] maxX, float[] maxY, float[] maxZ,\n            int count, int sourceCount, int maxPairs) {\n        if (sourceCount < 0 || sourceCount > count) throw new IllegalArgumentException("Invalid source count");\n        return compute(minX, minY, minZ, maxX, maxY, maxZ, count, maxPairs);\n    }\n'''
I.write_text(once(s,old,new,'interface compute'))

s=S.read_text()
old='''    uint gid = gl_GlobalInvocationID.x;\n    bool laneActive = gid < uint(entityCount);\n'''
new='''    uint gid = gl_GlobalInvocationID.x;\n    uint sourceCount = reservedInt > 0 ? min(uint(reservedInt), uint(entityCount)) : uint(entityCount);\n    bool laneActive = gid < sourceCount;\n'''
S.write_text(once(s,old,new,'shader lane'))

s=B.read_text()
sig='''    @Override\n    public synchronized Result compute(\n            float[] minX, float[] minY, float[] minZ,\n            float[] maxX, float[] maxY, float[] maxZ,\n            int count, int maxPairs) {\n'''
wrap='''    @Override\n    public synchronized Result compute(\n            float[] minX, float[] minY, float[] minZ,\n            float[] maxX, float[] maxY, float[] maxZ,\n            int count, int maxPairs) {\n        return computeInternal(minX, minY, minZ, maxX, maxY, maxZ, count, count, maxPairs);\n    }\n\n    @Override\n    public synchronized Result computeSourcePairs(\n            float[] minX, float[] minY, float[] minZ,\n            float[] maxX, float[] maxY, float[] maxZ,\n            int count, int sourceCount, int maxPairs) {\n        return computeInternal(minX, minY, minZ, maxX, maxY, maxZ, count, sourceCount, maxPairs);\n    }\n\n    private Result computeInternal(\n            float[] minX, float[] minY, float[] minZ,\n            float[] maxX, float[] maxY, float[] maxZ,\n            int count, int sourceCount, int maxPairs) {\n'''
s=once(s,sig,wrap,'backend compute signature')
s=once(s,'        if (count < 0 || maxPairs <= 0) throw new IllegalArgumentException("Invalid compute sizes");\n','        if (count < 0 || sourceCount < 0 || sourceCount > count || maxPairs <= 0) throw new IllegalArgumentException("Invalid compute sizes");\n','backend validation')
s=once(s,'        if (count < 2) return new Result(new int[0], new int[0], 0, false, 0L);\n','        if (count < 2 || sourceCount == 0) return new Result(new int[0], new int[0], 0, false, 0L);\n','backend empty')
s=once(s,'            dispatch(count, maxPairs);\n','            dispatch(count, sourceCount, maxPairs);\n','backend call')
s=once(s,'    private void dispatch(int count, int maxPairs) {\n','    private void dispatch(int count, int sourceCount, int maxPairs) {\n','dispatch signature')
s=once(s,'''            push.putInt(count).putInt(maxPairs).putFloat(0.0f).putInt(0).flip();\n            vkCmdPushConstants(commandBuffer, pipelineLayout, VK_SHADER_STAGE_COMPUTE_BIT, 0, push);\n            vkCmdDispatch(commandBuffer, (count + 63) / 64, 1, 1);\n''','''            push.putInt(count).putInt(maxPairs).putFloat(0.0f).putInt(sourceCount).flip();\n            vkCmdPushConstants(commandBuffer, pipelineLayout, VK_SHADER_STAGE_COMPUTE_BIT, 0, push);\n            vkCmdDispatch(commandBuffer, (sourceCount + 63) / 64, 1, 1);\n''','dispatch push')
B.write_text(s)

s=D.read_text()
s=once(s,'''import java.util.ArrayList;\nimport java.util.Collections;\nimport java.util.List;\n''','''import java.util.ArrayList;\nimport java.util.Collections;\nimport java.util.IdentityHashMap;\nimport java.util.List;\n''','dispatcher imports')
s=once(s,'''    public synchronized Optional<List<CollisionPair>> computeGpuOnly(List<Entity> entities) {\n        if (entities == null || entities.size() < 16 || !isOperational()) return Optional.empty();\n''','''    public synchronized Optional<List<CollisionPair>> computeGpuOnly(List<Entity> entities) {\n        return computeGpuOnlyInternal(entities, 0);\n    }\n\n    public synchronized Optional<List<CollisionPair>> computeGpuOnlyForSources(\n            List<Entity> entities, List<? extends Entity> sources) {\n        if (entities == null || sources == null || sources.isEmpty()) return Optional.empty();\n        IdentityHashMap<Entity, Boolean> wanted = new IdentityHashMap<>();\n        for (Entity source : sources) if (source != null) wanted.put(source, Boolean.TRUE);\n        if (wanted.isEmpty()) return Optional.empty();\n        IdentityHashMap<Entity, Boolean> found = new IdentityHashMap<>();\n        List<Entity> ordered = new ArrayList<>(entities.size());\n        for (Entity e : entities) {\n            if (wanted.containsKey(e) && found.put(e, Boolean.TRUE) == null) ordered.add(e);\n        }\n        if (found.size() != wanted.size()) return Optional.empty();\n        int sourceCount = ordered.size();\n        for (Entity e : entities) if (!wanted.containsKey(e)) ordered.add(e);\n        if (ordered.size() != entities.size()) return Optional.empty();\n        return computeGpuOnlyInternal(ordered, sourceCount);\n    }\n\n    private Optional<List<CollisionPair>> computeGpuOnlyInternal(List<Entity> entities, int sourceCount) {\n        if (entities == null || entities.size() < 16 || !isOperational()) return Optional.empty();\n        if (sourceCount < 0 || sourceCount > entities.size()) return Optional.empty();\n''','dispatcher entry')
s=once(s,'        VulkanCollisionBackend.Result result = executeGpu(current, count, pairCapacity);\n','        VulkanCollisionBackend.Result result = executeGpu(current, count, sourceCount, pairCapacity);\n','dispatcher first dispatch')
s=once(s,'            result = executeGpu(current, count, retryCapacity);\n','            result = executeGpu(current, count, sourceCount, retryCapacity);\n','dispatcher retry')
s=once(s,'''    private VulkanCollisionBackend.Result executeGpu(VulkanCollisionBackend current, int count, int maxPairs) {\n        Callable<VulkanCollisionBackend.Result> compute =\n                () -> current.compute(minX, minY, minZ, maxX, maxY, maxZ, count, maxPairs);\n        return crashGuard.execute(compute, null);\n    }\n''','''    private VulkanCollisionBackend.Result executeGpu(\n            VulkanCollisionBackend current, int count, int sourceCount, int maxPairs) {\n        Callable<VulkanCollisionBackend.Result> compute = sourceCount > 0 && sourceCount < count\n                ? () -> current.computeSourcePairs(minX, minY, minZ, maxX, maxY, maxZ, count, sourceCount, maxPairs)\n                : () -> current.compute(minX, minY, minZ, maxX, maxY, maxZ, count, maxPairs);\n        return crashGuard.execute(compute, null);\n    }\n''','dispatcher execute')
D.write_text(s)

s=P.read_text()
s=once(s,'''        Optional<List<GpuCollisionDispatcher.CollisionPair>> maybePairs =\n                GpuEntityModule.getCollisionDispatcher().computeGpuOnly(collisionPopulation);\n''','''        Optional<List<GpuCollisionDispatcher.CollisionPair>> maybePairs =\n                GpuEntityModule.getCollisionDispatcher().computeGpuOnlyForSources(collisionPopulation, deferred);\n''','push call')
P.write_text(s)

joined='\n'.join(x.read_text() for x in (D,P,B))
for bad in ('CandidateBatch','computeGpuCandidates(','ensureHostPairCapacity(','BatchState','AtomicIntegerArray nextIndex'):
    if bad in joined: raise SystemExit(f'rejected experiment leaked: {bad}')
for path, mark in ((D,'computeGpuOnlyForSources('),(P,'CollisionPair pair : pairs'),(B,'computeSourcePairs('),(S,'bool laneActive = gid < sourceCount;')):
    if mark not in path.read_text(): raise SystemExit(f'missing invariant {mark}')
print('source-pairs-only candidate applied')
