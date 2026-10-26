#!/usr/bin/env python3
"""Reconcile every alternating native FPS trial and report same-host medians."""
import hashlib
import json
import math
import statistics
import sys
from pathlib import Path

root = Path(sys.argv[1])
pins = {'baseline':'4b0c07eabbf3265bf6592d4aeb607ac1a62f91c83a0f74a846c9532a2f8192ec',
        'candidate':'8b593bac1ac77670849ed992c808d327dd6d9f188ddfdea341ac88f16edf8c9f'}
groups={'baseline':[],'candidate':[]};options=set();scenes=[];pids=set();receipts=[]
for directory in sorted(root.glob('trial-*')):
    variant=directory.name.rsplit('-',1)[1]
    result=json.loads((directory/'result.json').read_text())
    frames=json.loads((directory/'fps-sample.json').read_text())
    assert result['status']=='native_fps_trial_pass' and result['jar_sha256']==pins[variant]
    assert frames['status']=='complete' and frames['sample_count']==len(frames['frame_ms'])
    assert frames['warmup_seconds']==30 and sum(frames['frame_ms'])>=30000
    assert all(math.isfinite(x) and x>0 for x in frames['frame_ms'])
    assert result['native_jvm_pid'] not in pids; pids.add(result['native_jvm_pid'])
    assert not result['validation_layers_loaded'] and not result['jfr_profiled'] and result['normal_exit']
    options.add(hashlib.sha256((directory/'options-measured.txt').read_bytes()).hexdigest())
    scenes.append(frames['scene'])
    assert not (directory/'harness-error.txt').exists()
    assert abs(result['fps']-1000/statistics.mean(frames['frame_ms']))<1e-8
    result['camera_position']=frames['position'];result['yaw']=frames['yaw'];result['pitch']=frames['pitch']
    groups[variant].append(result)
    receipts.append({'directory':directory.name,'variant':variant,'result':result})
assert len(groups['baseline'])==len(groups['candidate'])==3 and len(options)==1
assert all(s==scenes[0] for s in scenes)
assert len({tuple(r['result']['camera_position']) for r in receipts})==1
assert len({(r['result']['yaw'],r['result']['pitch']) for r in receipts})==1
metrics=['fps','one_percent_low_fps','frame_p95_ms','frame_p99_ms','heap_used_bytes','gc_time_ms','process_cpu_ns']
summary={v:{m:statistics.median(r[m] for r in groups[v]) for m in metrics} for v in groups}
delta={m:(summary['candidate'][m]/summary['baseline'][m]-1)*100 if summary['baseline'][m] else None for m in metrics}
regressions=[m for m in ['fps','one_percent_low_fps'] if delta[m] < -5]
regressions += [m for m in ['frame_p95_ms','frame_p99_ms'] if delta[m]>5]
report={'schema':1,'status':'measured','scope':'Linux Mesa software graphics, same host and world/settings/camera, 30s capture after 30s warmup; hardware FPS unmeasured',
        'all_six_original_jvm_trials_verified':True,'same_settings_sha256':next(iter(options)),
        'scene':scenes[0],'medians':summary,'candidate_change_percent':delta,'trials':receipts,
        'fps_gain':delta['fps']>5,'material_regressions':regressions,
        'predeclared_material_frame_metric_threshold_percent':5,
        'all_frames_retained':True,'no_resolution_or_simulation_or_animation_reduction':True}
(root/'FPS-AB-REPORT.json').write_text(json.dumps(report,indent=2)+'\n')
print(json.dumps({k:v for k,v in report.items() if k!='trials'},indent=2))
# A noisy or slower candidate is never promoted by this report. Investigate all regressions.
