"""One-addon ABBAAB comparison with native visible grass and unchanged original JAR."""
from pathlib import Path
import argparse, hashlib, json, psutil, sqlite3, statistics, subprocess, sys
W=Path(__file__).resolve().parent;C=Path('C:/Users/Owner/Desktop/Minecraft Vulkan Hybrid/benchmarks/cumulative')
row=json.loads((C/'instance.json').read_text());root=Path(row['path']);cli=C.parent/'tools/mvh_pack_cli.exe'
assert row['id']=='local:b6985be9-ded6-4a96-85cd-5a2548b1d900'
assert json.loads((root/'MVH-CUMULATIVE-IDENTITY.json').read_text())=={'task':'mvh-cumulative-20261005','id':row['id'],'source':'local:2c68d7c5-8fae-4984-ad3e-c76db50b66c3'}
ap=argparse.ArgumentParser();ap.add_argument('--resume',action='store_true');ap.add_argument('--addon-version',choices=['1.0.0','1.0.1'],default='1.0.0');args=ap.parse_args()
source=W/'destination/compat/grass-distance-sort/build/libs'/f'mvh-grass-distance-compat-{args.addon_version}.jar';name=source.name
def sha(p):return hashlib.file_digest(p.open('rb'),'sha256').hexdigest()
digest=sha(source)
assert sha(root/'mods/Grassier-Grass-1.4.5-Render-Performance-Fix-REPLACEMENT-2.jar')=='91006e8220e65572d7e9e1e54b6058526c4adf908faedc750c9f19f13623df8c'
assert sha(root/'mods/harimt-forge-1.20.1-2.4.11-noxviola.9-dimensions-vulkan-hybrid-all.jar')=='c1d5d2e5b93ea55c0254843c2c29ee10ca1d1896e8971c1dfed0dba2f8b94cc8'
known_addons={'mvh-grass-distance-compat-1.0.0.jar','mvh-grass-distance-compat-1.0.1.jar'}
others={p.name:sha(p) for p in (root/'mods').glob('*.jar') if p.name not in known_addons}
prefix='grass-sort-native' if args.addon_version=='1.0.0' else 'grass-memo-native'
report=C/(prefix+'-abbaab-20261005');assert args.resume or not report.exists();report.mkdir(exist_ok=args.resume)
results=[]
def inactive():
    with sqlite3.connect('file:C:/Users/Owner/AppData/Roaming/ModrinthApp/app.db?mode=ro',uri=True) as db:
        assert all(not psutil.pid_exists(r[0]) for r in db.execute('select pid from processes where instance_id=?',(row['id'],)))
for i,present in enumerate([False,True,True,False,False,True],1):
    inactive();stage=f'{prefix}-{i:02d}-'+('present' if present else 'absent')
    out=C/'runs'/stage/'01-cumulative'
    if not (out/'DONE.json').exists():
        assert not (C/'runs'/stage).exists(),'Preserve incomplete trial and diagnose before changing a run name'
        request={'stage':stage,'add':[],'enable':[],'disable':['mods/'+p.name for p in (root/'mods').glob('*.jar') if p.name in known_addons and p.name!=name]}
        installed=(root/'mods'/name).exists() or (root/'mods'/(name+'.disabled')).exists()
        if present:
            if installed:request['enable']=['mods/'+name]
            else:request['add']=[{'path':str(source),'sha256':digest}]
        elif installed:request['disable'].append('mods/'+name)
        f=C/'stage-inputs'/(stage+'.json');assert not f.exists();f.write_text(json.dumps(request,indent=2)+'\n')
        with (report/(stage+'-install-private.log')).open('w') as log:subprocess.run([str(cli),'add-cumulative',str(f)],cwd=W,stdout=log,stderr=log,check=True,timeout=120)
        assert (root/'mods'/name).exists()==present
        assert {p.name:sha(p) for p in (root/'mods').glob('*.jar') if p.name!=name}==others
        print('EXACT_GRASS_SORT_VARIANT',i,'present' if present else 'absent',flush=True)
        subprocess.run([sys.executable,'-X','utf8',str(W/'run-cumulative-benchmark.py'),'--stage',stage],cwd=W,check=True)
    fps=json.loads((out/'fps.json').read_text());inputs=json.loads((out/'inputs-private.json').read_text())
    mods={p['name']:p['sha256'] for p in inputs['mods']};assert (mods.pop(name,None)==digest)==present and mods==others
    assert fps['api_renderer']=='VULKAN' and not inputs['instrumented_diagnostic']
    assert json.loads((out/'PLAYABILITY.json').read_text())['accepted']
    results.append({'stage':stage,'sort_addon_present':present,**{k:fps[k] for k in ['average_fps','one_percent_low_fps','p95_ms','p99_ms','max_ms']}})
    (report/'progress.json').write_text(json.dumps(results,indent=2)+'\n')
summary={}
for present in [False,True]:
    group=[r for r in results if r['sort_addon_present']==present]
    summary['present' if present else 'absent']={k:statistics.median(r[k] for r in group) for k in ['average_fps','one_percent_low_fps','p95_ms','p99_ms','max_ms']}
summary['fps_change_percent']=(summary['present']['average_fps']/summary['absent']['average_fps']-1)*100
(report/'SUMMARY.json').write_text(json.dumps({'comparison':'Only exact-distance sort addon toggled; pinned Hari .9 and original grass JAR retained; all settings/world/camera held constant; six ABBAAB native captures','sort_addon_version':args.addon_version,'sort_addon_sha256':digest,'trials':results,'summary':summary,'acceptance':'Requires screenshots, feature checks and contamination review; target success not inferred from unit parity'},indent=2)+'\n')
print('GRASS_SORT_MATCHED_RESULT',json.dumps(summary),flush=True)
