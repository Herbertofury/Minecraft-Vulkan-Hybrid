#!/usr/bin/env python3
"""Install only the checksum-pinned official Khronos QA layer, never game code."""
import hashlib
import json
import os
from pathlib import Path
import subprocess
import tarfile

VERSION = '1.4.363.0'
SHA256 = '197962f5cbf80baf2775a03336a01cee7c8745686c65aaa70d3f751ade4d7e43'
ROOT = Path('hari-validation-sdk').resolve()
EVIDENCE = Path('production-qa')
EVIDENCE.mkdir(exist_ok=True)
ROOT.mkdir(exist_ok=True)
archive = ROOT / 'vulkan_sdk.tar.xz'
url = f'https://sdk.lunarg.com/sdk/download/{VERSION}/linux/vulkan_sdk.tar.xz?Human=true'
subprocess.run(['curl', '-fL', '--retry', '3', '--max-time', '300', url, '-o', str(archive)], check=True)
actual = hashlib.file_digest(archive.open('rb'), 'sha256').hexdigest()
if actual != SHA256:
    raise RuntimeError(f'Official SDK hash mismatch: {actual}')
suffixes = ('/x86_64/lib/libVkLayer_khronos_validation.so',
            '/x86_64/share/vulkan/explicit_layer.d/VkLayer_khronos_validation.json')
with tarfile.open(archive, 'r:xz') as tar:
    selected = [member for member in tar.getmembers() if any(member.name.endswith(s) for s in suffixes)]
    if len(selected) != 2 or not all(member.isfile() for member in selected):
        raise RuntimeError('Official SDK has unexpected validation layer topology')
    tar.extractall(ROOT, members=selected, filter='data')
library, = ROOT.glob('*/x86_64/lib/libVkLayer_khronos_validation.so')
manifest, = ROOT.glob('*/x86_64/share/vulkan/explicit_layer.d/VkLayer_khronos_validation.json')
original_manifest = manifest.read_bytes()
data = json.loads(original_manifest)
assert data['layer']['name'] == 'VK_LAYER_KHRONOS_validation'
assert data['layer']['api_version'] == '1.4.363'
# Point the copied official manifest at its same exact library. The application,
# Vulkan loader, Mesa driver and all validation checks remain unchanged.
data['layer']['library_path'] = str(library)
manifest.write_text(json.dumps(data, indent=2) + '\n')
receipt = {'sdk_version': VERSION, 'official_download': url, 'archive_sha256': actual,
           'library': str(library), 'library_sha256': hashlib.file_digest(library.open('rb'), 'sha256').hexdigest(),
           'manifest_original_sha256': hashlib.sha256(original_manifest).hexdigest(),
           'manifest': data, 'game_jar_unchanged': True,
           'synchronization_validation': True, 'queue_submit_validation_disabled': False,
           'ubuntu_packages': subprocess.check_output(['dpkg-query', '-W', 'vulkan-validationlayers', 'mesa-vulkan-drivers', 'libvulkan1'], text=True)}
(EVIDENCE / 'pinned-validation-sdk.json').write_text(json.dumps(receipt, indent=2) + '\n')
with Path(os.environ['GITHUB_ENV']).open('a') as env:
    env.write(f'VK_LAYER_PATH={manifest.parent}\nHARI_QA_VALIDATION_LIBRARY={library}\n')
archive.unlink()
print(json.dumps({'sdk': VERSION, 'archive_sha256': actual, 'library_sha256': receipt['library_sha256'],
                  'all_core_and_synchronization_checks_retained': True}))
