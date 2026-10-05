"""Update the existing feature mod recoverably; restore the unresolved Create trial."""
from pathlib import Path
import hashlib,json,psutil,sqlite3,subprocess,sys
W=Path(__file__).resolve().parent;C=Path('C:/Users/Owner/Desktop/Minecraft Vulkan Hybrid/benchmarks/cumulative')
r=json.loads((C/'instance.json').read_text());root=Path(r['path']);cli=C.parent/'tools/mvh_pack_cli.exe'
assert r['id']=='local:b6985be9-ded6-4a96-85cd-5a2548b1d900'
assert json.loads((root/'MVH-CUMULATIVE-IDENTITY.json').read_text())=={'task':'mvh-cumulative-20261005','id':r['id'],'source':'local:2c68d7c5-8fae-4984-ad3e-c76db50b66c3'}
def inactive():
    with sqlite3.connect('file:C:/Users/Owner/AppData/Roaming/ModrinthApp/app.db?mode=ro',uri=True) as db:
        assert all(not psutil.pid_exists(x[0]) for x in db.execute('select pid from processes where instance_id=?',(r['id'],)))
def change(stage,add=None,disable=None,enable=None):
    inactive();p=C/'stage-inputs'/(stage+'.json');assert not p.exists()
    p.write_text(json.dumps({'stage':stage,'add':add or [],'disable':disable or [],'enable':enable or []},indent=2)+'\n')
    with (C/(stage+'-private.log')).open('w') as log:subprocess.run([str(cli),'add-cumulative',str(p)],cwd=W,stdout=log,stderr=log,check=True,timeout=120)
latest=W/'inventory/noxviola/upstream/fieldguide-1.20.4+1.20.1-forge.jar';manifest=json.loads((latest.parent/'fieldguide-latest-manifest.json').read_text())
assert hashlib.sha256(latest.read_bytes()).hexdigest()==manifest['sha256']=='1613b3ad057d2d0bc1955e7d35fdcaac9583fc780840f5c3c74ccddc232f6825'
official=next(f for f in manifest['version']['files'] if f['primary']);assert hashlib.sha512(latest.read_bytes()).hexdigest()==official['hashes']['sha512']
create=root/'mods/create-1.20.1-6.0.8.jar';assert hashlib.sha256(create.read_bytes()).hexdigest()=='6fbb910c367dbce8e4fc7e5bf64b6edd4de980906ed00af8e47e4af843c0d9b0'
records=json.loads((C/'progress.json').read_text());record=next(x for x in records if x['stage']=='017-create');assert record['status']=='RENDERER_TRANSITION_REQUIRES_COMPATIBILITY_WORK'
change('016-fieldguide-latest-install',add=[{'path':str(latest),'sha256':manifest['sha256']}],disable=['mods/'+create.name,'mods/fieldguide-forge-1.20.1-1.17.0.jar'])
try:
    subprocess.run([sys.executable,'-X','utf8',str(W/'run-cumulative-benchmark.py'),'--stage','016-fieldguide-latest-native','--repeat','3'],cwd=W,check=True)
    dirs=[p for p in sorted((C/'runs/016-fieldguide-latest-native').glob('*')) if (p/'DONE.json').exists()];assert len(dirs)==3
    assert all(json.loads((p/'fps.json').read_text())['api_renderer']=='VULKAN' for p in dirs)
    errors={p.name:[line for line in (p/'minecraft-sanitized.log').read_text(encoding='utf8').splitlines() if 'Parsing error loading recipe fieldguide:field_guide' in line or "Unknown item 'fieldguide:field_guide'" in line] for p in dirs}
    data={'version':manifest['version']['version_number'],'official_sha512_verified':True,'sha256':manifest['sha256'],'native_runs':len(dirs),'original_recipe_error_absent':all(not e for e in errors.values()),'complete_guide_feature_parity_accepted':False,'original_private_files_preserved':True,'scope':'Latest official version alone; no startup addon installed. Create temporarily disabled and restored after these scoped native tests.'}
    (C/'fieldguide-latest-native-summary.json').write_text(json.dumps(data,indent=2)+'\n')
    if data['original_recipe_error_absent']:
        records=json.loads((C/'progress.json').read_text());record=next(x for x in records if x['stage']=='016-fieldguide');record.update({'official_updated_version':data['version'],'official_updated_sha256':data['sha256'],'latest_native_capability':data,'effective_trial_dirs':['016-fieldguide-latest-native/'+p.name for p in dirs]});(C/'progress.json').write_text(json.dumps(records,indent=2)+'\n')
    print('LATEST_FIELDGUIDE_NATIVE_RESULTS',json.dumps(data),flush=True)
finally:
    change('017-create-restored-after-fieldguide-latest',enable=['mods/'+create.name])
    assert hashlib.sha256(create.read_bytes()).hexdigest()=='6fbb910c367dbce8e4fc7e5bf64b6edd4de980906ed00af8e47e4af843c0d9b0'
    print('UNRESOLVED_CREATE_RESTORED_ORIGINAL_HASH',flush=True)
