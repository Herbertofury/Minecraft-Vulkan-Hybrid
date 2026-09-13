#!/usr/bin/env python3
from pathlib import Path
import re
import sys

if len(sys.argv) != 2:
    raise SystemExit('usage: max_benchmark_patch_fixed.py <runtime_perf_benchmark.py>')

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

marker = 'FAR_MARKER_COUNT = 512\n'
if s.count(marker) != 1:
    raise SystemExit('far-marker constant insertion point drift')
s = s.replace(marker, marker + 'LOCAL_NONSOURCE_COUNT = 512\n', 1)

needle = '''def summon_dense_with_noise(h: ServerHarness) -> None:\n    summon_dense(h)\n    summon_far_markers(h)\n\n\n'''
addition = '''def summon_dense_with_noise(h: ServerHarness) -> None:\n    summon_dense(h)\n    summon_far_markers(h)\n\n\ndef summon_local_nonsources(h: ServerHarness) -> None:\n    # Interaction entities are alive, positive-volume, invisible non-LivingEntity\n    # occupants. Keeping them inside the dense cow AABB makes the 2.1.1 spatial\n    # query include them, while they never become deferred pushEntities sources.\n    # This directly exposes candidate<->candidate Vulkan work that source-lane\n    # dispatch can skip without altering any source collision query.\n    created = 0\n    for _ in range(LOCAL_NONSOURCE_COUNT):\n        h.send(\n            'execute in minecraft:overworld run summon minecraft:interaction 8.5 199 8.5 '\n            '{Tags:["harimt_local_noise"],width:0.8f,height:1.8f,response:0b}'\n        )\n        created += 1\n    if created != LOCAL_NONSOURCE_COUNT:\n        raise RuntimeError(f"expected {LOCAL_NONSOURCE_COUNT} local non-sources, created {created}")\n\n\ndef summon_dense_with_local_nonsources(h: ServerHarness) -> None:\n    summon_dense(h)\n    summon_local_nonsources(h)\n\n\ndef summon_affinity_1024(h: ServerHarness) -> None:\n    # 1024 NoAI cows spread over 64 forced chunks, 16 per chunk and spatially\n    # separated. This stresses the default affinity/work-stealing hot path without\n    # turning the lane into another dense-collision benchmark.\n    created = 0\n    offsets = (2.5, 6.5, 10.5, 14.5)\n    for cx in range(8):\n        for cz in range(8):\n            for ox in offsets:\n                for oz in offsets:\n                    x = cx * 16 + ox\n                    z = cz * 16 + oz\n                    h.send(\n                        f'execute in minecraft:overworld run summon minecraft:cow {x:.1f} 199 {z:.1f} '\n                        '{Tags:["harimt_perf"],NoAI:1b,NoGravity:1b,Silent:1b,'\n                        'PersistenceRequired:1b,Invulnerable:1b}'\n                    )\n                    created += 1\n    if created != 1024:\n        raise RuntimeError(f"expected 1024 affinity cows, created {created}")\n\n\n'''
if s.count(needle) != 1:
    raise SystemExit('benchmark helper insertion point drift')
s = s.replace(needle, addition, 1)

# Add local-noise cleanup with the indentation of each existing far-noise cleanup.
# The exact 2.1.1 benchmark has three sites: common preparation, per-scenario reset,
# and the final verifier reset. Requiring all three keeps the patch fail-closed.
cleanup_re = re.compile(r'(?m)^(?P<indent>[ \t]*)h\.send\("kill @e\[tag=harimt_noise\]"\)$')

def add_local_cleanup(match: re.Match[str]) -> str:
    indent = match.group('indent')
    return match.group(0) + '\n' + indent + 'h.send("kill @e[tag=harimt_local_noise]")'

s, cleanup_count = cleanup_re.subn(add_local_cleanup, s)
if cleanup_count != 3:
    raise SystemExit(f'benchmark cleanup shape drift: expected 3 sites, found {cleanup_count}')

needle = '''        workloads.append(run_scenario(\n            h, "dense-256-plus-512-far-markers", summon_dense_with_noise))\n\n        # The verifier caps its input at 512 entities. Remove far background noise\n'''
addition = '''        workloads.append(run_scenario(\n            h, "dense-256-plus-512-far-markers", summon_dense_with_noise))\n        workloads.append(run_scenario(\n            h, "dense-256-plus-512-local-nonsources", summon_dense_with_local_nonsources))\n\n        # Isolate affinity/work-stealing CPU scheduling from the O(n^2) GPU broad\n        # phase. These cows are spatially separated, so vanilla fallback stays cheap.\n        h.send("async gpu toggle")\n        time.sleep(2.0)\n        workloads.append(run_scenario(\n            h, "affinity-1024-64-chunks", summon_affinity_1024))\n        h.send("async gpu toggle")\n        time.sleep(2.0)\n\n        # The verifier caps its input at 512 entities. Remove background noise\n'''
if s.count(needle) != 1:
    raise SystemExit('benchmark workload insertion point drift')
s = s.replace(needle, addition, 1)

# The affinity lane leaves 1024 harimt_perf cows. Recreate the exact 256-cow dense
# verifier population after re-enabling Vulkan so /async gpu test is comparable to
# the accepted 2.1.1 correctness fixture and never inherits the CPU stress lane.
verifier = '''        # The verifier caps its input at 512 entities. Remove background noise\n        # so it checks the exact dense local collision population directly.\n        h.send("kill @e[tag=harimt_noise]")\n        h.send("kill @e[tag=harimt_local_noise]")\n        time.sleep(2.0)\n        start = h.send("async gpu test")\n'''
verifier_new = '''        # The verifier caps its input at 512 entities. Recreate the exact dense\n        # 256-cow local collision population after the 1024-cow CPU-only lane.\n        h.send("kill @e[tag=harimt_perf]")\n        h.send("kill @e[tag=harimt_noise]")\n        h.send("kill @e[tag=harimt_local_noise]")\n        time.sleep(2.0)\n        summon_dense(h)\n        time.sleep(2.0)\n        start = h.send("async gpu test")\n'''
if s.count(verifier) != 1:
    raise SystemExit('final verifier cleanup insertion point drift')
s = s.replace(verifier, verifier_new, 1)

required = (
    'dense-256-plus-512-local-nonsources',
    'affinity-1024-64-chunks',
    'summon_dense_with_local_nonsources',
    'summon_affinity_1024',
    'h.send("kill @e[tag=harimt_perf]")\n        h.send("kill @e[tag=harimt_noise]")\n        h.send("kill @e[tag=harimt_local_noise]")\n        time.sleep(2.0)\n        summon_dense(h)\n        time.sleep(2.0)\n        start = h.send("async gpu test")',
)
for invariant in required:
    if invariant not in s:
        raise SystemExit(f'missing max benchmark invariant: {invariant!r}')
if s.count('h.send("async gpu toggle")') != 2:
    raise SystemExit('expected exactly two GPU toggles around affinity lane')

p.write_text(s, encoding='utf-8')
print('max benchmark workloads added with indentation-safe cleanup and exact verifier reset')
