#!/usr/bin/env python3
"""Full previous native reconciler plus all six actual candidate texture readbacks."""
import argparse
import json
from pathlib import Path
import hari249_verify_native_profile as original
original.JAR_SHA = '8b593bac1ac77670849ed992c808d327dd6d9f188ddfdea341ac88f16edf8c9f'

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
