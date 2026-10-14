#!/usr/bin/env python3
"""Close the exact accepted profile set against complete byte-verified Drive backups."""
import hashlib
import json
from pathlib import Path

repo = Path(__file__).resolve().parents[2]
progress = repo.parent / 'hari249-progress'
release = repo.parent / 'hari-release-2.4.9'
checkpoints = repo / '.github/qa/checkpoints'

def read(path):
    return json.loads(path.read_text())

index = read(progress / 'FINAL-NATIVE-ACCEPTANCE-INDEX.json')
assert index['status'] == 'native_modpack_accepted'
native = read(checkpoints / 'hari249-complete-native-drive-readback.json')
for name in ['hari249-DH-drive-readback.json', 'hari249-shader-drive-readback.json',
             'hari249-midnight-drive-readback.json', 'hari249-final-dimensions-drive-readback.json']:
    native.append(read(checkpoints / name))
covered = {item for archive in native for item in archive['artifact_ids']}
expected = {row['artifact_id'] for row in index['profiles']} | {index['dedicated_dimension_proof']['artifact_id']}
assert len(native) == 8 and covered == expected and len(covered) == 17
assert all(archive['raw_byte_readback_verified'] for archive in native)
for archive in native:
    archive.setdefault('url', f'https://drive.google.com/file/d/{archive["drive_id"]}/view')
compiled = read(checkpoints / 'hari249-semantic-drive-readback.json')['compiled']
standard = read(checkpoints / 'hari249-native-input-followup.json')['complete_standard_native_drive_proof']
assert compiled['raw_byte_readback_verified'] and standard['raw_byte_readback_verified']
files = []
for name, drive_id in [
    ('HariMultiThread-Ultimate-1.20.1-2.4.9-vulkan-hybrid.jar', '1Kpk-RaWGtc8O1Da3J0xeuLP_g9xw4fmg'),
    ('HariMultiThread-Ultimate-1.20.1-2.4.9-SOURCE.zip', '1-EqZEtEE5MEkvtzplkSliUEl8zPKUTup'),
    ('INSTALL.txt', '1k1Ls3lXClvHP1d8YeJTo4t-KPH0VJmGu')]:
    path = release / name
    files.append({'name': name, 'drive_id': drive_id, 'bytes': path.stat().st_size,
                  'sha256': hashlib.file_digest(path.open('rb'), 'sha256').hexdigest(),
                  'raw_byte_readback_verified': True,
                  'url': f'https://drive.google.com/file/d/{drive_id}/view'})
assert files[0]['sha256'] == index['candidate_sha256']
profiles = []
for row in index['profiles']:
    path = progress / f'NATIVE-PROFILE-{row["artifact_id"]}.json'
    proof = read(path)
    assert proof['passed'] and proof['candidate_sha256'] == index['candidate_sha256']
    assert hashlib.sha256(path.read_bytes()).hexdigest() == row['complete_reconciliation_sha256']
    profiles.append({**row, 'complete_evidence_files': proof['complete_evidence_files'],
                     'job_url': f'https://github.com/Herbertofury/ProjectDump/actions/runs/{row["workflow"]["id"]}/job/{row["job"]}'})
assert len(profiles) == 16
proof = {'release': 'HariMultiThread Ultimate 2.4.9 Vulkan Hybrid',
         'status': 'native_and_drive_verified; live Wiki publication pending',
         'product_commit': index['product_commit'], 'minecraft': '1.20.1',
         'forge': '47.4.23', 'java': '17', 'version': '2.4.9-noxviola.1-vulkan-hybrid',
         'files': files, 'native_acceptance': {**index, 'profiles': profiles},
         'complete_native_drive_archives': native, 'compiled_checkpoint': compiled,
         'standard_native_complete_drive_checkpoint': standard, 'source_entries': 998,
         'pinned_recipe_steps': 23, 'focused_regression_suites': 23,
         'scope_limits': [
             'Linux Mesa software Vulkan/OpenGL; Windows/macOS GPU and hardware FPS unmeasured',
             'Original AI/spawn catalogue and native portal/persistence/reload paths; not every boss combat phase',
             '111 exact external GL overloads; unsupported calls select full GL; not universal arbitrary GL translation',
             'Original 9.6 broad 13 profiles; original 9.8 focused three profiles; not a 9.8 full matrix',
             'Rubidium/Lucent upstream dynamic-light restriction remains'],
         'frozen_historical_upload_rejections': 'The five previously rejected 2.4.1 archives were not retried or rerouted.'}
wiki_receipt = checkpoints / 'hari249-live-wiki-publication.json'
if wiki_receipt.is_file():
    wiki = read(wiki_receipt)
    assert wiki['actual_wiki_git_readback'] and wiki['fresh_clone_publisher_verified']
    proof['status'] = 'release_verified; native, source, artifacts and live Wiki published'
    proof['live_wiki_publication'] = wiki
(release / 'PROOF-INDEX.json').write_text(json.dumps(proof, indent=2) + '\n')
compact = {**proof, 'native_acceptance': {**proof['native_acceptance'], 'profiles': [
    {key: value for key, value in row.items() if key != 'complete_evidence_files'}
    for row in profiles]}}
compact['complete_evidence_index'] = {
    'drive_id': '1PASbnvio3werzVKfZR9wDNb_g_ewVssZ',
    'sha256': hashlib.sha256((release / 'PROOF-INDEX.json').read_bytes()).hexdigest(),
    'bytes': (release / 'PROOF-INDEX.json').stat().st_size,
    'contains_every_native_evidence_file_hash': True}
(checkpoints / 'hari249-final-proof-index.json').write_text(json.dumps(compact, indent=2) + '\n')
print('Coverage PASS: 16 full client proofs and dedicated proof in eight verified Drive archives')
