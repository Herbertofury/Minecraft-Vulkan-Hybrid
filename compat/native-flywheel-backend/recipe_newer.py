"""Pin and port CasualSwing's newer nested Flywheel; preserve root gameplay/API/assets."""
from pathlib import Path
import argparse, copy, hashlib, io, json, re, zipfile
from recipe import BASE, references

SOURCE_SHA = 'cb76cc4af706de4d6387b555e36fbb755579a1248a395b7b4d1da29f9535af42'
FLYWHEEL_SHA = '4e3667f3ddf413425eaf85a2380fb65496cf8c7d80a738e279d295b6c02d8ee5'
ENTRY = 'META-INF/jarjar/flywheel-forge-1.20.1-1.0.6-beta-266.jar'
PONDER_ENTRY = 'META-INF/jarjar/Ponder-Forge-1.20.1-1.0.91.jar'
PONDER_ORIGINAL_SHA = '86e6b64372aba6d9c56f2c35725ea26d8febf2c75eed9950566e7f2849443b34'
PONDER_TESTED_NATIVE_SHA = 'a3239fb968d8c059eecd502c05ca2daf826a33f4e66977530e4ab6fc4e92f5c7'
H = Path(__file__).resolve().parent

def sha(raw):
    return hashlib.sha256(raw).hexdigest()

def native_library(raw, backend):
    assert sha(raw) == FLYWHEEL_SHA, 'Unexpected newer Flywheel input'
    with zipfile.ZipFile(io.BytesIO(raw)) as old, zipfile.ZipFile(backend) as port:
        replacements = {n: port.read(n) for n in port.namelist() if n.endswith('.class')}
        assert BASE + 'Backends.class' in replacements
        assert 'mvhflywheelbackend/NativeEngine.class' in replacements
        roots = ['BackendConfig','BackendDebugFlags','FlwBackend','FlwBackendXplat','FlwBackendXplatImpl',
                 'MaterialShaderIndices','Samplers','SkyLightSectionStorageExtension','gl/GlTextureUnit',
                 'compile/core/ShaderException','compile/LayoutInterpreter',
                 'compile/component/InstanceAssemblerComponent','compile/component/InstanceStructComponent',
                 'compile/component/SsboInstanceComponent','compile/component/UberShaderComponent',
                 'compile/component/StringSubstitutionComponent','engine/LightLut','engine/LightDataCollector',
                 'engine/AbstractArena','engine/CpuArena','engine/MaterialEncoder','engine/TextureBinder']
        prefixes = ['glsl/','engine/uniform/','mixin/','util/']
        def keep(n):
            if not n.startswith(BASE) or not n.endswith('.class'):
                return True
            p = n[len(BASE):]
            return (n in replacements or any(p.startswith(x) for x in prefixes)
                    or any(p == x+'.class' or p.startswith(x+'$') for x in roots))
        kept = {n: old.read(n) for n in old.namelist() if keep(n)}
        removed = sorted(set(old.namelist()) - set(kept))
        kept.update(replacements)
        config_name = 'flywheel.backend.mixins.json'
        config = json.loads(kept[config_name]); original_mixins = list(config['client'])
        for name in ['NativeBuildTaskMixin','NativeWorldReloadMixin']:
            assert BASE+'mixin/'+name+'.class' in replacements and name not in config['client']
            config['client'].append(name)
        assert config['client'][:len(original_mixins)] == original_mixins
        kept[config_name] = (json.dumps(config, indent=2)+'\n').encode()
        missing = {n: sorted(r for r in references(b) if r+'.class' not in kept)
                   for n,b in kept.items() if n.endswith('.class')}
        missing = {n: r for n,r in missing.items() if r}
        assert not missing, 'Removed a referenced Flywheel type: '+json.dumps(missing)
        metadata = json.loads(kept['pack.mcmeta']); metadata['pack']['pack_format'] = 15
        kept['pack.mcmeta'] = (json.dumps(metadata, indent=2)+'\n').encode()
        toml, count = re.subn(rb'(?m)^license\s*=\s*"MIT"\s*$',
                             b'license = "MIT (upstream); GPL-3.0-only (native backend)"\n',
                             kept['META-INF/mods.toml'])
        assert count == 1
        kept['META-INF/mods.toml'] = toml
        exact = []
        for n in old.namelist():
            if n.startswith(('dev/engine_room/flywheel/api/','dev/engine_room/flywheel/lib/',
                             'dev/engine_room/flywheel/impl/','assets/')) and n not in replacements:
                assert kept[n] == old.read(n), n
                exact.append(n)
        report = {'original_flywheel_sha256': FLYWHEEL_SHA, 'native_backend_sha256': sha(backend.read_bytes()),
                  'removed_legacy_renderer_classes': removed, 'changed_classes': sorted(replacements),
                  'exact_original_api_lib_frontend_assets': exact,
                  'newer_material_ambient_occlusion_api_and_encoder_preserved': True,
                  'newer_model_baking_fixes_preserved': True,
                  'resource_pack_format': 15, 'runtime_accepted': False,
                  'scope': 'Real stable native Engine compiled against original1.0.6-beta-266; replaces its internal GL backend. No native gate/contract/optimizer/settings change. Newer public API, model baking, assets and original visualization flow stay. Runtime feature parity and fullpack FPS remain separate acceptance.'}
        kept['META-INF/MVH-NATIVE-FLYWHEEL.json'] = (json.dumps(report,indent=2)+'\n').encode()
        kept['META-INF/MVH-NATIVE-FLYWHEEL-GPL.txt'] = (H/'LICENSE').read_bytes()
        kept['META-INF/MVH-NATIVE-FLYWHEEL-UPSTREAM-MIT.txt'] = (H/'FLYWHEEL-LICENSE-MIT.txt').read_bytes()
        buffer = io.BytesIO()
        with zipfile.ZipFile(buffer,'w',zipfile.ZIP_DEFLATED) as z:
            for n in sorted(kept):
                info = zipfile.ZipInfo(n,(2026,10,6,0,0,0)); info.compress_type = zipfile.ZIP_DEFLATED
                z.writestr(info,kept[n])
        return buffer.getvalue(), report

def build(original, backend, output, native_ponder=None):
    assert not output.exists() and not output.with_suffix('.manifest.json').exists()
    raw = original.read_bytes(); assert sha(raw) == SOURCE_SHA, 'Unexpected CasualSwing input'
    with zipfile.ZipFile(io.BytesIO(raw)) as outer:
        fixed, report = native_library(outer.read(ENTRY), backend)
        entries = {n: outer.read(n) for n in outer.namelist()}
        entries[ENTRY] = fixed
        changed_entries = {ENTRY,'pack.mcmeta'}
        if native_ponder is not None:
            assert sha(entries[PONDER_ENTRY]) == PONDER_ORIGINAL_SHA
            repaired_ponder = native_ponder.read_bytes()
            assert sha(repaired_ponder) == PONDER_TESTED_NATIVE_SHA, 'Use the exact previously GPU-tested component'
            with zipfile.ZipFile(io.BytesIO(entries[PONDER_ENTRY])) as old, zipfile.ZipFile(io.BytesIO(repaired_ponder)) as new:
                for n in old.namelist():
                    if n not in {'net/createmod/catnip/gui/element/StencilElement.class','pack.mcmeta'}:
                        assert new.read(n) == old.read(n), n
                assert json.loads(new.read('pack.mcmeta'))['pack']['pack_format'] == 15
            entries[PONDER_ENTRY] = repaired_ponder
            changed_entries.add(PONDER_ENTRY)
            report['ponder_stencil_component'] = {
                'original_sha256':PONDER_ORIGINAL_SHA,'native_sha256':PONDER_TESTED_NATIVE_SHA,
                'scope':'Exact original1.0.91 component with four literal stencil owner changes and format15; same component already accepted in original Ponder native GPU clipping readbacks. All remaining original Ponder bytes exact. Not new whole-CasualSwing runtime acceptance.'}
        # Preserve gameplay bytes and other bundled libraries. Only update the resource-version metadata.
        if 'pack.mcmeta' in entries:
            pack = json.loads(entries['pack.mcmeta']); pack['pack']['pack_format'] = 15
            entries['pack.mcmeta'] = (json.dumps(pack,indent=2)+'\n').encode()
        report.update({'source_sha256': SOURCE_SHA, 'native_flywheel_sha256': sha(fixed),
                       'nested_version_and_selection_metadata_unchanged': True,
                       'original_gameplay_classes_assets_and_unmodified_other_bundles_exact': True,
                       'explicitly_changed_original_nested_entries':sorted(changed_entries-{'pack.mcmeta'})})
        output.parent.mkdir(parents=True,exist_ok=True)
        with zipfile.ZipFile(output,'w',zipfile.ZIP_DEFLATED) as z:
            for info in outer.infolist():
                z.writestr(copy.copy(info),entries[info.filename])
            z.writestr('META-INF/MVH-NATIVE-FLYWHEEL.json',json.dumps(report,indent=2))
        with zipfile.ZipFile(output) as verify:
            assert verify.testzip() is None
            for n in outer.namelist():
                assert verify.read(n) == entries[n], n
                if n not in changed_entries:
                    assert verify.read(n) == outer.read(n), n
            assert verify.read('META-INF/jarjar/metadata.json') == outer.read('META-INF/jarjar/metadata.json')
    report['output_sha256'] = sha(output.read_bytes())
    output.with_suffix('.manifest.json').write_text(json.dumps(report,indent=2)+'\n')
    print('PRIVATE_CASUALSWING_NATIVE_BACKEND_BUILT', report['output_sha256'])

if __name__ == '__main__':
    ap=argparse.ArgumentParser();ap.add_argument('original',type=Path);ap.add_argument('backend',type=Path);ap.add_argument('output',type=Path)
    ap.add_argument('--native-ponder',type=Path)
    a=ap.parse_args();build(a.original,a.backend,a.output,a.native_ponder)
