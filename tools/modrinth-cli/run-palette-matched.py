"""Only Hari .3/.4 varies; actual ABBAAB Minecraft comparison."""
from pathlib import Path
import argparse, hashlib, json, psutil, sqlite3, statistics, subprocess, sys

W=Path(__file__).resolve().parent
C=Path('C:/Users/Owner/Desktop/Minecraft Vulkan Hybrid/benchmarks/cumulative')
row=json.loads((C/'instance.json').read_text());root=Path(row['path']);cli=C.parent/'tools/mvh_pack_cli.exe'
assert row['id']=='local:b6985be9-ded6-4a96-85cd-5a2548b1d900'
assert json.loads((root/'MVH-CUMULATIVE-IDENTITY.json').read_text())['task']=='mvh-cumulative-20261005'
p=argparse.ArgumentParser();p.add_argument('--renderer',choices=['native','zink'],required=True);a=p.parse_args()
R=C/('palette-'+a.renderer+'-abbaab-20261005');assert not R.exists();R.mkdir()
names={version:f'harimt-forge-1.20.1-2.4.11-noxviola.{version}-dimensions-vulkan-hybrid-all.jar' for version in [3,4]}
pins={3:'203d5345ffd3dac9fdd2b717fa7d6d5674962456bfe937661a08d8a15898d4ec',4:'88acad7d8f8c55876ebae59591155108cdf422f558896d981afee937bb28d1a1'}

def sha(p):return hashlib.file_digest(p.open('rb'),'sha256').hexdigest()
def inactive():
    with sqlite3.connect('file:C:/Users/Owner/AppData/Roaming/ModrinthApp/app.db?mode=ro',uri=True) as db:
        assert all(not psutil.pid_exists(p[0]) for p in db.execute('select pid from processes where instance_id=?',(row['id'],)))

inactive()
for version,name in names.items():
    path=root/'mods'/name
    if not path.exists():path=path.with_name(path.name+'.disabled')
    assert sha(path)==pins[version]
embeddium='embeddium-0.3.31+mc1.20.1.jar'
# The renderer comparison changes no settings. Embeddium is retained for Zink;
# the separate native capability series uses the preceding native foundation.
prep=C/'stage-inputs'/('palette-'+a.renderer+'-prepare.json');assert not prep.exists()
prep.write_text(json.dumps({'add':[],'enable':['mods/'+embeddium] if a.renderer=='zink' else [],'disable':['mods/'+embeddium] if a.renderer=='native' else []},indent=2))
with (R/'prepare-private.log').open('w') as log:
    subprocess.run([str(cli),'add-cumulative',str(prep)],stdout=log,stderr=log,check=True,cwd=W)
others={f.name:sha(f) for f in (root/'mods').glob('*.jar') if f.name not in names.values()}
results=[]
for i,version in enumerate([3,4,4,3,3,4],1):
    inactive();stage=f'palette-{a.renderer}-{i:02d}-v{version}'
    request=C/'stage-inputs'/(stage+'.json');assert not request.exists()
    request.write_text(json.dumps({'add':[],'enable':['mods/'+names[version]],'disable':['mods/'+names[7-version]]},indent=2))
    with (R/(stage+'-toggle-private.log')).open('w') as log:
        subprocess.run([str(cli),'add-cumulative',str(request)],stdout=log,stderr=log,check=True,cwd=W)
    assert {f.name:sha(f) for f in (root/'mods').glob('*.jar') if f.name not in names.values()}==others
    print('PALETTE_MATCHED_TRIAL',i,a.renderer,version,flush=True)
    command=[sys.executable,'-X','utf8',str(W/'run-cumulative-benchmark.py'),'--stage',stage]
    if a.renderer=='zink':command.append('--zink')
    subprocess.run(command,check=True,cwd=W)
    trial=C/'runs'/stage/'01-cumulative';f=json.loads((trial/'fps.json').read_text());inputs=json.loads((trial/'inputs-private.json').read_text())
    assert not inputs['instrumented_diagnostic'] and json.loads((trial/'PLAYABILITY.json').read_text())['accepted']
    assert {m['name']:m['sha256'] for m in inputs['mods']}=={**others,names[version]:pins[version]}
    if a.renderer=='native':assert f['api_renderer']=='VULKAN'
    else:assert 'zink Vulkan' in f['gl_renderer'] and 'RTX 4090' in f['gl_renderer']
    results.append({'stage':stage,'hari_version':version,**{k:f[k] for k in ['average_fps','one_percent_low_fps','p95_ms','p99_ms','max_ms']}})
    (R/'progress.json').write_text(json.dumps(results,indent=2))
summary={str(version):{k:statistics.median(r[k] for r in results if r['hari_version']==version) for k in ['average_fps','one_percent_low_fps','p95_ms','p99_ms','max_ms']} for version in [3,4]}
summary['fps_change_percent']=(summary['4']['average_fps']/summary['3']['average_fps']-1)*100
(R/'SUMMARY.json').write_text(json.dumps({'scope':'Only Hari integrated .3/.4 varies; same scene/settings/foundation; ABBAAB; cache retained and concurrent projects running','renderer':a.renderer,'trials':results,'summary':summary},indent=2))
print('PALETTE_MATCHED_FINISHED',json.dumps(summary),flush=True)
