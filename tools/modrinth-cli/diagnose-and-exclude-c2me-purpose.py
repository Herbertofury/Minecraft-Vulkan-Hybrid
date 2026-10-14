"""Preserve failed paired study; check comment-only config changes and baseline terrain repeatability."""
from pathlib import Path
import hashlib,json,psutil,re,sqlite3,subprocess,sys
W=Path(__file__).resolve().parent;C=Path('C:/Users/Owner/Desktop/Minecraft Vulkan Hybrid/benchmarks/cumulative');r=json.loads((C/'instance.json').read_text());root=Path(r['path']);cli=C.parent/'tools/mvh_pack_cli.exe'
assert r['id']=='local:b6985be9-ded6-4a96-85cd-5a2548b1d900'
assert json.loads((root/'MVH-CUMULATIVE-IDENTITY.json').read_text())=={'task':'mvh-cumulative-20261005','id':r['id'],'source':'local:2c68d7c5-8fae-4984-ad3e-c76db50b66c3'}
group=C/'c2me-chunk-purpose-abbaab-20261005';assert not (group/'SUMMARY.json').exists()
dirs=[C/'runs/c2me-chunk-purpose-01-absent/01-cumulative',C/'runs/c2me-chunk-purpose-02-present/01-cumulative'];inputs=[json.loads((p/'inputs-private.json').read_text()) for p in dirs];reports=[json.loads((p/'CHUNKS.json').read_text()) for p in dirs]
def properties(p):
    values={}
    for line in p.read_text(encoding='utf8').splitlines():
        line=line.strip()
        if not line or line.startswith(('#','!')):continue
        assert re.fullmatch(r'[A-Za-z0-9_.]+\s*=\s*(true|false)',line),'Unknown properties syntax cannot be normalized'
        key,value=map(str.strip,line.split('=',1));assert key not in values;values[key]=value
    return values
names=[k for k in set(inputs[0]['config_sha256'])|set(inputs[1]['config_sha256']) if inputs[0]['config_sha256'].get(k)!=inputs[1]['config_sha256'].get(k)]
assert set(names)=={'modernfix-mixins.properties','harimt-vulkan-compat.properties'}
assert properties(dirs[0]/'config-input-private/modernfix-mixins.properties')==properties(dirs[1]/'config-input-private/modernfix-mixins.properties')
assert inputs[0]['visual_options']==inputs[1]['visual_options'] and inputs[0]['world']==inputs[1]['world']
name='c2meforge-0.2.0-forge.9.1-all-DimThread-Interop-v4.1.jar';a={m['name']:m['sha256'] for m in inputs[0]['mods']};b={m['name']:m['sha256'] for m in inputs[1]['mods']};assert b=={**a,name:'536097d1a6e1b8e639d6f5e4a37f27a60cfee72989e677171b8d261a21580867'}
proof={'same_explicit_config_values':True,'only_mod_toggle':'C2ME','changed_config_files':names,'modernfix_difference':'Only three generated comment lines describing effective automatic compatibility defaults; all explicit boolean properties are identical. Automatic ModList compatibility behavior is part of the C2ME variant.','generated_gate_cache_excluded':True,'block_biome_digest_parity_accepted':False,'all_144_ordered_digests_equal':reports[0]['terrain_sha256_ordered']==reports[1]['terrain_sha256_ordered'],'worldgen_digest_limit':'Registry ID mapping and within-vanilla repeatability are not yet verified. Different digests alone are not declared a C2ME terrain corruption.','six_run_series_completed':False,'fps_accepted':False}
(group/'STOPPED-INPUT-ANALYSIS.json').write_text(json.dumps(proof,indent=2)+'\n')
with sqlite3.connect('file:C:/Users/Owner/AppData/Roaming/ModrinthApp/app.db?mode=ro',uri=True) as db:assert all(not psutil.pid_exists(x[0]) for x in db.execute('select pid from processes where instance_id=?',(r['id'],)))
file=C/'stage-inputs/014-c2me-unproven-purpose-disable.json';assert not file.exists();file.write_text(json.dumps({'stage':'014-c2me-unproven-purpose-disable','add':[],'enable':[],'disable':['mods/'+name]},indent=2)+'\n')
with (C/'014-c2me-unproven-purpose-disable-private.log').open('w') as log:subprocess.run([str(cli),'add-cumulative',str(file)],cwd=W,stdout=log,stderr=log,check=True,timeout=120)
subprocess.run([sys.executable,'-X','utf8',str(W/'run-cumulative-benchmark.py'),'--stage','014-c2me-absent-chunk-repeatability','--chunk-purpose','--repeat','2'],cwd=W,check=True)
repeat=[json.loads((p/'CHUNKS.json').read_text()) for p in sorted((C/'runs/014-c2me-absent-chunk-repeatability').glob('*')) if (p/'CHUNKS.json').exists()];assert len(repeat)==2
absent=[reports[0],*repeat];parity=all(x['terrain_sha256_ordered']==absent[0]['terrain_sha256_ordered'] for x in absent)
data={'scope':'Stopped intended six-run study: one absent and one present paired native chunk diagnostic, plus two fresh absent repeatability diagnostics and one earlier C2ME-present probe. No FPS acceptance. C2ME remains disabled in the owned continuation because primary benefit and terrain parity are unestablished. Original files and all diagnostics retained.','decision':'REJECTED_UNPROVEN_CHUNK_PURPOSE_AND_UNACCEPTED_PARITY','complete_six_run_causal_speedup_or_regression_accepted':False,'initial_absent_generation_wall_s':reports[0]['generation_wall_s'],'initial_present_generation_wall_s':reports[1]['generation_wall_s'],'earlier_present_probe_wall_s':json.loads((C/'runs/014-c2me-fixed-chunk-purpose-probe/01-cumulative/CHUNKS.json').read_text())['generation_wall_s'],'absent_repeat_generation_wall_s':[x['generation_wall_s'] for x in repeat],'absent_terrain_digests_repeatable':parity,'initial_absent_present_digests_equal':proof['all_144_ordered_digests_equal'],'registry_id_mapping_equality_verified':False,'full_pack_accepted':False,'next_work':'Resolve C2ME scheduling/terrain parity before any future performance retention. Baseline native capability restored separately.'}
(group/'STOPPED-SUMMARY.json').write_text(json.dumps(data,indent=2)+'\n')
subprocess.run([sys.executable,'-X','utf8',str(W/'run-cumulative-benchmark.py'),'--stage','014-c2me-removed-native-reference-v11','--repeat','3'],cwd=W,check=True)
records=json.loads((C/'progress.json').read_text());record=next(x for x in records if x['stage']=='014-c2meforge');record.update({'status':'REJECTED_NATIVE_PERFORMANCE_MOD','purpose_retention_decision':data['decision'],'chunk_purpose_analysis':data,'effective_trial_dirs':['014-c2me-removed-native-reference-v11/'+p.name for p in sorted((C/'runs/014-c2me-removed-native-reference-v11').glob('*')) if (p/'DONE.json').exists()]})
assert len(record['effective_trial_dirs'])==3
(C/'progress.json').write_text(json.dumps(records,indent=2)+'\n');print('C2ME_UNPROVEN_PURPOSE_EXCLUDED_AND_NATIVE_REFERENCE_RESTORED',json.dumps(data),flush=True)
