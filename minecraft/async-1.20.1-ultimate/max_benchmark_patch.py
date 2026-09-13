#!/usr/bin/env python3
from pathlib import Path
import sys

if len(sys.argv) != 2:
    raise SystemExit('usage: max_benchmark_patch.py <runtime_perf_benchmark.py>')
p = Path(sys.argv[1])
s = p.read_text(encoding='utf-8')

# Keep the existing benchmark lanes and add a deterministic 8x8 forced-chunk
# field for the affinity/work-stealing stress lane.
forceload = '    h.send("forceload add 0 0")\n'
if s.count(forceload) != 1:
    raise SystemExit('common forceload insertion point drift')
s = s.replace(
    forceload,
    forceload + '    h.send("forceload add 0 0 127 127")\n',
    1,
)

s = s.replace('FAR_MARKER_COUNT = 512\n', 'FAR_MARKER_COUNT = 512\nLOCAL_NONSOURCE_COUNT = 512\n', 1)
needle = '''def summon_dense_with_noise(h: ServerHarness) -> None:\n    summon_dense(h)\n    summon_far_markers(h)\n\n\n'''
addition = '''def summon_dense_with_noise(h: ServerHarness) -> None:\n    summon_dense(h)\n    summon_far_markers(h)\n\n\ndef summon_local_nonsources(h: ServerHarness) -> None:\n    # Interaction entities are alive, positive-volume, invisible non-LivingEntity\n    # occupants. Keeping them inside the dense cow AABB makes the 2.1.1 spatial\n    # query include them, while they never become deferred pushEntities sources.\n    # This directly exposes candidate<->candidate Vulkan work that source-lane\n    # dispatch can skip without altering any source collision query.\n    created = 0\n    for _ in range(LOCAL_NONSOURCE_COUNT):\n        h.send(\n            'execute in minecraft:overworld run summon minecraft:interaction 8.5 199 8.5 '\n            '{Tags:["harimt_local_noise"],width:0.8f,height:1.8f,response:0b}'\n        )\n        created += 1\n    if created != LOCAL_NONSOURCE_COUNT:\n        raise RuntimeError(f"expected {LOCAL_NONSOURCE_COUNT} local non-sources, created {created}")\n\n\ndef summon_dense_with_local_nonsources(h: ServerHarness) -> None:\n    summon_dense(h)\n    summon_local_nonsources(h)\n\n\ndef summon_affinity_1024(h: ServerHarness) -> None:\n    # 1024 NoAI cows spread over 64 forced chunks, 16 per chunk and spatially\n    # separated. This stresses the default affinity/work-stealing hot path without\n    # turning the lane into another dense-collision benchmark.\n    created = 0\n    offsets = (2.5, 6.5, 10.5, 14.5)\n    for cx in range(8):\n        for cz in range(8):\n            for ox in offsets:\n                for oz in offsets:\n                    x = cx * 16 + ox\n                    z = cz * 16 + oz\n                    h.send(\n                        f'execute in minecraft:overworld run summon minecraft:cow {x:.1f} 199 {z:.1f} '\n                        '{Tags:["harimt_perf"],NoAI:1b,NoGravity:1b,Silent:1b,'\n                        'PersistenceRequired:1b,Invulnerable:1b}'\n                    )\n                    created += 1\n    if created != 1024:\n        raise RuntimeError(f"expected 1024 affinity cows, created {created}")\n\n\n'''
if s.count(needle) != 1:
    raise SystemExit('benchmark helper insertion point drift')
s = s.replace(needle, addition, 1)

prepare_cleanup = '''    h.send("kill @e[tag=harimt_perf]")
    h.send("kill @e[tag=harimt_noise]")
    h.send("fill 0 198 0 15 198 15 minecraft:stone")
'''
prepare_cleanup_new = '''    h.send("kill @e[tag=harimt_perf]")
    h.send("kill @e[tag=harimt_noise]")
    h.send("kill @e[tag=harimt_local_noise]")
    h.send("fill 0 198 0 15 198 15 minecraft:stone")
'''
if s.count(prepare_cleanup) != 1:
    raise SystemExit('prepare_common cleanup shape drift')
s = s.replace(prepare_cleanup, prepare_cleanup_new, 1)

scenario_cleanup = '''    h.send("kill @e[tag=harimt_perf]")
    h.send("kill @e[tag=harimt_noise]")
    time.sleep(3.0)
'''
scenario_cleanup_new = '''    h.send("kill @e[tag=harimt_perf]")
    h.send("kill @e[tag=harimt_noise]")
    h.send("kill @e[tag=harimt_local_noise]")
    time.sleep(3.0)
'''
if s.count(scenario_cleanup) != 1:
    raise SystemExit('run_scenario cleanup shape drift')
s = s.replace(scenario_cleanup, scenario_cleanup_new, 1)

needle = '''        workloads.append(run_scenario(\n            h, "dense-256-plus-512-far-markers", summon_dense_with_noise))\n\n        # The verifier caps its input at 512 entities. Remove far background noise\n'''
addition = '''        workloads.append(run_scenario(
            h, "dense-256-plus-512-far-markers", summon_dense_with_noise))
        workloads.append(run_scenario(
            h, "dense-256-plus-512-local-nonsources", summon_dense_with_local_nonsources))

        # Isolate affinity/work-stealing CPU scheduling from the O(n^2) GPU broad
        # phase. These cows are spatially separated, so vanilla fallback stays cheap.
        h.send("async gpu toggle")
        time.sleep(2.0)
        workloads.append(run_scenario(
            h, "affinity-1024-64-chunks", summon_affinity_1024))
        h.send("async gpu toggle")
        time.sleep(2.0)

        # The verifier caps its input at 512 entities. Remove background noise
'''
if s.count(needle) != 1:
    raise SystemExit('benchmark workload insertion point drift')
s = s.replace(needle, addition, 1)

# After adding the 1024 affinity lane, the final GPU verifier must not inherit any
# prior population. Recreate the exact 256-cow dense verifier set after cleanup.
verifier = '''        # The verifier caps its input at 512 entities. Remove background noise
        # so it checks the exact dense local collision population directly.
        h.send("kill @e[tag=harimt_noise]")
        time.sleep(2.0)
        start = h.send("async gpu test")
'''
verifier_new = '''        # The verifier caps its input at 512 entities. Recreate the exact dense
        # local collision population after the affinity CPU-only lane.
        h.send("kill @e[tag=harimt_perf]")
        h.send("kill @e[tag=harimt_noise]")
        h.send("kill @e[tag=harimt_local_noise]")
        time.sleep(2.0)
        summon_dense(h)
        time.sleep(2.0)
        start = h.send("async gpu test")
'''
if s.count(verifier) != 1:
    raise SystemExit('final verifier cleanup insertion point drift')
s = s.replace(verifier, verifier_new, 1)

p.write_text(s, encoding='utf-8')
print('max benchmark workloads added')
