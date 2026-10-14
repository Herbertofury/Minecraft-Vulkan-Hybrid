"""Preserve native compatibility evidence; do not retain unproved performance mods."""
from pathlib import Path
import hashlib,json,psutil,sqlite3,statistics,subprocess,sys
W=Path(__file__).resolve().parent
C=Path('C:/Users/Owner/Desktop/Minecraft Vulkan Hybrid/benchmarks/cumulative')
r=json.loads((C/'instance.json').read_text()); root=Path(r['path'])
assert r['id']=='local:b6985be9-ded6-4a96-85cd-5a2548b1d900'
assert json.loads((root/'MVH-CUMULATIVE-IDENTITY.json').read_text())=={'task':'mvh-cumulative-20261005','id':r['id'],'source':'local:2c68d7c5-8fae-4984-ad3e-c76db50b66c3'}
with sqlite3.connect('file:C:/Users/Owner/AppData/Roaming/ModrinthApp/app.db?mode=ro',uri=True) as db:
    assert all(not psutil.pid_exists(x[0]) for x in db.execute('select pid from processes where instance_id=?',(r['id'],)))
captures=sorted((C/'runs/015-ixeris-native-v12-capability').glob('*/fps.json'))
frames=[json.loads(p.read_text()) for p in captures]
assert len(frames)==3 and all(f['api_renderer']=='VULKAN' for f in frames)
smoke=json.loads((C/'runs/015-ixeris-native-v12-playability/01-cumulative/GRASS-SMOKE.json').read_text())
assert smoke['passed'] and smoke['api_renderer']=='VULKAN'
records=json.loads((C/'progress.json').read_text()); record=next(x for x in records if x['stage']=='015-ixeris')
assert record['status']=='RENDERER_TRANSITION_REQUIRES_COMPATIBILITY_WORK'
name=record['file']; source=root/'mods'/name
assert hashlib.sha256(source.read_bytes()).hexdigest()=='6cc7e1bfad6a2e82983a41535accf919a1027ca7087f1e6fcc955df22f7d91e1'
stage='015-ixeris-unproven-purpose-disable'; p=C/'stage-inputs'/(stage+'.json'); assert not p.exists()
p.write_text(json.dumps({'stage':stage,'add':[],'enable':[],'disable':['mods/'+name]},indent=2)+'\n')
with (C/(stage+'-private.log')).open('w') as log:
    subprocess.run([str(C.parent/'tools/mvh_pack_cli.exe'),'add-cumulative',str(p)],cwd=W,stdout=log,stderr=log,check=True,timeout=120)
subprocess.run([sys.executable,'-X','utf8',str(W/'run-cumulative-benchmark.py'),'--stage','015-ixeris-removed-native-reference-v12','--repeat','3'],cwd=W,check=True)
effective=[f'015-ixeris-removed-native-reference-v12/{p.name}' for p in sorted((C/'runs/015-ixeris-removed-native-reference-v12').glob('*')) if (p/'DONE.json').exists()]
assert len(effective)==3
record.update({'status':'REJECTED_NATIVE_PERFORMANCE_MOD','effective_trial_dirs':effective,'native_compatibility_repaired':True,'native_capability_fps':[f['average_fps'] for f in frames],'native_capability_median_fps':statistics.median(f['average_fps'] for f in frames),'grass_playability_checks':len(smoke['checks']),'purpose_retention_decision':'INPUT_POLLING_BENEFIT_NOT_ESTABLISHED','decision_limits':'Three native compatibility captures are not a matched input-polling purpose comparison. One capture missed 2432 FPS. No causal performance regression is inferred. Disabled only in the owned continuation pending a demonstrated purpose benefit; original JAR and repair source retained.'})
(C/'progress.json').write_text(json.dumps(records,indent=2)+'\n')
receipt=W/'inventory/noxviola/ixeris-native-v12-build-manifest.json'; data=json.loads(receipt.read_text()); data.update({'native_game_accepted':True,'native_game_scope':'Three native original-grass subset capability captures and seven-step native playability; no full pack, shader, zero-hitch or Ixeris purpose-benefit acceptance.','full_pack_performance_promotion_accepted':False}); receipt.write_text(json.dumps(data,indent=2)+'\n')
print('IXERIS_NATIVE_COMPATIBILITY_REPAIRED_PURPOSE_RETENTION_DEFERRED',flush=True)
subprocess.run([sys.executable,'-X','utf8',str(W/'run-cumulative-stages.py'),'--start','16','--end','30'],cwd=W,check=True)
