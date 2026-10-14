#!/usr/bin/env python3
"""Full previous native reconciler plus all six actual candidate texture readbacks."""
import argparse
import json
import inspect
from pathlib import Path
import hari249_verify_native_profile as original
original.JAR_SHA = '8b593bac1ac77670849ed992c808d327dd6d9f188ddfdea341ac88f16edf8c9f'

# A faster reopened JVM can finish before the scheduled 180-second diagnostic.
# Its actual loaded-library PID, launcher PID and /proc mapping provide identity
# without extending gameplay or manufacturing a thread dump.
body = inspect.getsource(original.verify)
old = """        startup = list(directory.glob('jvm-thread-startup-*.txt'))
        assert len(startup) == 1
        pids.append(int(startup[0].stem.rsplit('-', 1)[1]))"""
new = r"""        startup = list(directory.glob('jvm-thread-startup-*.txt'))
        if len(startup) == 1:
            native_pid = int(startup[0].stem.rsplit('-', 1)[1])
        else:
            assert not startup and expected == 'VULKAN'
            loaded_identity = json.loads((directory / 'native-validation-library.json').read_text())['loaded_original_library']
            assert len(loaded_identity) == 1
            native_pid = loaded_identity[0]['pid']
            assert re.search(r'Process:\s*' + str(native_pid) + r'\b', (directory / 'launcher-stdout.log').read_text())
            assert (directory / f'native-validation-library-maps-{native_pid}.txt').is_file()
        pids.append(native_pid)"""
assert body.count(old) == 1
body = body.replace(old, new)
# Installing the SDK on a host does not load a Vulkan library in a GL client.
# Require the complete pinned native library receipt for every Vulkan phase.
validation_guard = "        if (root / 'pinned-validation-sdk.json').is_file():"
assert body.count(validation_guard) == 1
body = body.replace(validation_guard, "        if expected == 'VULKAN':\n            assert (root / 'pinned-validation-sdk.json').is_file()")
exec(compile(body, str(Path(original.__file__)), 'exec'), original.__dict__)


def verify(root):
    report = original.verify(root)
    proofs = []
    if report['phases'][0]['renderer'] == 'VULKAN':
        for phase in ['client-first','client-reopen']:
            data = json.loads((root/phase/'texture-upload-proof.json').read_text())
            assert data['passed'] and data['overlapping_writes'] and data['padded_rows_and_nonzero_buffer_position']
            assert {(r['format'],r['mip']) for r in data['results']} == {(format,mip) for format in [9,37] for mip in range(3)}
            assert len({r['expected_and_actual_sha256'] for r in data['results']}) == 6
            proofs.append(data)
        assert proofs[0] == proofs[1]
    report['exact_vulkan_texture_readback'] = proofs if proofs else 'OpenGL compatibility renderer: Vulkan texture path not active'
    return report

if __name__ == '__main__':
    p = argparse.ArgumentParser();p.add_argument('root',type=Path);p.add_argument('--output',type=Path,required=True)
    a = p.parse_args();report=verify(a.root)
    a.output.write_text(json.dumps(report,indent=2)+'\n')
    print(json.dumps({k:report[k] for k in ['lane','passed','two_distinct_original_client_pids']}))
