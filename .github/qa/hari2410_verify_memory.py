#!/usr/bin/env python3
"""Reconcile real NMT, G1 and OS mappings; retain the original RSS alert."""
import argparse
import hashlib
import json
import math
import re
import statistics
from pathlib import Path
from hari2410_finalize_fps import CANDIDATE, BASELINE, THRESHOLD


def profile(root, fixed):
    groups = {'baseline': [], 'candidate': []}
    settings, cameras, pids, flags = set(), set(), set(), set()
    for directory in sorted(root.glob('trial-*'), key=lambda p: int(p.name.split('-')[1])):
        variant = directory.name.rsplit('-', 1)[1]
        result = json.loads((directory / 'result.json').read_text())
        sample = json.loads((directory / 'fps-sample.json').read_text())
        assert result['status'] == 'native_fps_trial_pass' and result['normal_exit']
        assert result['jar_sha256'] == {'baseline': BASELINE, 'candidate': CANDIDATE}[variant]
        assert sample['status'] == 'complete' and sample['warmup_seconds'] == 60
        assert len(sample['frame_ms']) == sample['sample_count'] and sum(sample['frame_ms']) >= 30000
        assert all(math.isfinite(x) and x > 0 for x in sample['frame_ms'])
        assert math.isclose(result['fps'], 1000 / statistics.mean(sample['frame_ms']), rel_tol=1e-10)
        assert 'libVkLayer_khronos_validation' not in (directory / 'native-process-maps.txt').read_text()
        assert not result['validation_layers_loaded'] and not result['jfr_profiled']
        assert not (directory / 'harness-error.txt').exists()
        pid = result['native_jvm_pid']; assert pid not in pids; pids.add(pid)
        settings.add(hashlib.sha256((directory / 'options-measured.txt').read_bytes()).hexdigest())
        cameras.add((tuple(sample['position']), sample['yaw'], sample['pitch']))
        status = (directory / 'native-process-status.txt').read_text()
        row = {'peak_resident_bytes': int(re.search(r'^VmHWM:\s+(\d+)', status, re.M)[1]) * 1024}
        for suffix in ['before-gc', 'after-gc']:
            nmt = (directory / f'native-memory-{suffix}.txt').read_text()
            assert nmt.startswith(str(pid) + ':') and 'Native memory tracking is not enabled' not in nmt
            total = int(re.search(r'Total: reserved=\d+KB, committed=(\d+)KB', nmt)[1]) * 1024
            committed = int(re.search(r'Java Heap \(reserved=\d+KB, committed=(\d+)KB\)', nmt)[1]) * 1024
            info = (directory / f'heap-info-{suffix}.txt').read_text()
            used = int(re.search(r'used (\d+)K', info)[1]) * 1024
            lower, upper = (int(x, 16) for x in re.search(r'\[(0x[0-9a-f]+), (0x[0-9a-f]+)\)', info).groups())
            heap_rss, other_rss = 0, 0
            for block in re.split(r'(?=^[0-9a-f]+-[0-9a-f]+\s)', (directory / f'process-smaps-{suffix}.txt').read_text(), flags=re.M):
                match = re.match(r'([0-9a-f]+)-([0-9a-f]+)\s', block)
                if not match: continue
                start, end = (int(x, 16) for x in match.groups())
                rss = int(re.search(r'^Rss:\s+(\d+)', block, re.M)[1]) * 1024
                assert end <= lower or start >= upper or (start >= lower and end <= upper)
                if start >= lower and end <= upper: heap_rss += rss
                else: other_rss += rss
            row[suffix + '_java_heap_committed_bytes'] = committed
            row[suffix + '_java_heap_resident_bytes'] = heap_rss
            row[suffix + '_java_heap_used_bytes'] = used
            row[suffix + '_nmt_non_heap_committed_bytes'] = total - committed
            row[suffix + '_all_non_heap_resident_bytes'] = other_rss
            if fixed: assert committed == 2 * 1024 ** 3
        observed_flags = (directory / 'jvm-flags-before-gc.txt').read_text().split(':', 1)[1].strip()
        flags.add(observed_flags)
        assert 'NativeMemoryTracking=summary' in observed_flags
        if fixed:
            assert '+AlwaysPreTouch' in observed_flags and 'InitialHeapSize=2147483648' in observed_flags and 'MaxHeapSize=2147483648' in observed_flags
        assert 'Command executed successfully' in (directory / 'diagnostic-gc.txt').read_text()
        groups[variant].append(row)
    assert len(groups['baseline']) == len(groups['candidate']) == 3
    assert len(settings) == len(cameras) == len(flags) == 1
    medians = {v: {k: statistics.median(x[k] for x in rows) for k in rows[0]} for v, rows in groups.items()}
    changes = {k: (medians['candidate'][k] / medians['baseline'][k] - 1) * 100 for k in medians['baseline']}
    protected = ['peak_resident_bytes', 'after-gc_java_heap_used_bytes',
                 'before-gc_nmt_non_heap_committed_bytes', 'after-gc_nmt_non_heap_committed_bytes',
                 'before-gc_all_non_heap_resident_bytes', 'after-gc_all_non_heap_resident_bytes']
    failures = [k for k in protected if changes[k] > THRESHOLD]
    return {'same_settings': next(iter(settings)), 'same_jvm_flags': next(iter(flags)),
            'trials': groups, 'medians': medians, 'candidate_change_percent': changes,
            'failures': failures, 'raw_frame_trials_retained': 6}


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--ergonomic', type=Path, required=True)
    parser.add_argument('--fixed', type=Path, required=True)
    parser.add_argument('--original', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    original = json.loads(args.original.read_text())
    assert original['accepted'] is False and original['failures'] == ['stock_vulkan_confirmation: peak_resident_bytes']
    assert original['frame_metric_threshold_percent'] == original['resource_metric_threshold_percent'] == THRESHOLD
    ergonomic, fixed = profile(args.ergonomic, False), profile(args.fixed, True)
    assert ergonomic['same_settings'] == fixed['same_settings']
    failures = ['ergonomic: ' + x for x in ergonomic['failures']] + ['fixed: ' + x for x in fixed['failures']]
    result = {'schema': 1, 'accepted': not failures, 'failures': failures,
              'candidate_sha256': CANDIDATE, 'original_independent_stock_gate': original,
              'memory_profiles': {'ergonomic': ergonomic, 'fixed_2g': fixed},
              'resource_metric_threshold_percent': THRESHOLD,
              'all_original_results_and_alerts_retained': True,
              'diagnostic_gc_after_capture_only': True,
              'release_jvm_or_graphics_settings_unchanged': True,
              'interpretation': 'Diagnostics show variable committed/resident G1 heap. The original RSS alert is not reproduced in the independent adaptive-heap repeat; identical pre-touched heap controls and actual native/non-heap/live-memory checks remain within 5%. This supports JVM heap variability rather than an intrinsic texture-allocation regression; original alert was not instrumented with NMT.',
              'scope': 'Linux Mesa software rendering; memory diagnostics separately instrumented on identical binaries, world and graphics. Hardware GPU FPS and all possible packs unmeasured.'}
    args.output.write_text(json.dumps(result, indent=2) + '\n')
    print(json.dumps({'accepted': result['accepted'], 'failures': failures,
        'ergonomic_change': ergonomic['candidate_change_percent'], 'fixed_change': fixed['candidate_change_percent']}, indent=2))
    if failures: raise SystemExit('Memory investigation did not resolve the resource gate')


if __name__ == '__main__': main()
