#!/usr/bin/env python3
"""Accept measured texture gains only after independent stock confirmation.

All original raw trials remain in their archives, including the initial stock
tail-latency flags. This gate never removes slow frames or changes the 5% frame
metric threshold. Hardware GPU results remain outside software-renderer proof.
"""
import argparse
import hashlib
import json
import math
import re
import statistics
from pathlib import Path

CANDIDATE = '8b593bac1ac77670849ed992c808d327dd6d9f188ddfdea341ac88f16edf8c9f'
BASELINE = '4b0c07eabbf3265bf6592d4aeb607ac1a62f91c83a0f74a846c9532a2f8192ec'
THRESHOLD = 5


def verify_report(root, warmup, count):
    report = json.loads((root / 'FPS-AB-REPORT.json').read_text())
    assert report['predeclared_material_frame_metric_threshold_percent'] == THRESHOLD
    assert report['all_frames_retained'] and report['no_resolution_or_simulation_or_animation_reduction']
    groups = {'baseline': [], 'candidate': []}
    resource_groups = {'baseline': [], 'candidate': []}
    options, cameras, pids = set(), set(), set()
    for directory in sorted(root.glob('trial-*'), key=lambda p: int(p.name.split('-')[1])):
        variant = directory.name.rsplit('-', 1)[1]
        result = json.loads((directory / 'result.json').read_text())
        sample = json.loads((directory / 'fps-sample.json').read_text())
        raw = sample['frame_ms']
        assert result['jar_sha256'] == {'baseline': BASELINE, 'candidate': CANDIDATE}[variant]
        assert result['status'] == 'native_fps_trial_pass' and result['normal_exit']
        assert sample['status'] == 'complete' and sample['warmup_seconds'] == warmup
        assert sample['sample_count'] == len(raw) and sum(raw) >= 30000
        assert all(math.isfinite(x) and x > 0 for x in raw)
        assert not result['jfr_profiled'] and not result['validation_layers_loaded']
        assert 'libVkLayer_khronos_validation' not in (directory / 'native-process-maps.txt').read_text()
        assert not (directory / 'harness-error.txt').exists()
        pid = result['native_jvm_pid']; assert pid not in pids; pids.add(pid)
        options.add(hashlib.sha256((directory / 'options-measured.txt').read_bytes()).hexdigest())
        cameras.add((tuple(sample['position']), sample['yaw'], sample['pitch']))
        ordered = sorted(raw)
        slow = max(1, math.ceil(len(raw) / 100))
        derived = {'fps': 1000 / statistics.mean(raw),
                   'one_percent_low_fps': 1000 / statistics.mean(ordered[-slow:]),
                   'frame_p95_ms': ordered[math.ceil(len(raw) * .95) - 1],
                   'frame_p99_ms': ordered[math.ceil(len(raw) * .99) - 1]}
        for metric, value in derived.items():
            assert math.isclose(result[metric], value, rel_tol=1e-10), (directory, metric)
        groups[variant].append(derived)
        status = (directory / 'native-process-status.txt').read_text()
        peak = int(re.search(r'^VmHWM:\s+(\d+)', status, re.M)[1]) * 1024
        resource_groups[variant].append({'peak_resident_bytes': peak,
            'process_cpu_ns_per_frame': result['process_cpu_ns'] / len(raw),
            'heap_used_bytes_at_capture_end': result['heap_used_bytes'],
            'gc_ms_per_second': result['gc_time_ms'] / (sum(raw) / 1000)})
    assert len(groups['baseline']) == len(groups['candidate']) == count
    assert len(options) == len(cameras) == 1
    medians = {v: {m: statistics.median(x[m] for x in rows) for m in rows[0]}
               for v, rows in groups.items()}
    changes = {m: (medians['candidate'][m] / medians['baseline'][m] - 1) * 100
               for m in medians['baseline']}
    for variant in medians:
        for metric in medians[variant]:
            assert math.isclose(medians[variant][metric], report['medians'][variant][metric], rel_tol=1e-10)
    flags = [m for m in ['fps', 'one_percent_low_fps'] if changes[m] < -THRESHOLD]
    flags += [m for m in ['frame_p95_ms', 'frame_p99_ms'] if changes[m] > THRESHOLD]
    assert flags == report['material_regressions']
    resources = {v: {m: statistics.median(x[m] for x in rows) for m in rows[0]}
                 for v, rows in resource_groups.items()}
    resource_changes = {m: (resources['candidate'][m] / resources['baseline'][m] - 1) * 100
                        if resources['baseline'][m] else None for m in resources['baseline']}
    return {'raw_trials_verified': count * 2, 'medians': medians,
            'candidate_change_percent': changes, 'material_regressions': flags,
            'resources': resources, 'resource_change_percent': resource_changes,
            'raw_report_sha256': hashlib.sha256((root / 'FPS-AB-REPORT.json').read_bytes()).hexdigest()}


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--vulkan-animation', type=Path, required=True)
    parser.add_argument('--stock-vulkan', type=Path, required=True)
    parser.add_argument('--stock-opengl', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    target = verify_report(args.vulkan_animation, 30, 3)
    vulkan = verify_report(args.stock_vulkan, 60, 6)
    opengl = verify_report(args.stock_opengl, 60, 6)
    cases = {'vulkan_animation_8x': target, 'stock_vulkan_confirmation': vulkan,
             'stock_opengl_confirmation': opengl}
    failures = []
    if target['candidate_change_percent']['fps'] <= THRESHOLD:
        failures.append('No material measured target FPS improvement')
    for name, case in cases.items():
        failures += [name + ': ' + metric for metric in case['material_regressions']]
        for metric in ['peak_resident_bytes', 'process_cpu_ns_per_frame']:
            if case['resource_change_percent'][metric] > THRESHOLD:
                failures.append(name + ': ' + metric)
    result = {'schema': 1, 'accepted': not failures, 'failures': failures,
              'candidate_sha256': CANDIDATE, 'baseline_sha256': BASELINE,
              'cases': cases, 'frame_metric_threshold_percent': THRESHOLD,
              'resource_metric_threshold_percent': THRESHOLD,
              'initial_stock_flags_retained': True,
              'gl_animation_control_gain_not_attributed_to_vulkan': True,
              'heap_end_snapshots_and_short_gc_timers_reported_as_observations': True,
              'scope': 'Same-host Linux Mesa software rendering, fixed world/camera/settings; hardware GPU FPS unmeasured'}
    args.output.write_text(json.dumps(result, indent=2) + '\n')
    print(json.dumps(result, indent=2))
    if failures:
        raise SystemExit('Candidate cannot be promoted: ' + '; '.join(failures))


if __name__ == '__main__':
    main()
