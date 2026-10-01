#!/usr/bin/env python3
"""Package exact tested binary, complete merged source and executable reproduction evidence."""
from __future__ import annotations
import argparse
import hashlib
import json
import zipfile
from pathlib import Path

GENERATED_ROOTS = {'build', 'forge/build', 'common/build', 'fabric/build', 'buildSrc/build', 'vulkan-backend/build'}
CACHE_NAMES = {'.git', '.gradle', '__pycache__'}

def source_file(relative: Path) -> bool:
    name = relative.as_posix()
    return not (set(relative.parts) & CACHE_NAMES) and not any(
        name == root or name.startswith(root + '/') for root in GENERATED_ROOTS
    ) and relative.suffix != '.pyc'

def digest(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()

def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument('--source', type=Path, required=True)
    parser.add_argument('--project', type=Path, required=True)
    parser.add_argument('--jar', type=Path, required=True)
    parser.add_argument('--evidence', type=Path, required=True)
    parser.add_argument('--checkpoint', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    source = args.source.resolve()
    if 'version=2.4.1-noxviola.1-vulkan-hybrid' not in (source/'gradle.properties').read_text():
        raise SystemExit('Wrong candidate source identity')
    checkpoint = json.loads(args.checkpoint.read_text())
    binary = args.jar.read_bytes()
    if checkpoint['jar_sha256'] != digest(binary) or checkpoint['jar_bytes'] != len(binary):
        raise SystemExit('Candidate JAR does not match accepted CI checkpoint')
    if not checkpoint['verification_complete']:
        raise SystemExit('Native acceptance is incomplete')
    entries: dict[str, bytes] = {args.jar.name: binary, 'CHECKPOINT.json': args.checkpoint.read_bytes()}
    for origin, prefix in [(source, 'source'), (args.project.resolve(), 'reconstruction'), (args.evidence.resolve(), 'evidence')]:
        for path in sorted(origin.rglob('*')):
            if path.is_file() and source_file(path.relative_to(origin)):
                entries[f'{prefix}/{path.relative_to(origin).as_posix()}'] = path.read_bytes()
    # Prevent recurrence of the old broad "build in path.parts" source filter.
    for tail in ['TaskDispatcher.java', 'task/BuildTask.java', 'task/SortTransparencyTask.java', 'thread/BuilderResources.java']:
        expected = 'source/forge/src/main/java/net/vulkanmod/render/chunk/build/' + tail
        if expected not in entries:
            raise SystemExit('Missing legitimate source package: ' + expected)
    entries['README.txt'] = (
        'HariMultiThread Ultimate 2.4.1 Vulkan hybrid — Minecraft 1.20.1 / Forge 47.4.23 / Java 17.\n'
        'Install the included JAR in mods, replacing the previous Hari JAR. The Vulkan renderer is merged into this mod.\n'
        'Do not also install a standalone VulkanMod JAR; this release already includes that renderer.\n'
        'C2ME is optional, not bundled. Native compatibility proof covers c2meforge-0.2.0-forge.9.6-all.jar (CurseForge file 8929972).\n'
        'Keep your current graphics and gameplay settings. Fabulous and conflicting renderers retain the existing OpenGL compatibility route.\n'
        'Source: source/ is the complete merged source, including Java packages named build. reconstruction/ contains the versioned overlay recipe.\n'
        'Evidence: exact packaged Forge clients and servers, C2ME save/unload/reload/restart, failure surfacing, reproducible build and CPU component benchmark.\n'
        'Identical CI binary copies are represented by the root JAR and BINARY-REFERENCES.json. Historical failures in diagnostics-repaired/ are retained for provenance and are not current acceptance.\n'
        'Native tests used Linux Mesa software drivers; no Windows/RTX 4090 FPS guarantee is made.\n'
        'See CHECKPOINT.json and reconstruction/VULKAN-PERFORMANCE-2.4.1.md for pinned provenance and acceptance.\n'
    ).encode()
    entries['SHA256SUMS.txt'] = ''.join(f'{digest(data)}  {name}\n' for name, data in sorted(entries.items())).encode()
    args.output.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(args.output, 'w', zipfile.ZIP_DEFLATED, compresslevel=9) as archive:
        for name, data in sorted(entries.items()):
            info = zipfile.ZipInfo(name, (2026, 10, 1, 0, 0, 0))
            info.compress_type = zipfile.ZIP_DEFLATED
            info.external_attr = 0o100644 << 16
            if name.endswith('/gradlew') or name.endswith('/run.sh'):
                info.external_attr = 0o100755 << 16
            archive.writestr(info, data, compresslevel=9)
    with zipfile.ZipFile(args.output) as archive:
        if archive.testzip() is not None:
            raise SystemExit('Archive CRC failure')
        for line in archive.read('SHA256SUMS.txt').decode().splitlines():
            sha, name = line.split('  ', 1)
            if digest(archive.read(name)) != sha:
                raise SystemExit('Manifest mismatch: ' + name)
    print(json.dumps({'file': str(args.output.resolve()), 'bytes': args.output.stat().st_size,
                      'sha256': digest(args.output.read_bytes()), 'manifest_entries': len(entries)-1,
                      'complete_source_files': sum(x.startswith('source/') for x in entries)}, indent=2))

if __name__ == '__main__':
    main()
