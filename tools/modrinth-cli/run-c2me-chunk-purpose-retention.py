"""Six sole-C2ME batch chunk generations in restored worlds, with full block/biome digest parity."""
from pathlib import Path
import hashlib,json,psutil,sqlite3,statistics,subprocess,sys
W=Path(__file__).resolve().parent;C=Path('C:/Users/Owner/Desktop/Minecraft Vulkan Hybrid/benchmarks/cumulative')
row=json.loads((C/'instance.json').read_text());root=Path(row['path']);cli=C.parent/'tools/mvh_pack_cli.exe'
assert row['id']=='local:b6985be9-ded6-4a96-85cd-5a2548b1d900'
assert json.loads((root/'MVH-CUMULATIVE-IDENTITY.json').read_text())=={'task':'mvh-cumulative-20261005','id':row['id'],'source':'local:2c68d7c5-8fae-4984-ad3e-c76db50b66c3'}
probe=json.loads((C/'runs/014-c2me-fixed-chunk-purpose-probe/01-cumulative/CHUNKS.json').read_text())
assert probe['passed'] and probe['api_renderer']=='VULKAN' and probe['verified_full_lit_chunks']==144 and not probe['fps_accepted']
def sha(p):
    with p.open('rb') as f:return hashlib.file_digest(f,'sha256').hexdigest()
def change(stage,present):
    with sqlite3.connect('file:C:/Users/Owner/AppData/Roaming/ModrinthApp/app.db?mode=ro',uri=True) as db:
        assert all(not psutil.pid_exists(r[0]) for r in db.execute('select pid from processes where instance_id=?',(row['id'],)))
    target=root/'mods'/name;assert sha(target if target.exists() else target.with_suffix('.jar.disabled'))==pin
    p=C/'stage-inputs'/(stage+'.json');assert not p.exists()
    p.write_text(json.dumps({'stage':stage,'add':[],'enable':['mods/'+name] if present else [],'disable':[] if present else ['mods/'+name]},indent=2)+'\n')
    with (C/(stage+'-install-private.log')).open('w') as log:
        subprocess.run([str(cli),'add-cumulative',str(p)],cwd=W,stdout=log,stderr=log,check=True,timeout=120)
name='c2meforge-0.2.0-forge.9.1-all-DimThread-Interop-v4.1.jar';pin=sha(root/'mods'/name)
assert pin=='536097d1a6e1b8e639d6f5e4a37f27a60cfee72989e677171b8d261a21580867'
others={p.name:sha(p) for p in (root/'mods').glob('*.jar') if p.name!=name}
folder=C/'c2me-chunk-purpose-abbaab-20261005';assert not folder.exists();folder.mkdir();results=[]
semantic_config=visual=world=terrain=None
for i,present in enumerate([False,True,True,False,False,True],1):
    stage=f'c2me-chunk-purpose-{i:02d}-'+('present' if present else 'absent');change(stage,present)
    expected={**others,**({name:pin} if present else {})}
    assert {p.name:sha(p) for p in (root/'mods').glob('*.jar')}==expected
    print('SOLE_C2ME_CHUNK_PURPOSE_VARIANT',i,present,flush=True)
    subprocess.run([sys.executable,'-X','utf8',str(W/'run-cumulative-benchmark.py'),'--stage',stage,'--chunk-purpose'],cwd=W,check=True)
    out=C/'runs'/stage/'01-cumulative';inputs=json.loads((out/'inputs-private.json').read_text());chunks=json.loads((out/'CHUNKS.json').read_text())
    assert inputs['instrumented_diagnostic'] and not inputs['zink_translation_requested'] and chunks['api_renderer']=='VULKAN'
    assert chunks['passed'] and not chunks['fps_accepted'] and chunks['requested_chunks']==chunks['completed_futures']==chunks['verified_full_lit_chunks']==144
    assert {m['name']:m['sha256'] for m in inputs['mods']}==expected
    config={k:v for k,v in inputs['config_sha256'].items() if k!='harimt-vulkan-compat.properties'}
    if semantic_config is None:semantic_config=config;visual=inputs['visual_options'];world=inputs['world'];terrain=chunks['terrain_sha256_ordered']
    assert config==semantic_config and inputs['visual_options']==visual and inputs['world']==world
    assert chunks['terrain_sha256_ordered']==terrain,'Block/biome generation differs; preserve evidence, do not retain or advance'
    results.append({'stage':stage,'c2me_present':present,**chunks})
    (folder/'progress.json').write_text(json.dumps(results,indent=2)+'\n')
summary={label:{k:statistics.median(r.get(k,0) for r in results if r['c2me_present']==present) for k in ['generation_wall_s','server_tick_interval_p99_ms','server_tick_interval_max_ms','server_tick_interval_samples']} for label,present in [('absent',False),('present',True)]}
summary['generation_time_change_percent']=(summary['present']['generation_wall_s']/summary['absent']['generation_wall_s']-1)*100
reliable=max(r['generation_wall_s'] for r in results if r['c2me_present'])<min(r['generation_wall_s'] for r in results if not r['c2me_present'])
retain=reliable and summary['generation_time_change_percent']<=-2
decision='RETAINED_FOR_MEASURED_MATCHED_CHUNK_GENERATION_BENEFIT' if retain else 'NO_ACCEPTED_CHUNK_GENERATION_BENEFIT_DISABLED'
if not retain:change('014-c2me-chunk-purpose-final-disable',False)
proof={'only_c2me_toggled':True,'c2me_sha256':pin,'semantic_config_unchanged':True,'visual_options_unchanged':True,'world_identity_unchanged':True,'pristine_target_region_absent':True,'all_144_ordered_block_biome_digests_equal_across_six_runs':True,'terrain_digest_scope':probe['terrain_digest_scope'],'excluded_derived_cache':'Only harimt-vulkan-compat.properties generated gate signature/timestamp, not user settings','semantic_config_manifest_sha256':hashlib.sha256(json.dumps(semantic_config,sort_keys=True).encode()).hexdigest()}
(folder/'INPUT-PARITY.json').write_text(json.dumps(proof,indent=2)+'\n')
(folder/'SUMMARY.json').write_text(json.dumps({'scope':'Six ABBAAB actual native sessions; one C2ME toggle, fixed 144 originally ungenerated FULL/lit chunks x/z 1024..1035; block/biome digest parity. Same pristine world, Hari .11, original grass/addon, controller 1.0.11 and all other JARs/settings/configs fixed. Whole-adapter telemetry includes active other projects. Not FPS, loading-menu startup, entity/NBT/light-array parity or a no-hitch/full-pack acceptance.','trials':results,'summary':summary,'decision':decision,'full_pack_accepted':False,'fps_improvement_claimed':False},indent=2)+'\n')
records=json.loads((C/'progress.json').read_text());r=next(r for r in records if r['stage']=='014-c2meforge')
r.update({'status':'PASS_WITH_RECORDED_LIMITS' if retain else 'REJECTED_NATIVE_PERFORMANCE_MOD','purpose_retention_decision':decision,'chunk_generation_summary':summary,'chunk_study':'c2me-chunk-purpose-abbaab-20261005','fps_improvement_claimed':False})
(C/'progress.json').write_text(json.dumps(records,indent=2)+'\n')
print('C2ME_CHUNK_PURPOSE_RETENTION_FINISHED',decision,json.dumps(summary),flush=True)
