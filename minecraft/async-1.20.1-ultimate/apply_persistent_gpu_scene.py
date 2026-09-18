#!/usr/bin/env python3
"""Apply Hari 2.3 persistent GPU scene metadata on certified 2.3 GPU terrain."""
from __future__ import annotations
from pathlib import Path
import base64, gzip, hashlib, io, sys, tarfile

if len(sys.argv) != 2:
    raise SystemExit('usage: apply_persistent_gpu_scene.py <merged-upstream-root>')
root = Path(sys.argv[1]).resolve()
here = Path(__file__).resolve().parent
parts_dir = here / 'persistent_gpu_scene_patch'
props = root / 'gradle.properties'
if not props.is_file() or 'version=2.3.0-noxviola.1' not in props.read_text(encoding='utf-8'):
    raise SystemExit('source drift: expected Hari 2.3.0-noxviola.1 GPU-terrain base')
renderer = root / 'forge/src/main/java/com/axalotl/async/forge/client/hari263/render/HariTerrainRenderer.java'
if not renderer.is_file() or 'Hari GPU terrain initialized' not in renderer.read_text(encoding='utf-8'):
    raise SystemExit('source drift: unexpected HariTerrainRenderer base')
meta = {}
for line in (parts_dir/'META.txt').read_text().splitlines():
    k,v=line.split('=',1); meta[k]=v
parts=sorted(parts_dir.glob('part*.b64'))
if len(parts) != int(meta['PARTS']):
    raise SystemExit('persistent scene patch chunks incomplete')
b64=''.join(p.read_text(encoding='ascii').strip() for p in parts).encode('ascii')
if hashlib.sha256(b64).hexdigest() != meta['B64_SHA256']:
    raise SystemExit('persistent scene patch base64 checksum mismatch')
gz=base64.b64decode(b64, validate=True)
if hashlib.sha256(gz).hexdigest() != meta['ARCHIVE_SHA256']:
    raise SystemExit('persistent scene patch archive checksum mismatch')
expected={}
for line in (parts_dir/'MANIFEST.sha256').read_text().splitlines():
    digest, rel=line.split('  ',1); expected[rel]=digest
with tarfile.open(fileobj=io.BytesIO(gzip.decompress(gz)), mode='r:') as tf:
    members=[m for m in tf.getmembers() if m.isfile()]
    if sorted(m.name for m in members) != sorted(expected):
        raise SystemExit('persistent scene patch inventory mismatch')
    for m in members:
        if m.name.startswith('/') or '..' in Path(m.name).parts:
            raise SystemExit(f'unsafe embedded path: {m.name}')
        data=tf.extractfile(m).read()
        if hashlib.sha256(data).hexdigest() != expected[m.name]:
            raise SystemExit(f'embedded file checksum mismatch: {m.name}')
        dst=root/m.name; dst.parent.mkdir(parents=True, exist_ok=True); dst.write_bytes(data)

common=root/'common/src/main/java/com/axalotl/async/common/config/AsyncConfig.java'
text=common.read_text(encoding='utf-8')
if 'enableGpuTerrainPersistentScene' not in text:
    old='    public static Map.Entry<String, Boolean> enableGpuTerrainRegionCache = new AbstractMap.SimpleEntry<String, Boolean>("enableGpuTerrainRegionCache", true);\n    public static Map.Entry<String, Integer> gpuTerrainCpuThreshold = new AbstractMap.SimpleEntry<String, Integer>("gpuTerrainCpuThreshold", 768);'
    new='    public static Map.Entry<String, Boolean> enableGpuTerrainRegionCache = new AbstractMap.SimpleEntry<String, Boolean>("enableGpuTerrainRegionCache", true);\n    public static Map.Entry<String, Boolean> enableGpuTerrainPersistentScene = new AbstractMap.SimpleEntry<String, Boolean>("enableGpuTerrainPersistentScene", true);\n    public static Map.Entry<String, Integer> gpuTerrainCpuThreshold = new AbstractMap.SimpleEntry<String, Integer>("gpuTerrainCpuThreshold", 768);'
    if old not in text: raise SystemExit('source drift: AsyncConfig GPU terrain anchor missing')
    common.write_text(text.replace(old,new,1),encoding='utf-8')

cfg=root/'forge/src/main/java/com/axalotl/async/forge/config/AsyncConfigForge.java'
text=cfg.read_text(encoding='utf-8')
if 'enableGpuTerrainPersistentSceneLocal' not in text:
    replacements=[
      ('        private static final ForgeConfigSpec.ConfigValue<Boolean> enableGpuTerrainRegionCacheLocal;\n        private static final ForgeConfigSpec.ConfigValue<Integer> gpuTerrainCpuThresholdLocal;',
       '        private static final ForgeConfigSpec.ConfigValue<Boolean> enableGpuTerrainRegionCacheLocal;\n        private static final ForgeConfigSpec.ConfigValue<Boolean> enableGpuTerrainPersistentSceneLocal;\n        private static final ForgeConfigSpec.ConfigValue<Integer> gpuTerrainCpuThresholdLocal;'),
      ('                enableGpuTerrainRegionCacheLocal = BUILDER.comment(\n                                "Cache unchanged per-region indirect draw commands. Does not cache geometry and is invalidated whenever Embeddium uploads/removes/resizes section meshes.")\n                                .define("enableGpuTerrainRegionCache", enableGpuTerrainRegionCache.getValue());\n\n                gpuTerrainCpuThresholdLocal = BUILDER.comment(',
       '                enableGpuTerrainRegionCacheLocal = BUILDER.comment(\n                                "Cache unchanged per-region indirect draw commands using pass-local Embeddium storage generations and exact visible-face signatures.")\n                                .define("enableGpuTerrainRegionCache", enableGpuTerrainRegionCache.getValue());\n\n                enableGpuTerrainPersistentSceneLocal = BUILDER.comment(\n                                "Keep immutable terrain draw-boundary metadata resident on the GPU and refresh it only when Embeddium changes that region/pass storage. " +\n                                "Visible-section and block-face masks remain sourced from Embeddium every frame, preserving visual behavior while removing per-frame mesh-metadata rebuilds.")\n                                .define("enableGpuTerrainPersistentScene", enableGpuTerrainPersistentScene.getValue());\n\n                gpuTerrainCpuThresholdLocal = BUILDER.comment('),
      ('                enableGpuTerrainRegionCache.setValue(enableGpuTerrainRegionCacheLocal.get());\n                gpuTerrainCpuThreshold.setValue(gpuTerrainCpuThresholdLocal.get());',
       '                enableGpuTerrainRegionCache.setValue(enableGpuTerrainRegionCacheLocal.get());\n                enableGpuTerrainPersistentScene.setValue(enableGpuTerrainPersistentSceneLocal.get());\n                gpuTerrainCpuThreshold.setValue(gpuTerrainCpuThresholdLocal.get());'),
      ('                enableGpuTerrainRegionCacheLocal.set(enableGpuTerrainRegionCache.getValue());\n                gpuTerrainCpuThresholdLocal.set(gpuTerrainCpuThreshold.getValue());',
       '                enableGpuTerrainRegionCacheLocal.set(enableGpuTerrainRegionCache.getValue());\n                enableGpuTerrainPersistentSceneLocal.set(enableGpuTerrainPersistentScene.getValue());\n                gpuTerrainCpuThresholdLocal.set(gpuTerrainCpuThreshold.getValue());'),
    ]
    for old,new in replacements:
        if old not in text: raise SystemExit('source drift: AsyncConfigForge persistent-scene anchor missing')
        text=text.replace(old,new,1)
    cfg.write_text(text,encoding='utf-8')

assert 'enableGpuTerrainPersistentScene' in common.read_text(encoding='utf-8')
assert 'enableGpuTerrainPersistentSceneLocal' in cfg.read_text(encoding='utf-8')
assert 'useBlockFaceCulling' in renderer.read_text(encoding='utf-8')
assert 'preparePersistent' in renderer.read_text(encoding='utf-8')
assert 'SectionStorageGenerationAccess' in (root/'forge/src/main/java/com/axalotl/async/forge/mixin/client/embeddium/MixinSectionRenderDataStorage.java').read_text(encoding='utf-8')
assert 'BYTES = 64' in (root/'forge/src/main/java/com/axalotl/async/forge/client/hari263/meshlet/MeshletHeader.java').read_text(encoding='utf-8')
assert 'uUseSectionMasks' in (root/'forge/src/main/resources/assets/harimt/shaders/hari_terrain_indirect.glsl').read_text(encoding='utf-8')
print('Hari 2.3 persistent GPU scene layer applied successfully')
