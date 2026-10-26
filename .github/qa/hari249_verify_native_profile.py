#!/usr/bin/env python3
"""Reconcile complete immutable native profile proof with the exact candidate."""
import argparse
import hashlib
import json
import re
from pathlib import Path

JAR_SHA = 'c1082c8282ff40221c0ea982ce329ec6ab9a26c80790d643d1277b412b8fb396'


def verify(root):
    inputs = json.loads((root / 'installed-mod-inputs.json').read_text())
    assert inputs['candidate_sha256'] == JAR_SHA and inputs['third_party_mods_unchanged']
    selection = json.loads((root / 'installed-renderer-selection.json').read_text())
    expected = 'VULKAN' if selection['expected_renderer'] == 'vulkan' else 'OPENGL_FALLBACK'
    fixture_bytes = (root / 'client-first/dimension-fixture.json').read_bytes()
    assert fixture_bytes == (root / 'client-reopen/dimension-fixture.json').read_bytes()
    fixture = json.loads(fixture_bytes)
    assert fixture and all('NoAI' not in '\n'.join(s['setup']) for s in fixture)
    namespaces = {s['namespace']: s['expected_entities'] for s in fixture}
    assert namespaces in ({'aether': 18}, {'midnight': 10}, {'aether': 18, 'midnight': 10})
    pids, phases, commands = [], [], []
    for phase, reopen in [('client-first', False), ('client-reopen', True)]:
        directory = root / phase
        assert not (directory / 'harness-error.txt').exists()
        assert not list((directory / 'runtime-crash-reports').glob('*'))
        frames = json.loads((directory / 'frame-sample.json').read_text())
        assert frames['renderer'] == expected
        assert frames['warmup_frames'] == 120 and frames['sample_count'] == len(frames['frame_ms']) == 300
        assert (frames['width'], frames['height'], frames['render_distance']) == (1280, 720, 4)
        assert all(value > 0 for value in frames['frame_ms'])
        scenes = json.loads((directory / 'dimension-results.json').read_text())
        assert {s['namespace']: s['expected_entities'] for s in scenes} == namespaces
        for scene in scenes:
            assert scene['new_jvm_reopen'] is reopen and scene['all_mob_types_present']
            assert scene['block_nbt_retained'] and scene['far_chunks_loaded'] and scene['home_chunks_unloaded']
            assert scene['unexpected_errors'] == 0 and len(scene['mob_views']) == 4
            assert all((directory / v['screenshot']).is_file() for v in scene['mob_views'])
            if not reopen:
                assert scene['portal_return'] and (directory / scene['portal_return']['screenshot']).is_file()
        log = (directory / 'production-client-console.log').read_text()
        assert '--launchTarget, forgeclient' in log and 'forgeclientuserdev' not in log
        assert 'Vulkan push broad-phase sustained: 10 consecutive verified batches completed' in log
        assert 'Saving chunks for level' in log and 'All dimensions are saved' in log
        if inputs['c2me']:
            versions = set(re.findall(r'\[C2ME-Forge\] BOOTING\s+v0\.2\.0-forge\.(9\.[68])', log))
            assert len(versions) == 1, versions
        else:
            versions = set()
        if inputs['lane'] == 'rubidium':
            observed = list(directory.glob('runtime-snapshot-rubidium-ready-*.json'))
            assert len(observed) == 1
            flags = json.loads(observed[0].read_text())
            assert flags['rubidium_cache_loaded'] and flags['rubidium_mutation_monitors'] == {
                'cleanup': True, 'acquire': True, 'invalidate': True}
        if inputs['lane'] == 'oculus-shaders':
            shader = json.loads((directory / 'active-shaderpack.json').read_text())
            assert shader['shaderpack'] == 'MakeUp-UltraFast-9.5f.zip' and shader['enableShaders']
            assert shader['original_oculus_pipeline_marker'] and shader['post_reload_world_rendered']
        startup = list(directory.glob('jvm-thread-startup-*.txt'))
        assert len(startup) == 1
        pids.append(int(startup[0].stem.rsplit('-', 1)[1]))
        if (root / 'pinned-validation-sdk.json').is_file():
            sdk = json.loads((root / 'pinned-validation-sdk.json').read_text())
            assert sdk['sdk_version'] == '1.4.363.0'
            assert sdk['archive_sha256'] == '197962f5cbf80baf2775a03336a01cee7c8745686c65aaa70d3f751ade4d7e43'
            assert sdk['library_sha256'] == 'ea3395cdad554bd92e0f2ee6a7558d2094262c9695355ae0f4815843c504dbf4'
            assert sdk['game_jar_unchanged'] and sdk['synchronization_validation']
            assert sdk['queue_submit_validation_disabled'] is False
            loaded = json.loads((directory / 'native-validation-library.json').read_text())
            assert loaded['sdk_version'] == sdk['sdk_version'] and loaded['no_validation_checks_disabled']
            assert loaded['synchronization_validation_enabled'] == 'VK_VALIDATION_FEATURE_ENABLE_SYNCHRONIZATION_VALIDATION_EXT'
            assert loaded['loaded_original_library'] == [{'pid': pids[-1], 'library': sdk['library'],
                                                          'sha256': sdk['library_sha256']}]
            maps = (directory / f'native-validation-library-maps-{pids[-1]}.txt').read_text()
            assert {line.split(maxsplit=5)[-1] for line in maps.splitlines()
                    if 'libVkLayer_khronos_validation.so' in line} == {sdk['library']}
        commands.append((directory / 'launch-command.txt').read_text())
        phases.append({'phase': phase, 'c2me_versions': sorted(versions),
                       'dimensions': namespaces, 'frame_samples': 300,
                       'renderer': expected, 'original_portals': not reopen,
                       'save_and_clean_close': True})
    assert pids[0] != pids[1] and commands[0] == commands[1]
    files = {p.relative_to(root).as_posix(): hashlib.sha256(p.read_bytes()).hexdigest()
             for p in sorted(root.rglob('*')) if p.is_file()}
    return {'lane': inputs['lane'], 'candidate_sha256': JAR_SHA, 'passed': True,
            'two_distinct_original_client_pids': pids, 'phases': phases,
            'complete_evidence_files': files,
            'scope': 'Linux software Vulkan/OpenGL; original mob AI, native first-JVM portals, unload/return, NBT and same-world second JVM; not all boss combat phases or hardware FPS.'}


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('root', type=Path)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    report = verify(args.root)
    args.output.write_text(json.dumps(report, indent=2) + '\n')
    print(json.dumps({key: report[key] for key in ['lane', 'passed', 'two_distinct_original_client_pids']}))
