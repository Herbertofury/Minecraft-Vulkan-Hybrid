#!/usr/bin/env python3
from pathlib import Path
import sys

if len(sys.argv) != 2: raise SystemExit('usage: max_benchmark_patch.py <runtime_perf_benchmark.py>')
p=Path(sys.argv[1]); s=p.read_text(encoding='utf-8')
forceload='    h.send("forceload add 0 0")\n'
if s.count(forceload)!=1: raise SystemExit('common forceload insertion point drift')
s=s.replace(forceload,forceload+'    h.send("forceload add 0 0 127 127")\n',1)
s=s.replace('FAR_MARKER_COUNT = 512\n','FAR_MARKER_COUNT = 512\nLOCAL_NONSOURCE_COUNT = 512\n',1)
needle='''def summon_dense_with_noise(h: ServerHarness) -> None:\n    summon_dense(h)\n    summon_far_markers(h)\n\n\n'''
addition='''def summon_dense_with_noise(h: ServerHarness) -> None:\n    summon_dense(h)\n    summon_far_markers(h)\n\n\ndef summon_local_nonsources(h: ServerHarness) -> None:\n    created = 0\n    for _ in range(LOCAL_NONSOURCE_COUNT):\n        h.send('execute in minecraft:overworld run summon minecraft:interaction 8.5 199 8.5 {Tags:["harimt_local_noise"],width:0.8f,height:1.8f,response:0b}')\n        created += 1\n    if created != LOCAL_NONSOURCE_COUNT: raise RuntimeError(f"expected {LOCAL_NONSOURCE_COUNT} local non-sources, created {created}")\n\n\ndef summon_dense_with_local_nonsources(h: ServerHarness) -> None:\n    summon_dense(h); summon_local_nonsources(h)\n\n\ndef summon_affinity_1024(h: ServerHarness) -> None:\n    created=0; offsets=(2.5,6.5,10.5,14.5)\n    for cx in range(8):\n        for cz in range(8):\n            for ox in offsets:\n                for oz in offsets:\n                    x=cx*16+ox; z=cz*16+oz\n                    h.send(f'execute in minecraft:overworld run summon minecraft:cow {x:.1f} 199 {z:.1f} '+'{Tags:["harimt_perf"],NoAI:1b,NoGravity:1b,Silent:1b,PersistenceRequired:1b,Invulnerable:1b}')\n                    created += 1\n    if created != 1024: raise RuntimeError(f"expected 1024 affinity cows, created {created}")\n\n\n'''
if s.count(needle)!=1: raise SystemExit('benchmark helper insertion point drift')
s=s.replace(needle,addition,1)
prepare='''    h.send("kill @e[tag=harimt_perf]")\n    h.send("kill @e[tag=harimt_noise]")\n    h.send("fill 0 198 0 15 198 15 minecraft:stone")\n'''
prepare_new='''    h.send("kill @e[tag=harimt_perf]")\n    h.send("kill @e[tag=harimt_noise]")\n    h.send("kill @e[tag=harimt_local_noise]")\n    h.send("fill 0 198 0 15 198 15 minecraft:stone")\n'''
if s.count(prepare)!=1: raise SystemExit('prepare_common cleanup shape drift')
s=s.replace(prepare,prepare_new,1)
scenario='''    h.send("kill @e[tag=harimt_perf]")\n    h.send("kill @e[tag=harimt_noise]")\n    time.sleep(3.0)\n'''
scenario_new='''    h.send("kill @e[tag=harimt_perf]")\n    h.send("kill @e[tag=harimt_noise]")\n    h.send("kill @e[tag=harimt_local_noise]")\n    time.sleep(3.0)\n'''
if s.count(scenario)!=1: raise SystemExit('run_scenario cleanup shape drift')
s=s.replace(scenario,scenario_new,1)
needle='''        workloads.append(run_scenario(\n            h, "dense-256-plus-512-far-markers", summon_dense_with_noise))\n\n        # The verifier caps its input at 512 entities. Remove far background noise\n'''
addition='''        workloads.append(run_scenario(\n            h, "dense-256-plus-512-far-markers", summon_dense_with_noise))\n        workloads.append(run_scenario(\n            h, "dense-256-plus-512-local-nonsources", summon_dense_with_local_nonsources))\n        h.send("async gpu toggle"); time.sleep(2.0)\n        workloads.append(run_scenario(h, "affinity-1024-64-chunks", summon_affinity_1024))\n        h.send("async gpu toggle"); time.sleep(2.0)\n\n        # The verifier caps its input at 512 entities. Remove background noise\n'''
if s.count(needle)!=1: raise SystemExit('benchmark workload insertion point drift')
s=s.replace(needle,addition,1)
verifier='''        # The verifier caps its input at 512 entities. Remove background noise\n        # so it checks the exact dense local collision population directly.\n        h.send("kill @e[tag=harimt_noise]")\n        time.sleep(2.0)\n        start = h.send("async gpu test")\n'''
verifier_new='''        # Recreate exact 256-cow dense verifier after CPU-only affinity lane.\n        h.send("kill @e[tag=harimt_perf]")\n        h.send("kill @e[tag=harimt_noise]")\n        h.send("kill @e[tag=harimt_local_noise]")\n        time.sleep(2.0); summon_dense(h); time.sleep(2.0)\n        start = h.send("async gpu test")\n'''
if s.count(verifier)!=1: raise SystemExit('final verifier cleanup insertion point drift')
s=s.replace(verifier,verifier_new,1)
p.write_text(s,encoding='utf-8'); print('five-lane benchmark harness applied')
