"""ABBAAB Citadel-only comparison in the owned cumulative profile."""
from pathlib import Path
import hashlib,json,sqlite3,psutil,subprocess,sys,statistics,argparse
W=Path(__file__).resolve().parent
C=Path('C:/Users/Owner/Desktop/Minecraft Vulkan Hybrid/benchmarks/cumulative')
P=json.loads((C/'instance.json').read_text());ROOT=Path(P['path']);CLI=C.parent/'tools/mvh_pack_cli.exe'
NAME='citadel-2.6.3-1.20.1.jar';SHA='56c7ab87348a5292583aac787b6cf11b5193e77f02af1d17ea75a96f8640b3ce'
SOURCE=Path('C:/Users/Owner/AppData/Roaming/ModrinthApp/profiles/Noxviola/mods')/NAME
assert ROOT.name!='Noxviola' and json.loads((ROOT/'MVH-CUMULATIVE-IDENTITY.json').read_text())['id']==P['id']
def sha(p):return hashlib.file_digest(p.open('rb'),'sha256').hexdigest()
def inactive():
 with sqlite3.connect('file:C:/Users/Owner/AppData/Roaming/ModrinthApp/app.db?mode=ro',uri=True) as db:
  assert all(not psutil.pid_exists(row[0]) for row in db.execute('select pid from processes where instance_id=?',(P['id'],)))
assert sha(SOURCE)==SHA
others={p.name:sha(p) for p in (ROOT/'mods').glob('*.jar') if p.name!=NAME}
ap=argparse.ArgumentParser();ap.add_argument('--resume',action='store_true');args=ap.parse_args();R=C/'citadel-only-abbaab-20261005';assert args.resume or not R.exists();R.mkdir(exist_ok=args.resume)
results=[]
for i,present in enumerate([False,True,True,False,False,True],1):
 inactive();base=f'citadel-cost-{i:02d}-'+('present' if present else 'absent');stage=base
 completed=C/'runs'/base/'01-cumulative'
 if (completed/'DONE.json').exists():
  fps=json.loads((completed/'fps.json').read_text());assert fps['api_renderer']=='VULKAN' and json.loads((completed/'PLAYABILITY.json').read_text())['accepted'];inputs=json.loads((completed/'inputs-private.json').read_text());mods={p['name']:p['sha256'] for p in inputs['mods']};assert (mods.pop(NAME,None)==SHA)==present and mods==others
  results.append({'stage':stage,'citadel_present':present,**{k:fps[k] for k in ['average_fps','one_percent_low_fps','p95_ms','p99_ms','max_ms']}});continue
 if (C/'runs'/stage).exists():
  assert args.resume and (completed/'REJECTED.json').exists();stage=base+'-retry2';assert not (C/'runs'/stage).exists()
 request={'stage':stage,'add':[],'disable':[] if present else ['mods/'+NAME],'enable':['mods/'+NAME] if present else []}
 f=C/'stage-inputs'/(stage+'.json');assert not f.exists();f.write_text(json.dumps(request,indent=2)+'\n')
 with (R/(stage+'-toggle-private.log')).open('w') as log:
  subprocess.run([str(CLI),'add-cumulative',str(f)],cwd=W,stdout=log,stderr=log,check=True)
 assert (ROOT/'mods'/NAME).exists()==present and (ROOT/'mods'/(NAME+'.disabled')).exists()==(not present)
 assert sha(ROOT/'mods'/(NAME if present else NAME+'.disabled'))==SHA
 assert others=={p.name:sha(p) for p in (ROOT/'mods').glob('*.jar') if p.name!=NAME}
 print('CITADEL_ONLY_VARIANT',i,'present' if present else 'absent',flush=True)
 subprocess.run([sys.executable,'-X','utf8',str(W/'run-cumulative-benchmark.py'),'--stage',stage],cwd=W,check=True)
 folder=C/'runs'/stage/'01-cumulative';fps=json.loads((folder/'fps.json').read_text())
 assert fps['api_renderer']=='VULKAN' and json.loads((folder/'PLAYABILITY.json').read_text())['accepted']
 results.append({'stage':stage,'citadel_present':present,**{k:fps[k] for k in ['average_fps','one_percent_low_fps','p95_ms','p99_ms','max_ms']}})
 (R/'progress.json').write_text(json.dumps(results,indent=2)+'\n')
assert sha(ROOT/'mods'/NAME)==SHA and sha(SOURCE)==SHA
summary={}
for present in [False,True]:
 group=[r for r in results if r['citadel_present']==present]
 summary['present' if present else 'absent']={k:statistics.median(r[k] for r in group) for k in ['average_fps','one_percent_low_fps','p95_ms','p99_ms','max_ms']}
summary['fps_change_percent']=(summary['present']['average_fps']/summary['absent']['average_fps']-1)*100
(R/'SUMMARY.json').write_text(json.dumps({'comparison':'Only Citadel toggled; eight-JAR cumulative foundation otherwise held constant; ABBAAB native Vulkan','trials':results,'summary':summary},indent=2)+'\n')
print('CITADEL_ONLY_MATCHED_RESULT',json.dumps(summary),flush=True)
