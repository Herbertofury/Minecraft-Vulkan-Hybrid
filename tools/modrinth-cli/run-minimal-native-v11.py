"""After matched grass runs, test only integrated Hari and recorder; restore all owned profile mods."""
from pathlib import Path
import hashlib,json,psutil,sqlite3,statistics,subprocess,sys,time
W=Path(__file__).resolve().parent;C=Path('C:/Users/Owner/Desktop/Minecraft Vulkan Hybrid/benchmarks/cumulative');cli=C.parent/'tools/mvh_pack_cli.exe'
deadline=time.monotonic()+1800;study=C/'grass-terrain-native-abbaab-20261005/SUMMARY.json'
while not study.exists():assert time.monotonic()<deadline,'Grass matched study unfinished; diagnose before minimal test';time.sleep(10)
assert len(json.loads(study.read_text())['trials'])==6
row=json.loads((C/'instance.json').read_text());root=Path(row['path'])
assert row['id']=='local:b6985be9-ded6-4a96-85cd-5a2548b1d900'
assert json.loads((root/'MVH-CUMULATIVE-IDENTITY.json').read_text())=={'task':'mvh-cumulative-20261005','id':row['id'],'source':'local:2c68d7c5-8fae-4984-ad3e-c76db50b66c3'}
def inactive():
    with sqlite3.connect('file:C:/Users/Owner/AppData/Roaming/ModrinthApp/app.db?mode=ro',uri=True) as db:assert all(not psutil.pid_exists(p[0]) for p in db.execute('select pid from processes where instance_id=?',(row['id'],)))
def sha(path):return hashlib.file_digest(path.open('rb'),'sha256').hexdigest()
def toggle(label,request):
    inactive();file=C/'stage-inputs'/(label+'.json');assert not file.exists();file.write_text(json.dumps({'stage':label,**request},indent=2)+'\n')
    with (C/(label+'-toggle-private.log')).open('w') as log:subprocess.run([str(cli),'add-cumulative',str(file)],cwd=W,stdout=log,stderr=log,check=True,timeout=120)
original={p.name:sha(p) for p in (root/'mods').glob('*.jar')}
name='harimt-forge-1.20.1-2.4.11-noxviola.11-dimensions-vulkan-hybrid-all.jar';control='mvh-noxviola-control-1.0.11.jar'
assert original[name]=='8333a3fc7008b2e126e8a409bf1b9ab8530d2f86df130bd38c2800d7b267c665'
assert original[control]=='589ec0b4b996212ea80b7b852dd69733b5eb5adbd13807b6ddd2f6bac8f85d76'
out=C/'minimal-native-v11-capability-20261005';assert not out.exists();out.mkdir();(out/'original-owned-active-mods.json').write_text(json.dumps(original,indent=2)+'\n')
minimal={name:original[name],control:original[control]};removed=[name for name in original if name not in minimal]
toggle('minimal-native-v11-prepare',{'add':[],'enable':[],'disable':['mods/'+name for name in removed]})
try:
    assert {p.name:sha(p) for p in (root/'mods').glob('*.jar')}==minimal
    subprocess.run([sys.executable,'-X','utf8',str(W/'run-cumulative-benchmark.py'),'--stage','minimal-native-v11-capability','--repeat','3'],cwd=W,check=True)
    results=[]
    for trial in sorted((C/'runs/minimal-native-v11-capability').glob('*')):
        if not trial.is_dir() or not (trial/'DONE.json').exists():continue
        fps=json.loads((trial/'fps.json').read_text());inputs=json.loads((trial/'inputs-private.json').read_text())
        assert fps['api_renderer']=='VULKAN' and not inputs['instrumented_diagnostic']
        assert {m['name']:m['sha256'] for m in inputs['mods']}==minimal
        assert json.loads((trial/'PLAYABILITY.json').read_text())['accepted']
        results.append({'trial':trial.name,**{k:fps[k] for k in ['average_fps','one_percent_low_fps','p95_ms','p99_ms','max_ms']}})
    assert len(results)==3
    summary={k:statistics.median(row[k] for row in results) for k in ['average_fps','one_percent_low_fps','p95_ms','p99_ms','max_ms']}
    (out/'SUMMARY.json').write_text(json.dumps({'scope':'Minimal integrated Hari .11 plus identical recorder only; vanilla checksum scene/settings/camera. Three capability repeats, not a matched causal speedup comparison or unmodded Minecraft. Original concurrent workloads remain active.','mods':minimal,'trials':results,'summary':summary,'target_average_fps_reached':summary['average_fps']>=2432,'full_pack_accepted':False,'zero_hitch_accepted':False},indent=2)+'\n')
    print('MINIMAL_NATIVE_V11_CAPABILITY',json.dumps(summary),flush=True)
finally:
    toggle('minimal-native-v11-restore',{'add':[],'enable':['mods/'+name for name in removed],'disable':[]})
    assert {p.name:sha(p) for p in (root/'mods').glob('*.jar')}==original
    (out/'RESTORED.json').write_text(json.dumps({'owned_profile_mod_set_restored':True,'mods_sha256':original},indent=2)+'\n')
    print('OWNED_NATIVE_GRASS_PROFILE_RESTORED',flush=True)
