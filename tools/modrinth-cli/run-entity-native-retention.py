"""Bounded native UI smoke then six sole-mod captures; promotion requires measured purpose benefit."""
from pathlib import Path
import csv,hashlib,json,psutil,sqlite3,statistics,subprocess,sys
W=Path(__file__).resolve().parent;C=Path('C:/Users/Owner/Desktop/Minecraft Vulkan Hybrid/benchmarks/cumulative');row=json.loads((C/'instance.json').read_text());root=Path(row['path']);cli=C.parent/'tools/mvh_pack_cli.exe'
assert row['id']=='local:b6985be9-ded6-4a96-85cd-5a2548b1d900'
assert json.loads((root/'MVH-CUMULATIVE-IDENTITY.json').read_text())=={'task':'mvh-cumulative-20261005','id':row['id'],'source':'local:2c68d7c5-8fae-4984-ad3e-c76db50b66c3'}
def sha(p):return hashlib.file_digest(p.open('rb'),'sha256').hexdigest()
def inactive():
    with sqlite3.connect('file:C:/Users/Owner/AppData/Roaming/ModrinthApp/app.db?mode=ro',uri=True) as db:assert all(not psutil.pid_exists(r[0]) for r in db.execute('select pid from processes where instance_id=?',(row['id'],)))
def change(stage,add=[],enable=[],disable=[]):
    inactive();p=C/'stage-inputs'/(stage+'.json');assert not p.exists();p.write_text(json.dumps({'stage':stage,'add':[{'path':str(f),'sha256':sha(f)} for f in add],'enable':['mods/'+n for n in enable],'disable':['mods/'+n for n in disable]},indent=2)+'\n')
    with (C/(stage+'-install-private.log')).open('w') as log:subprocess.run([str(cli),'add-cumulative',str(p)],cwd=W,stdout=log,stderr=log,check=True,timeout=120)
source=W/'inventory/noxviola/repairs/entityculling-forge-1.11.2-mc1.20.1-MVH-NativeScissor-v1.jar';name=source.name;pin=sha(source);assert pin=='4d8e4bc63bc5520fc7a9c9631bf0cdb2199c9b93e65883b5a0cc62e46bacd60e'
assert sha(root/'mods/harimt-forge-1.20.1-2.4.11-noxviola.11-dimensions-vulkan-hybrid-all.jar')=='8333a3fc7008b2e126e8a409bf1b9ab8530d2f86df130bd38c2800d7b267c665'
inactive();change('012-entity-native-scissor-install-v1',[source],disable=['entityculling-forge-1.11.2-mc1.20.1.jar'])
subprocess.run([sys.executable,str(W/'install-grass-smoke-control.py'),'--version','1.0.10','--stage','012-entity-native-ui-scissor-smoke'],cwd=W,check=True)
subprocess.run([sys.executable,'-X','utf8',str(W/'run-cumulative-benchmark.py'),'--stage','012-entity-native-ui-scissor-smoke','--grass-smoke'],cwd=W,check=True)
report=json.loads((C/'runs/012-entity-native-ui-scissor-smoke/01-cumulative/GRASS-SMOKE.json').read_text());assert report['passed'] and report['api_renderer']=='VULKAN' and len(report['checks'])==9
others={p.name:sha(p) for p in (root/'mods').glob('*.jar') if p.name!=name};results=[];folder=C/'entity-native-retention-abbaab-20261005';assert not folder.exists();folder.mkdir()
for i,present in enumerate([False,True,True,False,False,True],1):
    stage=f'entity-native-retention-{i:02d}-'+('present' if present else 'absent');change(stage,enable=[name] if present else [],disable=[] if present else [name])
    assert {p.name:sha(p) for p in (root/'mods').glob('*.jar')}=={**others,**({name:pin} if present else {})}
    print('SOLE_ENTITY_NATIVE_RETENTION_VARIANT',i,present,flush=True)
    subprocess.run([sys.executable,'-X','utf8',str(W/'run-cumulative-benchmark.py'),'--stage',stage],cwd=W,check=True)
    out=C/'runs'/stage/'01-cumulative';fps=json.loads((out/'fps.json').read_text());inputs=json.loads((out/'inputs-private.json').read_text())
    assert fps['api_renderer']=='VULKAN' and not inputs['instrumented_diagnostic'] and not inputs['zink_translation_requested'] and json.loads((out/'PLAYABILITY.json').read_text())['accepted']
    assert {m['name']:m['sha256'] for m in inputs['mods']}=={**others,**({name:pin} if present else {})}
    start=fps['capture_epoch_ms'];end=start+fps['duration_ms'];rss=[]
    for r in csv.DictReader((out/'cpu.csv').open()):
        if start<=int(r['epoch_ms'])<=end:rss.append(float(r['java_rss_mib']))
    results.append({'stage':stage,'entity_present':present,**{k:fps[k] for k in ['average_fps','one_percent_low_fps','p95_ms','p99_ms','max_ms']},'capture_median_java_rss_mib':statistics.median(rss),'capture_rss_samples':len(rss)})
    (folder/'progress.json').write_text(json.dumps(results,indent=2)+'\n')
summary={label:{k:statistics.median(r[k] for r in results if r['entity_present']==present) for k in ['average_fps','one_percent_low_fps','p95_ms','p99_ms','max_ms','capture_median_java_rss_mib']} for label,present in [('absent',False),('present',True)]}
summary['fps_change_percent']=(summary['present']['average_fps']/summary['absent']['average_fps']-1)*100
# Require separable ranges plus improved lows/tails in this benchmark. Dense occlusion purpose is not inferred.
reliable=min(r['average_fps'] for r in results if r['entity_present'])>max(r['average_fps'] for r in results if not r['entity_present'])
retain=reliable and summary['present']['one_percent_low_fps']>=summary['absent']['one_percent_low_fps'] and summary['present']['p99_ms']<=summary['absent']['p99_ms'] and summary['present']['average_fps']>=2432
decision='SCOPED_PERFORMANCE_RETAINED' if retain else 'NO_ACCEPTED_NATIVE_SCENE_BENEFIT_DEFERRED_DENSE_OCCLUSION_PURPOSE'
if not retain:change('012-entity-native-retention-final-disable',disable=[name])
(folder/'SUMMARY.json').write_text(json.dumps({'scope':'Only pinned repaired EntityCulling toggled in six ABBAAB native captures; Hari .11, original grass, addon 1.0.3, controller 1.0.10, checksum world/settings/camera fixed. Concurrent workloads remain active. Still village benchmark, not a purpose-built dense occlusion scene.','trials':results,'summary':summary,'decision':decision,'native_ui_smoke_checks':report['checks'],'full_pack_accepted':False,'dense_occlusion_benefit_accepted':False},indent=2)+'\n')
records=json.loads((C/'progress.json').read_text());r=next(r for r in records if r['stage']=='012-entityculling');r.update({'previous_attempt_status':r['status'],'status':'PASS_WITH_RECORDED_LIMITS' if retain else 'REJECTED_NATIVE_PERFORMANCE_MOD','installed_repair_sha256':pin,'native_renderer':'VULKAN','native_ui_checks':report['checks'],'purpose_retention_decision':decision,'effective_trial_dirs':[x['stage']+'/01-cumulative' for x in results if x['entity_present']==retain],'latest_reference_median_fps':summary['present' if retain else 'absent']['average_fps'],'scope':'Native UI interoperability proven for pinned source; retained only if this scene shows separable FPS ranges with improved lows/tails. Dense occlusion benefit pending; no permanent content removal.'});(C/'progress.json').write_text(json.dumps(records,indent=2)+'\n')
print('ENTITY_NATIVE_RETENTION_FINISHED',decision,json.dumps(summary),flush=True)
