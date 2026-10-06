from pathlib import Path
import argparse,datetime,hashlib,json,msvcrt,psutil,re,shutil,sqlite3,subprocess,time,os,ctypes
D=Path('C:/Users/Owner/Desktop/Minecraft Vulkan Hybrid/benchmarks');N=D/'noxviola';ap=argparse.ArgumentParser();ap.add_argument('--run-name',default='matched-20261004');ap.add_argument('--candidate-only',action='store_true');ap.add_argument('--zink',action='store_true');ap.add_argument('--smoke',action='store_true');ap.add_argument('--options',type=Path);ap.add_argument('--hari-sha256');a=ap.parse_args();assert re.fullmatch('[A-Za-z0-9_-]+',a.run_name)
R=N/a.run_name;R.mkdir(exist_ok=True);rows=json.loads((N/'instances.json').read_text());by={('baseline' if 'baseline' in r['name'] else 'candidate'):r for r in rows}
assert {r['id'] for r in rows}=={'local:b8b62592-bf53-4cc8-a770-a07757e1f41a','local:9ec14df5-d028-4fc0-865e-f4d898af4409'}
P=N/'pristine-world';exe=D/'tools/mvh_pack_cli.exe';opts=a.options.resolve() if a.options else N/'matched-options.txt';worldname='MVH-Noxviola-Benchmark-20261004';db=Path('C:/Users/Owner/AppData/Roaming/ModrinthApp/app.db')
def manifest(p):return {f.relative_to(p).as_posix():hashlib.file_digest(f.open('rb'),'sha256').hexdigest() for f in p.rglob('*') if f.is_file()}
def own(p):
 p=p.resolve();root=next((Path(r['path']).resolve() for r in rows if p==Path(r['path']).resolve() or Path(r['path']).resolve() in p.parents),None);assert root and json.loads((root/'MVH-NOXVIOLA-IDENTITY.json').read_text())['task']=='mvh-noxviola-20261004';return p
def archive(p,q):
 p=own(p);q=q.resolve();assert N.resolve() in q.parents and not q.exists();q.parent.mkdir(parents=True,exist_ok=True);shutil.move(str(p),str(q))
def processes(id):
 with sqlite3.connect(f'file:{db.as_posix()}?mode=ro',uri=True) as c:return c.execute('select pid,start_time from processes where instance_id=?',(id,)).fetchall()
def retain_failed_log(profile,out):
 if not (profile/'logs/latest.log').exists():return
 content=(profile/'logs/latest.log').read_text(errors='replace')
 lines=[re.sub(r'\b[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}\b|\b[0-9a-fA-F]{32}\b','[identifier]',x) for x in content.splitlines() if not re.search('arguments|accesstoken|xuid|username|uuid|setting user:|logged in with entity|joined the game|left the game|session id|bearer|authorization',x,re.I)]
 (out/'minecraft-sanitized.log').write_text('\n'.join(lines)+'\n',encoding='utf8')
def close_unloaded_owned_client(profile,owned):
 state=json.loads((profile/'mvh-load-state.json').read_text());assert not state['client_level_present'] and not state['client_player_present'] and time.time()*1000-state['epoch_ms']<15000
 for pid,created in processes(row['id']):
  process=psutil.Process(pid);assert pid in owned and abs(process.create_time()-created)<3 and Path(process.exe()).name.lower()=='javaw.exe'
  u=ctypes.windll.user32;windows=[];callback=ctypes.WINFUNCTYPE(ctypes.c_bool,ctypes.c_void_p,ctypes.c_void_p)
  def observe(hwnd,_):
   q=ctypes.c_ulong();u.GetWindowThreadProcessId(ctypes.c_void_p(hwnd),ctypes.byref(q))
   if q.value==pid and u.IsWindowVisible(ctypes.c_void_p(hwnd)):windows.append(hwnd)
   return True
  u.EnumWindows(callback(observe),0);assert len(windows)==1 and process.is_running();assert u.PostMessageW(ctypes.c_void_p(windows[0]),0x0010,0,0)

expected=manifest(P);assert expected=={r['path']:r['sha256'] for r in json.loads((N/'pristine-world-sha256.json').read_text())};(R/'world-input-sha256.json').write_text(json.dumps(expected,indent=2))
for num,kind in enumerate(['candidate'] if a.candidate_only else ['baseline','candidate','candidate','baseline','baseline','candidate'],1):
 if (R/'STOP-AFTER-CURRENT').exists():print('STOP_REQUESTED_AFTER_CURRENT_TRIAL',flush=True);break
 label=f'{num:02d}-{kind}';out=R/label
 if (out/'DONE.json').exists():continue
 assert not out.exists(),'Preserve failed/incomplete run and use a new run name: '+label
 row=by[kind];p=own(Path(row['path']));stale=processes(row['id'])
 for pid,created in stale:
  assert not psutil.pid_exists(pid),'An owned or reused PID is active; diagnose it before running'
 if stale:
  (R/(label+'-stale-owned-processes.json')).write_text(json.dumps(stale))
  with sqlite3.connect(db) as c:
   for pid,created in stale:
    assert not psutil.pid_exists(pid);c.execute('delete from processes where pid=? and start_time=? and instance_id=?',(pid,created,row['id']))
 assert not processes(row['id'])
 out.mkdir()
 if (p/'config/harimt-native-required.properties').exists():
  assert a.candidate_only and kind=='candidate' and not a.zink,'Required native session excludes fallback comparisons/Zink'
  assert a.hari_sha256 and re.fullmatch('[0-9a-f]{64}',a.hari_sha256),'Supply the independently verified Hari SHA256 before native preflight'
  preflight=Path(__file__).resolve().parents[1]/'native-required-preflight.py'
  if not preflight.exists():preflight=Path(__file__).resolve().parent/'destination/tools/native-required-preflight.py'
  result=subprocess.run([__import__('sys').executable,str(preflight),str(p),'--hari-sha256',a.hari_sha256,'--report',str(out/'native-preflight.json')])
  assert result.returncode==0,'Native preflight blocked; no Minecraft launch, fallback, world/config replacement or error-screen loop'
 world=p/'saves'/worldname
 if world.exists():
  with (world/'session.lock').open('r+b') as lock:msvcrt.locking(lock.fileno(),msvcrt.LK_NBLCK,1);msvcrt.locking(lock.fileno(),msvcrt.LK_UNLCK,1)
  archive(world,N/'archived-worlds'/a.run_name/label)
 shutil.copytree(P,world);assert manifest(world)==expected
 if (p/'mvh-pack-fps.json').exists():archive(p/'mvh-pack-fps.json',out/'previous-fps.json')
 if a.smoke and (p/'mvh-native-smoke.json').exists():archive(p/'mvh-native-smoke.json',out/'previous-native-smoke.json')
 for shot in (p/'screenshots').glob('*.png'):archive(shot,N/'archived-screenshots'/a.run_name/label/shot.name)
 shutil.copy2(opts,p/'options.txt');shutil.copy2(opts,out/'options.txt')
 mods=[{'name':f.name,'sha256':hashlib.file_digest(f.open('rb'),'sha256').hexdigest()} for f in sorted((p/'mods').glob('*.jar'))]
 visual={}
 for line in opts.read_text().splitlines():
  key,_,v=line.partition(':')
  if key in ['renderDistance','simulationDistance','graphicsMode','mipmapLevels','entityDistanceScaling','biomeBlendRadius','particles','fov','maxFps','enableVsync','pauseOnLostFocus','resourcePacks']:visual[key]=v
 inputs={'kind':kind,'renderer_experiment':'Mesa Zink 26.2.4' if a.zink and kind=='candidate' else 'auto gated native Vulkan or OpenGL fallback; actual renderer recorded by game','minecraft':'1.20.1','forge':'47.4.26','java':'Temurin 17.0.20.1','maximum_heap_mib':12288,'jvm':['-Dorg.lwjgl.system.stackSize=1024'],'resolution':[1920,1080],'visual_options':visual,'world':worldname,'warmup_s':60,'capture_s':30,'mods':mods,'config_sha256':manifest(p/'config')}
 if a.zink and kind=='candidate':
  driver=json.loads((Path.cwd()/'destination/compat/opengl-over-vulkan/UPSTREAM.json').read_text())
  for record in driver['binaries']:
   dll=Path.cwd()/'mesa-zink-install/bin'/record['file']
   assert hashlib.file_digest(dll.open('rb'),'sha256').hexdigest()==record['sha256'], 'Unreviewed Zink DLL'
  inputs['zink_driver_build']=driver
 (out/'inputs-private.json').write_text(json.dumps(inputs,indent=2));print('START',label,flush=True)
 start=int(time.time()*1000);owned_pids={};java_start=None;observed_screen=None;blocked_since=None
 with (out/'launcher-private.log').open('w') as log,(out/'gpu.csv').open('w') as gpu,(out/'cpu.csv').open('w') as cpu:
  gpu.write('epoch_ms,timestamp,gpu_percent,memory_percent,memory_mib,power_w,temperature_c,graphics_mhz\n');cpu.write('epoch_ms,system_cpu_percent,java_cpu_percent,java_rss_mib\n')
  childenv=os.environ.copy();command='launch-smoke' if a.smoke else 'launch'
  if a.zink and kind=='candidate':
   command='launch-zink';childenv['GALLIUM_DRIVER']='zink';childenv['MESA_SHADER_CACHE_DIR']=str(N/'zink-minecraft-cache');childenv['PATH']=str(Path.cwd()/'mesa-zink-install/bin')+';'+childenv['PATH']
  proc=subprocess.Popen([str(exe),command,row['id']],stdout=log,stderr=log,cwd=str(D/'tools'),env=childenv)
  while proc.poll() is None:
   tick=time.monotonic();q=subprocess.run(['nvidia-smi','--query-gpu=timestamp,utilization.gpu,utilization.memory,memory.used,power.draw,temperature.gpu,clocks.gr','--format=csv,noheader,nounits'],capture_output=True,text=True);epoch=int(time.time()*1000);gpu.write(str(epoch)+','+q.stdout.strip()+'\n');gpu.flush()
   for pid,created in processes(row['id']):
    if pid not in owned_pids:
     try:
      jp=psutil.Process(pid);assert Path(jp.exe()).name.lower()=='javaw.exe' and abs(jp.create_time()-created)<3;owned_pids[pid]=jp;java_start=round(jp.create_time()*1000)
     except psutil.NoSuchProcess:pass
   jpct=rss=0.
   for jp in owned_pids.values():
    try:jpct+=jp.cpu_percent();rss+=jp.memory_info().rss/1048576
    except psutil.NoSuchProcess:pass
   cpu.write(f'{epoch},{psutil.cpu_percent()},{jpct},{rss}\n');cpu.flush()
   loadlog=p/'logs/latest.log'
   if java_start is not None and loadlog.exists() and loadlog.stat().st_mtime*1000>=start:
    loading=loadlog.read_text(errors='replace')
    if 'Error during pre-loading phase' in loading and 'Crash report saved to' in loading and 'stable scene warmup started' not in loading:
     retain_failed_log(p,out);(out/'PRELOADING_ERROR.json').write_text(json.dumps({'reason':'Forge pre-loading failed; no world loaded; normal close of exact owned client'}))
     for pid,jp in owned_pids.items():
      if not jp.is_running():continue
      assert Path(jp.exe()).name.lower()=='javaw.exe';u=ctypes.windll.user32;windows=[];callback=ctypes.WINFUNCTYPE(ctypes.c_bool,ctypes.c_void_p,ctypes.c_void_p)
      def observe(hwnd,_):
       q=ctypes.c_ulong();u.GetWindowThreadProcessId(ctypes.c_void_p(hwnd),ctypes.byref(q))
       if q.value==pid and u.IsWindowVisible(ctypes.c_void_p(hwnd)):windows.append(hwnd)
       return True
      u.EnumWindows(callback(observe),0);assert len(windows)==1;assert u.PostMessageW(ctypes.c_void_p(windows[0]),0x0010,0,0)
     raise RuntimeError('Forge pre-loading failed; isolated client closed normally')
   heartbeat=p/'mvh-load-state.json'
   if heartbeat.exists():
    try:state=json.loads(heartbeat.read_text())
    except json.JSONDecodeError:state={}
    if state.get('epoch_ms',0)>=start and epoch-state['epoch_ms']<15000:
     screen=state['screen_class']
     if screen!=observed_screen:print('LOADING_SCREEN',label,screen,flush=True);observed_screen=screen;blocked_since=epoch
     if screen in ['net.minecraftforge.client.gui.LoadingErrorScreen','net.minecraft.client.gui.screens.TitleScreen'] and epoch-blocked_since>30000 and not state['client_level_present']:
      (out/'BLOCKED_LOADING_SCREEN.json').write_text(json.dumps({'state':state,'reason':'QuickPlay blocked for30s; normal close of unloaded owned client; no warning/error bypass'},indent=2));retain_failed_log(p,out);close_unloaded_owned_client(p,owned_pids);raise RuntimeError('Full-pack launch blocked: '+screen)
   if epoch-start>240000 and java_start is None:
    (out/'REJECTED.json').write_text(json.dumps({'reason':'Backend did not start Java within4min; owned helper stopped','launcher_pid':proc.pid}));proc.terminate();proc.wait(timeout=15);raise RuntimeError('Modrinth backend stalled before Java')
   if epoch-start>900000:retain_failed_log(p,out);raise RuntimeError('Owned full pack exceeded15min; retain diagnosis')
   time.sleep(max(0,1-(time.monotonic()-tick)))
 retain_failed_log(p,out)
 assert proc.returncode==0,'Modrinth backend launch failed'
 assert (p/'mvh-pack-fps.json').exists(),'No frame capture; loading diagnostics retained'
 f=json.loads((p/'mvh-pack-fps.json').read_text());assert f['status']=='complete' and f['frames']>0 and f['duration_ms']>=29000;assert f['camera']=='0.5,110.5,0.5 / yaw -68.7007 / pitch 9.999512'
 if a.smoke:
  smoke=json.loads((p/'mvh-native-smoke.json').read_text());assert (p/'mvh-native-smoke.json').stat().st_mtime*1000>=start and smoke['completed'] and smoke['passed'] and len(smoke['checks'])==7,'Current full-pack smoke did not pass'
  shutil.copy2(p/'mvh-native-smoke.json',out/'native-smoke.json')
 shutil.copy2(p/'mvh-pack-fps.json',out/'fps.json');text=(p/'logs/latest.log').read_text(errors='replace');lines=[x for x in text.splitlines() if not re.search('arguments|accesstoken|xuid|username|uuid|setting user:|logged in with entity|joined the game|left the game|session id|bearer|authorization',x,re.I)];(out/'minecraft-sanitized.log').write_text('\n'.join(lines)+'\n',encoding='utf8')
 startup=re.findall(r'Total time to load game and open world was ([\d.]+) seconds',text);worldtime=re.findall(r'Time from main menu to in-game was ([\d.]+) seconds',text)
 timing={'launcher_start_epoch_ms':start,'java_start_epoch_ms':java_start,'scene_ready_epoch_ms':f['capture_epoch_ms']-60000,'modernfix_game_to_world_s':float(startup[-1]) if startup else None,'modernfix_menu_to_world_s':float(worldtime[-1]) if worldtime else None,'cache_state':'Repeated local launch; OS/filesystem caches retained; not a cold boot'}
 timing['launcher_to_scene_s']=(timing['scene_ready_epoch_ms']-start)/1000;timing['java_to_scene_s']=(timing['scene_ready_epoch_ms']-java_start)/1000 if java_start else None
 for shot in (p/'screenshots').glob('*.png'):shutil.copy2(shot,out/shot.name)
 critical=len(re.findall('RunningOnDifferentThreadException|Exception ticking world|ModelMissingException|Mixin apply failed',text));(out/'PLAYABILITY.json').write_text(json.dumps({'critical_error_occurrences':critical,'accepted':critical==0,'scope':'Automated scene capture and normal save/exit; extended play tests separate'}));
 (out/'DONE.json').write_text(json.dumps(timing,indent=2));print('DONE',label,'FPS',round(f['average_fps'],2),'1%LOW',round(f['one_percent_low_fps'],2),'STARTUP',timing['modernfix_game_to_world_s'],flush=True)
print('REQUESTED_FULL_PACK_TRIALS_FINISHED',flush=True)
