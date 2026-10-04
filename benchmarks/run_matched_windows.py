from pathlib import Path
import json,shutil,hashlib,subprocess,time,datetime,argparse,re
W=Path(__file__).resolve().parents[1];D=Path('C:/Users/Owner/Desktop/Minecraft Vulkan Hybrid/benchmarks')
parser=argparse.ArgumentParser();parser.add_argument('--run-name',default='windows-'+datetime.datetime.now(datetime.timezone.utc).strftime('%Y%m%dT%H%M%SZ'));args=parser.parse_args()
assert re.fullmatch(r'[A-Za-z0-9_-]+',args.run_name),'Run name must be a directory name, not a path'
R=D/args.run_name;R.mkdir(exist_ok=True)
instances=json.loads((D/'instances.json').read_text());by={('baseline' if 'baseline' in r['name'] else 'candidate'):r for r in instances}
exe=D/'tools/mvh_instance_cli.exe';pristine=D/'pristine-world'
def owned(p):
 p=p.resolve();assert any(p==Path(r['path']).resolve() or Path(r['path']).resolve() in p.parents for r in instances),str(p)
 assert (Path(next(r['path'] for r in instances if p==Path(r['path']).resolve() or Path(r['path']).resolve() in p.parents))/'MVH-BENCHMARK-IDENTITY.json').exists()
 return p
def archive_owned(src,dst):
 src=owned(src);dst=dst.resolve();assert D.resolve() in dst.parents and not dst.exists();dst.parent.mkdir(parents=True,exist_ok=True);shutil.move(str(src),str(dst))
def tree_manifest(p):return {str(f.relative_to(p)):hashlib.sha256(f.read_bytes()).hexdigest() for f in p.rglob('*') if f.is_file()}
if not pristine.exists():
 source=owned(Path(by['baseline']['path'])/'saves/MVH-Benchmark-20261004');assert (source/'region').is_dir(),'Preparatory run must finish and save its world first'
 report=json.loads((source.parent.parent/'harimt-fps-last.json').read_text());assert report['renderer']=='VULKAN' # Preparatory capture is discarded; world was generated and saved
 import msvcrt
 with (source/'session.lock').open('r+b') as lock:
  msvcrt.locking(lock.fileno(),msvcrt.LK_NBLCK,1);msvcrt.locking(lock.fileno(),msvcrt.LK_UNLCK,1) # Refuse a live owned world
 shutil.copytree(source,pristine)
(R/'pristine-world-sha256.json').write_text(json.dumps(tree_manifest(pristine),indent=2))
for n,kind in enumerate(['baseline','candidate','candidate','baseline','baseline','candidate'],1):
 label=f'{n:02d}-{kind}';out=R/label
 if out.exists() and (out/'fps.json').exists():continue
 out.mkdir(exist_ok=True);record=by[kind];p=owned(Path(record['path']));world=p/'saves/MVH-Benchmark-20261004'
 if world.exists():archive_owned(world,D/'archived-test-worlds'/args.run_name/label)
 shutil.copytree(pristine,world);assert tree_manifest(world)==tree_manifest(pristine)
 for name in ['harimt-fps-last.json','options.txt']:
  if name=='harimt-fps-last.json' and (p/name).exists():archive_owned(p/name,out/'previous-fps.json')
  if name=='options.txt':shutil.copy2(p/name,out/name)
 for shot in (p/'screenshots').glob('*.png'):archive_owned(shot,D/'archived-screenshots'/args.run_name/label/shot.name)
 mods=[{'name':f.name,'sha256':hashlib.sha256(f.read_bytes()).hexdigest()} for f in (p/'mods').glob('*.jar')]
 (out/'inputs.json').write_text(json.dumps({'kind':kind,'loader':'47.4.26','minecraft':'1.20.1','java':'17.0.20.1','jvm':['-Dorg.lwjgl.system.stackSize=1024','-Dharimt.fps.auto=true','-Dharimt.fps.warmupSeconds=60'],'maximum_heap_mib':8192,'mods':mods},indent=2))
 procs=subprocess.run(['powershell','-NoProfile','-Command','Get-Process | Select-Object ProcessName,Id,CPU | ConvertTo-Json -Compress'],capture_output=True,text=True);(out/'concurrent-processes.json').write_text(procs.stdout)
 start=int(time.time()*1000);print('START',label,flush=True)
 with (out/'launcher.log').open('w') as log,(out/'gpu.csv').open('w') as gpu:
  gpu.write('epoch_ms,timestamp,gpu_percent,memory_percent,memory_mib,power_w,temperature_c,graphics_mhz\n')
  proc=subprocess.Popen([str(exe),'launch',record['id']],stdout=log,stderr=log,cwd=W)
  while proc.poll() is None:
   tick=time.monotonic();q=subprocess.run(['nvidia-smi','--query-gpu=timestamp,utilization.gpu,utilization.memory,memory.used,power.draw,temperature.gpu,clocks.gr','--format=csv,noheader,nounits'],capture_output=True,text=True)
   gpu.write(str(int(time.time()*1000))+','+q.stdout.strip()+'\n');gpu.flush()
   if time.time()*1000-start>360000:raise RuntimeError('Benchmark client exceeded six minutes; leave scoped process for diagnosis')
   time.sleep(max(0,1-(time.monotonic()-tick)))
 if not (p/'harimt-fps-last.json').exists():raise RuntimeError('No real FPS report after game exit: '+label)
 f=json.loads((p/'harimt-fps-last.json').read_text());shutil.copy2(p/'harimt-fps-last.json',out/'fps.json');assert f['status']=='complete',(label,f.get('invalid_reason'));assert f['renderer']=='VULKAN';assert f['position']==[0.5,110.5,0.5];assert abs(f['yaw']+68.7007)<0.001 and abs(f['pitch']-9.999512)<0.001,'Camera mismatch invalidates comparison'
 text=(p/'logs/latest.log').read_text(errors='replace');lines=[l for l in text.splitlines() if not any(x in l for x in ['arguments','accessToken','xuid','username','uuid','Setting user:','logged in with entity','joined the game','left the game'])];(out/'minecraft-sanitized.log').write_text('\n'.join(lines)+'\n',encoding='utf-8')
 for shot in (p/'screenshots').glob('*.png'):shutil.copy2(shot,out/shot.name)
 print('DONE',label,'FPS',round(f['average_fps'],2),'1%LOW',round(f['one_percent_low_fps'],2),flush=True)
print('ALL_SIX_TRIALS_COMPLETE',flush=True)
