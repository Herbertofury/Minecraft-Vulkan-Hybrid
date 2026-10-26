"""Only Hari .9/.10 varies; original native grass and addon 1.0.3 remain fixed."""
from pathlib import Path
import argparse,hashlib,json,psutil,sqlite3,statistics,subprocess,sys
W=Path(__file__).resolve().parent;C=Path('C:/Users/Owner/Desktop/Minecraft Vulkan Hybrid/benchmarks/cumulative')
row=json.loads((C/'instance.json').read_text());root=Path(row['path']);cli=C.parent/'tools/mvh_pack_cli.exe'
assert row['id']=='local:b6985be9-ded6-4a96-85cd-5a2548b1d900'
assert json.loads((root/'MVH-CUMULATIVE-IDENTITY.json').read_text())=={'task':'mvh-cumulative-20261005','id':row['id'],'source':'local:2c68d7c5-8fae-4984-ad3e-c76db50b66c3'}
parser=argparse.ArgumentParser();parser.add_argument('--resume',action='store_true');args=parser.parse_args()
report=C/'grass-terrain-native-abbaab-20261005';assert args.resume or not report.exists();report.mkdir(exist_ok=args.resume)
names={v:f'harimt-forge-1.20.1-2.4.11-noxviola.{v}-dimensions-vulkan-hybrid-all.jar' for v in [9,10]}
pins={9:'c1d5d2e5b93ea55c0254843c2c29ee10ca1d1896e8971c1dfed0dba2f8b94cc8',10:'9abd89393e1ffba7b705d78f61c718417309f9b102ced4432f674da8cc09cd7a'}
def sha(path):return hashlib.file_digest(path.open('rb'),'sha256').hexdigest()
def inactive():
    with sqlite3.connect('file:C:/Users/Owner/AppData/Roaming/ModrinthApp/app.db?mode=ro',uri=True) as db:assert all(not psutil.pid_exists(r[0]) for r in db.execute('select pid from processes where instance_id=?',(row['id'],)))
inactive()
for version,name in names.items():
    path=root/'mods'/name
    if not path.exists():path=path.with_name(path.name+'.disabled')
    assert sha(path)==pins[version]
assert sha(root/'mods/mvh-grass-distance-compat-1.0.3.jar')=='45d0067072c608eccf6ebafc06990a7ca5661c08bd5828118834f1b1a0eb325a'
assert sha(root/'mods/Grassier-Grass-1.4.5-Render-Performance-Fix-REPLACEMENT-2.jar')=='91006e8220e65572d7e9e1e54b6058526c4adf908faedc750c9f19f13623df8c'
others={p.name:sha(p) for p in (root/'mods').glob('*.jar') if p.name not in names.values()}
assert not any('embeddium' in name.lower() or 'oculus' in name.lower() for name in others)
results=[]
for i,version in enumerate([9,10,10,9,9,10],1):
    inactive();stage=f'grass-terrain-native-{i:02d}-v{version}';out=C/'runs'/stage/'01-cumulative'
    if not (out/'DONE.json').exists():
        assert not (C/'runs'/stage).exists(),'Preserve incomplete capture and diagnose before retry'
        request=C/'stage-inputs'/(stage+'.json');assert not request.exists()
        request.write_text(json.dumps({'stage':stage,'add':[],'enable':['mods/'+names[version]],'disable':['mods/'+names[19-version]]},indent=2)+'\n')
        with (report/(stage+'-toggle-private.log')).open('w') as log:subprocess.run([str(cli),'add-cumulative',str(request)],cwd=W,stdout=log,stderr=log,check=True,timeout=120)
        assert {p.name:sha(p) for p in (root/'mods').glob('*.jar')}=={**others,names[version]:pins[version]}
        print('EXACT_HARI_NATIVE_VARIANT',i,version,flush=True)
        subprocess.run([sys.executable,'-X','utf8',str(W/'run-cumulative-benchmark.py'),'--stage',stage],cwd=W,check=True)
    fps=json.loads((out/'fps.json').read_text());inputs=json.loads((out/'inputs-private.json').read_text())
    assert fps['api_renderer']=='VULKAN' and not inputs['instrumented_diagnostic'] and not inputs['zink_translation_requested']
    assert {m['name']:m['sha256'] for m in inputs['mods']}=={**others,names[version]:pins[version]}
    assert json.loads((out/'PLAYABILITY.json').read_text())['accepted']
    results.append({'stage':stage,'hari_version':version,**{k:fps[k] for k in ['average_fps','one_percent_low_fps','p95_ms','p99_ms','max_ms']}})
    (report/'progress.json').write_text(json.dumps(results,indent=2)+'\n')
summary={str(v):{k:statistics.median(r[k] for r in results if r['hari_version']==v) for k in ['average_fps','one_percent_low_fps','p95_ms','p99_ms','max_ms']} for v in [9,10]}
summary['fps_change_percent']=(summary['10']['average_fps']/summary['9']['average_fps']-1)*100
(report/'SUMMARY.json').write_text(json.dumps({'scope':'Only Hari .9/.10 varies; pinned native original grass, addon 1.0.3 and recorder unchanged; checksum world/settings/camera; six ABBAAB runs; other projects remain active','pins':pins,'trials':results,'summary':summary,'acceptance':'Representative screenshots and play checks separate; full pack, shader parity and zero hitching are not inferred'},indent=2)+'\n')
print('HARI_NATIVE_MATCHED_RESULT',json.dumps(summary),flush=True)
