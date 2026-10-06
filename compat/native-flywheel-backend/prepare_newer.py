"""Rebuild the retained stable Engine against checksum-pinned newer Flywheel sources."""
from pathlib import Path
import argparse, hashlib, io, json, subprocess, zipfile

H = Path(__file__).resolve().parent
R = H.parents[1]
STABLE_COMMIT = 'e873da9c5b2baafd5a28ebcb67d26dd5646bbc7b'
SOURCES_SHA = '9f45eeb4ba8608eec6ab13994a06a755eedb2753cf4fd078de5f371ed38bb6ca'
PREFIX = 'compat/native-flywheel-backend/'

def prepare(sources, output):
    assert not output.exists(), 'Use a fresh output directory'
    raw = sources.read_bytes()
    assert hashlib.sha256(raw).hexdigest() == SOURCES_SHA, 'Unexpected official reference sources'
    archive = subprocess.run(['git','-C',str(R),'archive','--format=zip',STABLE_COMMIT,PREFIX],
                             check=True,capture_output=True).stdout
    with zipfile.ZipFile(io.BytesIO(archive)) as z:
        for name in z.namelist():
            if not name.startswith(PREFIX):
                assert name.endswith('/') and PREFIX.startswith(name)
                continue
            rel = Path(name).relative_to(PREFIX)
            assert not rel.is_absolute() and '..' not in rel.parts
            if name.endswith('/') or not rel.parts:
                continue
            target = output/rel; target.parent.mkdir(parents=True,exist_ok=True)
            target.write_bytes(z.read(name))
    base = 'dev/engine_room/flywheel/impl/'
    with zipfile.ZipFile(io.BytesIO(raw)) as z:
        manager = z.read(base+'visualization/VisualizationManagerImpl.java').decode()
        debug = z.read(base+'FlwDebugInfo.java').decode()
    old = 'import dev.engine_room.flywheel.backend.engine.EngineImpl;'
    assert manager.count(old)==1 and manager.count('public EngineImpl getEngineImpl()')==1
    assert manager.count('engine instanceof EngineImpl engineImpl')==1
    manager = manager.replace(old,'import mvhflywheelbackend.NativeEngine;')
    manager = manager.replace('public EngineImpl getEngineImpl()','public NativeEngine getEngineImpl()')
    manager = manager.replace('engine instanceof EngineImpl engineImpl','engine instanceof NativeEngine engineImpl')
    old = 'import dev.engine_room.flywheel.backend.gl.GlCompat;'
    assert debug.count(old)==1
    debug = debug.replace(old,'import net.vulkanmod.vulkan.device.DeviceManager;')
    old = '''\t\tappendLine(out, "Environments: ").append(engineImpl.environmentStorage().arena.occupancy())
\t\t\t\t.append(" / ")
\t\t\t\t.append(engineImpl.environmentStorage().arena.capacity());'''
    assert debug.count(old)==1
    debug = debug.replace(old,'\t\tappendLine(out, "Native Instancers: ").append(engineImpl.nativeInstancerCount());')
    start = debug.index('\tprivate static void addOpenGLDebugInfo(StringBuilder out) {')
    end = debug.index('\n\tpublic static void addDebugInfo(',start)
    debug = debug[:start]+'''\tprivate static void addOpenGLDebugInfo(StringBuilder out) {
\t\tappendHeader(out, "Vulkan");
\t\tappendLine(out, "Device: ").append(DeviceManager.deviceProperties.deviceNameString());
\t\tappendLine(out, "API version: ").append(DeviceManager.deviceProperties.apiVersion());
\t\tappendLine(out, "Native model draws: ").append(mvhflywheelbackend.NativeEngine.MODEL_DRAWS.get());
\t}
''' + debug[end:]
    (output/'src/main/java'/base/'visualization/VisualizationManagerImpl.java').write_text(manager,encoding='utf8')
    (output/'src/main/java'/base/'FlwDebugInfo.java').write_text(debug,encoding='utf8')
    manifest = {'stable_native_source_commit':STABLE_COMMIT,'official_newer_sources_sha256':SOURCES_SHA,
                'newer_frontend_changes':'Only native engine debug type/counters. Original visualization/lifecycle flow retained.',
                'unmeasured_small15_optimization_included':False,
                'sources':{str(p.relative_to(output)).replace('\\','/'):hashlib.sha256(p.read_bytes()).hexdigest()
                           for p in output.rglob('*.java')}}
    (output/'NEWER-NATIVE-SOURCE-MANIFEST.json').write_text(json.dumps(manifest,indent=2)+'\n')
    print('PINNED_NEWER_NATIVE_BUILD_PREPARED',len(manifest['sources']))

if __name__=='__main__':
    ap=argparse.ArgumentParser();ap.add_argument('official_sources',type=Path);ap.add_argument('output',type=Path)
    a=ap.parse_args();prepare(a.official_sources,a.output)
