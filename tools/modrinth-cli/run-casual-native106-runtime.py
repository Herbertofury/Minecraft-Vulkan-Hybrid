"""Only the marked cumulative clone; require native, select106, preserve every original input."""
from pathlib import Path
import argparse,hashlib,json,os,psutil,shutil,sqlite3,subprocess,sys,tempfile,time,zipfile
W=None;C=Path('C:/Users/Owner/Desktop/Minecraft Vulkan Hybrid/benchmarks/cumulative')
ap=argparse.ArgumentParser();ap.add_argument('--workspace',type=Path,required=True);ap.add_argument('--pilot',type=int,required=True);a=ap.parse_args();W=a.workspace.resolve();assert 1<=a.pilot<=5
row=json.loads((C/'instance.json').read_text(encoding='utf8'));root=Path(row['path'])
marker={'task':'mvh-cumulative-20261005','id':'local:b6985be9-ded6-4a96-85cd-5a2548b1d900','source':'local:2c68d7c5-8fae-4984-ad3e-c76db50b66c3'}
db=Path('C:/Users/Owner/AppData/Roaming/ModrinthApp/app.db');prefix=f'024-casual-native106-pilot{a.pilot}-20261006';R=C/'diagnostics'/prefix;assert not R.exists();R.mkdir()
candidate=Path('C:/Users/Owner/AppData/Roaming/ModrinthApp/profiles/noxviola-candidate')
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def active(p):return {q.name:sha(q) for q in (p/'mods').glob('*.jar')}
def processes():
    with sqlite3.connect('file:'+db.as_posix()+'?mode=ro',uri=True) as q:return q.execute('select pid,start_time from processes where instance_id=?',(row['id'],)).fetchall()
def inactive():
    assert json.loads((root/'MVH-CUMULATIVE-IDENTITY.json').read_text(encoding='utf8'))==marker
    assert all(not psutil.pid_exists(pid) for pid,_ in processes()),'Owned or reused PID is still active'
def disable(name):
    inactive();p=root/'mods'/name;q=p.with_name(p.name+'.disabled');assert p.is_file() and not q.exists();p.rename(q)
def enable(name):
    inactive();p=root/'mods'/name;q=p.with_name(p.name+'.disabled');assert not p.exists() and q.is_file();q.rename(p)
def save(name,data):
    (R/name).write_text(json.dumps(data,indent=2)+'\n',encoding='utf8')
inactive();before=active(root);assert before==json.loads((C/'diagnostics/flywheel-native-ponder-v16-20261005/RESTORED.json').read_text(encoding='utf8'))['active_mods']
candidate_before=active(candidate);assert len(candidate_before)==327
hari=W/'hari-native-required23-build/forge/build/libs/harimt-forge-1.20.1-2.4.11-noxviola.23-dimensions-vulkan-hybrid-all.jar'
assert sha(hari)=='551d6047f3d7638a964050ca7f00c6e4eeb56a2c432b43f452f9ded68229e3a9'
controller=W/'noxviola-control/build/libs/mvh-noxviola-control-1.0.42.jar'
casual=W/'inventory/casualswing-native-20261006/casualswing-0.1.0-1.20.1-Forge-MVH-Native106-v3.jar'
assert sha(casual)=='70f580131b86ea87682bea609f22ab2409055b14ebe6fe6434819eadfa0c8c7e'
create=W/'inventory/noxviola/repairs/create-1.20.1-6.0.8-mvh-native-engine-17-pilot13.jar';assert sha(create)=='cc2ddeefa78e5eb999f2643277b4b639851b260d24171ff7fe886d15dba88b77'
testcreate=W/'inventory/casualswing-native-20261006/create-native14-newer-library-test-only.jar'
entry='META-INF/jarjar/flywheel-forge-1.20.1-1.0.5.jar'
if not testcreate.exists():
    with zipfile.ZipFile(create) as old:
        metadata=json.loads(old.read('META-INF/jarjar/metadata.json'));removed=[q for q in metadata['jars'] if q['path']==entry];assert len(removed)==1
        metadata['jars']=[q for q in metadata['jars'] if q['path']!=entry]
        with zipfile.ZipFile(testcreate,'w') as new:
            for info in old.infolist():
                if info.filename!=entry:new.writestr(info,(json.dumps(metadata,indent=2)+'\n').encode() if info.filename=='META-INF/jarjar/metadata.json' else old.read(info))
    with zipfile.ZipFile(create) as old,zipfile.ZipFile(testcreate) as new:
        assert set(old.namelist())-set(new.namelist())=={entry}
        assert [n for n in new.namelist() if new.read(n)!=old.read(n)]==['META-INF/jarjar/metadata.json']
    testcreate.with_suffix('.manifest.json').write_text(json.dumps({'original_native_create_sha256':sha(create),'test_only_sha256':sha(testcreate),'removed_duplicate_library':entry,'all_original_create_gameplay_assets_and_other_bundles_exact':True,'scope':'Disposable runtime selection fixture only. Original Create dependencies stay; CasualSwing supplies actual native1.0.6. Not installed in full candidate.'},indent=2)+'\n',encoding='utf8')
else:assert sha(testcreate)==json.loads(testcreate.with_suffix('.manifest.json').read_text(encoding='utf8'))['test_only_sha256']
files=[hari,controller,casual,testcreate]
for p in files:
    with zipfile.ZipFile(p) as z:assert z.testzip() is None and json.loads(z.read('pack.mcmeta'))['pack']['pack_format']==15
    dst=root/'mods'/(p.name+'.disabled');assert not (root/'mods'/p.name).exists()
    if dst.exists():assert sha(dst)==sha(p)
    else:shutil.copy2(p,dst);assert sha(dst)==sha(p)
old=[n for n in before if 'noxviola.13-dimensions' in n or n in ['mvh-noxviola-control-1.0.16.jar','create-1.20.1-6.0.8.jar','modernfix-forge-5.27.83+mc1.20.1.jar','ferritecore-6.0.1-forge.jar','mvh-grass-distance-compat-1.0.3.jar']]
assert len(old)==6
config=root/'config/harimt-native-required.properties';config_before=config.read_bytes() if config.exists() else None
options=root/'options.txt';options_before=options.read_bytes()
save('INPUTS.json',{'owned':marker,'before':before,'full_candidate_before':candidate_before,'installed':{p.name:sha(p) for p in files},'disabled_for_scoped_test':old,'native_required':True,'opengl_fallback_allowed':False,'optimizers_added':False,'scope':'Original Create and CasualSwing gameplay in a disposable native-only subset. No fullpack or FPS acceptance; all other workloads left running.'})
try:
    for n in old:disable(n)
    for p in files:enable(p.name)
    config.write_bytes(b'requireNative=true\n')
    expected={n:h for n,h in before.items() if n not in old};expected.update({p.name:sha(p) for p in files});assert active(root)==expected
    # Exact installed production gate, verified copies of all actual active jars, no mod class loading.
    with tempfile.TemporaryDirectory(prefix='mvh-cumulative-native-preflight-') as t:
        shadow=Path(t);(shadow/'mods').mkdir();(shadow/'config').mkdir()
        (shadow/'MVH-NOXVIOLA-IDENTITY.json').write_text(json.dumps({'task':'mvh-noxviola-20261004','source':'Noxviola','variant':'candidate','isolated':True}),encoding='utf8')
        shutil.copy2(config,shadow/'config'/config.name)
        for p in (root/'mods').glob('*.jar'):shutil.copy2(p,shadow/'mods'/p.name)
        assert active(shadow)==expected and active(root)==expected
        subprocess.run([sys.executable,str(W/'destination/tools/native-required-preflight.py'),str(shadow),'--hari-sha256',sha(hari),'--report',str(R/'PREFLIGHT.json')],check=True,cwd=W)
    assert active(root)==expected and config.read_bytes()==b'requireNative=true\n'
    save('ACTIVE.json',expected)
    subprocess.run([sys.executable,str(W/'run-cumulative-benchmark.py'),'--stage',prefix,'--create-native'],cwd=W,check=True)
finally:
    inactive()
    for p in files:
        if (root/'mods'/p.name).exists():disable(p.name)
    for n in old:
        if not (root/'mods'/n).exists():enable(n)
    assert active(root)==before and active(candidate)==candidate_before
    options.write_bytes(options_before)
    if config_before is None:shutil.move(str(config),str(R/'native-required-test-config.properties'))
    else:config.write_bytes(config_before)
    for name in ['mvh-create-native-request.json','mvh-create-native-smoke.json','mvh-create-framebuffer-diag-stage.json']:
        p=root/name
        if p.exists():assert not (R/name).exists();shutil.move(str(p),str(R/name))
    save('RESTORED.json',{'owned_inactive':True,'active_mods':before,'all_original_active_hashes_equal':True,'full_candidate_all_327_active_hashes_equal':active(candidate)==candidate_before,'native_requirement_original_config_restored':True,'options_restored':options.read_bytes()==options_before,'personal_profile_untouched':True})
    print('NATIVE106_RUNTIME_TEST_PROFILE_AND_FULL_CANDIDATE_PRESERVED',flush=True)
