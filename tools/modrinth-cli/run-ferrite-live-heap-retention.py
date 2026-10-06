"""Sole-mod six-run live managed heap comparison; diagnostic FPS never promotes."""
from pathlib import Path
import hashlib,json,psutil,sqlite3,statistics,subprocess,sys
W=Path(__file__).resolve().parent
C=Path('C:/Users/Owner/Desktop/Minecraft Vulkan Hybrid/benchmarks/cumulative')
row=json.loads((C/'instance.json').read_text());root=Path(row['path']);cli=C.parent/'tools/mvh_pack_cli.exe'
assert row['id']=='local:b6985be9-ded6-4a96-85cd-5a2548b1d900'
assert json.loads((root/'MVH-CUMULATIVE-IDENTITY.json').read_text())=={'task':'mvh-cumulative-20261005','id':row['id'],'source':'local:2c68d7c5-8fae-4984-ad3e-c76db50b66c3'}
probe=json.loads((C/'runs/013-ferrite-native-live-heap-probe-retry1/01-cumulative/HEAP.json').read_text())
assert probe['status']=='complete' and probe['actual_renderer']=='VULKAN' and not probe['fps_promotion_accepted']
def sha(p):
    with p.open('rb') as f:return hashlib.file_digest(f,'sha256').hexdigest()
def change(stage,present):
    with sqlite3.connect('file:C:/Users/Owner/AppData/Roaming/ModrinthApp/app.db?mode=ro',uri=True) as db:
        assert all(not psutil.pid_exists(r[0]) for r in db.execute('select pid from processes where instance_id=?',(row['id'],)))
    target=root/'mods'/name
    assert sha(target if target.exists() else target.with_suffix('.jar.disabled'))==pin
    p=C/'stage-inputs'/(stage+'.json');assert not p.exists()
    p.write_text(json.dumps({'stage':stage,'add':[],'enable':['mods/'+name] if present else [],'disable':[] if present else ['mods/'+name]},indent=2)+'\n')
    with (C/(stage+'-install-private.log')).open('w') as log:
        subprocess.run([str(cli),'add-cumulative',str(p)],cwd=W,stdout=log,stderr=log,check=True,timeout=120)
name='ferritecore-6.0.1-forge.jar';pin=sha(root/'mods'/name)
with (root/'mods'/name).open('rb') as f:
    assert hashlib.file_digest(f,'sha512').hexdigest()=='a1960a7c03dc32d4ccaccaf28afdd9b078758bbd62d15a91d4039a83fa9397a098e89b69591f6bd5190254d9ee97e502504154b9aec764adb8c65f000b75ba2c'
others={p.name:sha(p) for p in (root/'mods').glob('*.jar') if p.name!=name}
folder=C/'ferrite-live-heap-abbaab-20261005';assert not folder.exists();folder.mkdir();results=[]
semantic_config=visual=world=None
for i,present in enumerate([False,True,True,False,False,True],1):
    stage=f'ferrite-live-heap-{i:02d}-'+('present' if present else 'absent')
    change(stage,present)
    expected={**others,**({name:pin} if present else {})}
    assert {p.name:sha(p) for p in (root/'mods').glob('*.jar')}==expected
    print('SOLE_FERRITE_LIVE_HEAP_VARIANT',i,present,flush=True)
    subprocess.run([sys.executable,'-X','utf8',str(W/'run-cumulative-benchmark.py'),'--stage',stage,'--heap-diagnostic'],cwd=W,check=True)
    out=C/'runs'/stage/'01-cumulative';inputs=json.loads((out/'inputs-private.json').read_text());heap=json.loads((out/'HEAP.json').read_text())
    assert inputs['instrumented_diagnostic'] and not inputs['zink_translation_requested'] and heap['actual_renderer']=='VULKAN'
    assert not heap['fps_promotion_accepted'] and heap['only_marker_owned_client_targeted'] and not heap['object_contents_or_heap_dump_collected']
    assert {m['name']:m['sha256'] for m in inputs['mods']}==expected
    assert json.loads((out/'PLAYABILITY.json').read_text())['accepted']
    config={k:v for k,v in inputs['config_sha256'].items() if k!='harimt-vulkan-compat.properties'}
    if semantic_config is None:semantic_config=config;visual=inputs['visual_options'];world=inputs['world']
    assert config==semantic_config and inputs['visual_options']==visual and inputs['world']==world
    results.append({'stage':stage,'ferrite_present':present,**heap})
    (folder/'progress.json').write_text(json.dumps(results,indent=2)+'\n')
summary={label:{k:statistics.median(r[k] for r in results if r['ferrite_present']==present) for k in ['managed_live_objects','managed_live_bytes','managed_live_mib','diagnostic_duration_ms']} for label,present in [('absent',False),('present',True)]}
summary['managed_live_heap_change_percent']=(summary['present']['managed_live_bytes']/summary['absent']['managed_live_bytes']-1)*100
reliable=max(r['managed_live_bytes'] for r in results if r['ferrite_present'])<min(r['managed_live_bytes'] for r in results if not r['ferrite_present'])
retain=reliable and summary['managed_live_heap_change_percent']<=-2
decision='RETAINED_FOR_MEASURED_LIVE_MANAGED_HEAP_REDUCTION' if retain else 'NO_ACCEPTED_MEMORY_PURPOSE_BENEFIT_DISABLED'
if not retain:change('013-ferrite-live-heap-final-disable',False)
proof={'only_ferrite_toggled':True,'official_file_sha256':pin,'official_modrinth_sha512_verified':True,'semantic_config_unchanged':True,'visual_options_unchanged':True,'world_identity_unchanged':True,'excluded_derived_cache':'Only harimt-vulkan-compat.properties: generated gate signature/timestamp, not user settings','semantic_config_manifest_sha256':hashlib.sha256(json.dumps(semantic_config,sort_keys=True).encode()).hexdigest()}
(folder/'INPUT-PARITY.json').write_text(json.dumps(proof,indent=2)+'\n')
(folder/'SUMMARY.json').write_text(json.dumps({'scope':'Six ABBAAB actual JVM GC.class_histogram live-object totals at 25 seconds after stable-scene warmup; sole FerriteCore toggle, Hari .11, original grass/addon, C2ME and controller fixed. No object contents/heap dump; all diagnostic FPS excluded. Other workloads remained active. Managed heap excludes native memory, working set and VRAM.','trials':results,'summary':summary,'decision':decision,'full_pack_accepted':False,'fps_improvement_claimed':False},indent=2)+'\n')
records=json.loads((C/'progress.json').read_text());r=next(r for r in records if r['stage']=='013-ferritecore')
r.update({'status':'PASS_WITH_RECORDED_LIMITS' if retain else 'REJECTED_NATIVE_PERFORMANCE_MOD','purpose_retention_decision':decision,'memory_purpose_summary':summary,'heap_study':'ferrite-live-heap-abbaab-20261005','fps_improvement_claimed':False})
(C/'progress.json').write_text(json.dumps(records,indent=2)+'\n')
print('FERRITE_LIVE_HEAP_RETENTION_FINISHED',decision,json.dumps(summary),flush=True)
