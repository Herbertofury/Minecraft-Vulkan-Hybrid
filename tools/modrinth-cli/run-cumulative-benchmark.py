from pathlib import Path
import argparse,datetime,hashlib,json,msvcrt,psutil,re,shutil,sqlite3,subprocess,time,os,ctypes
D=Path('C:/Users/Owner/Desktop/Minecraft Vulkan Hybrid/benchmarks');N=D/'cumulative';ap=argparse.ArgumentParser();ap.add_argument('--stage',required=True);ap.add_argument('--zink',action='store_true');ap.add_argument('--profile',action='store_true');ap.add_argument('--grass-smoke',action='store_true');ap.add_argument('--owned-gpu',action='store_true');ap.add_argument('--export-palette',action='store_true');ap.add_argument('--heap-diagnostic',action='store_true');ap.add_argument('--chunk-purpose',action='store_true');ap.add_argument('--zink-descriptors',choices=['auto','lazy','db'],default='auto');ap.add_argument('--repeat',type=int,default=1);a=ap.parse_args();assert re.fullmatch('[A-Za-z0-9_-]+',a.stage);assert 1<=a.repeat<=6;assert not a.owned_gpu or (a.profile and not a.zink);assert not a.grass_smoke or (a.repeat==1 and not a.profile and not a.zink and not a.export_palette);assert not a.heap_diagnostic or (not a.profile and not a.zink and not a.grass_smoke and not a.export_palette and not a.owned_gpu);assert not a.chunk_purpose or (not a.profile and not a.zink and not a.grass_smoke and not a.export_palette and not a.owned_gpu and not a.heap_diagnostic)
R=N/'runs'/a.stage;R.mkdir(parents=True,exist_ok=True);row=json.loads((N/'instance.json').read_text());rows=[row];by={'cumulative':row}
assert row['id']=='local:b6985be9-ded6-4a96-85cd-5a2548b1d900'
P=D/'pristine-world';exe=D/'tools/mvh_pack_cli.exe';opts=N/'matched-options.txt';worldname='MVH-Benchmark-20261004';db=Path('C:/Users/Owner/AppData/Roaming/ModrinthApp/app.db')

def manifest(p):return {f.relative_to(p).as_posix():hashlib.file_digest(f.open('rb'),'sha256').hexdigest() for f in p.rglob('*') if f.is_file()}
def own(p):
 p=p.resolve();root=next((Path(r['path']).resolve() for r in rows if p==Path(r['path']).resolve() or Path(r['path']).resolve() in p.parents),None);assert root and json.loads((root/'MVH-CUMULATIVE-IDENTITY.json').read_text())=={'task':'mvh-cumulative-20261005','id':row['id'],'source':'local:2c68d7c5-8fae-4984-ad3e-c76db50b66c3'};return p
def archive(p,q):
 p=own(p);q=q.resolve();assert N.resolve() in q.parents and not q.exists();q.parent.mkdir(parents=True,exist_ok=True);shutil.move(str(p),str(q))
def processes(id):
 with sqlite3.connect(f'file:{db.as_posix()}?mode=ro',uri=True) as c:return c.execute('select pid,start_time from processes where instance_id=?',(id,)).fetchall()
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

def redact_player_identifiers(line):
 line=re.sub(r"(?i)(for player\s+)[A-Za-z0-9_]{1,16}",r"\1[player]",line)
 line=re.sub(r"\b[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}\b|\b[0-9a-fA-F]{32}\b","[identifier]",line)
 return line

def retain_failed_log(profile,out):
 text=(profile/'logs/latest.log').read_text(errors='replace');lines=[redact_player_identifiers(x) for x in text.splitlines() if not re.search('arguments|accesstoken|xuid|username|uuid|setting user:|logged in with entity|joined the game|left the game|session id|bearer|authorization',x,re.I)];(out/'minecraft-sanitized.log').write_text('\n'.join(lines)+'\n',encoding='utf8')

expected=manifest(P);original=json.loads((D/'windows-20261004/pristine-world-sha256.json').read_text());assert expected=={k.replace(chr(92),'/'):v for k,v in original.items()};(R/'world-input-sha256.json').write_text(json.dumps(expected,indent=2))
for num,kind in enumerate(['cumulative']*a.repeat,1):
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
 out.mkdir();world=p/'saves'/worldname
 if world.exists():
  with (world/'session.lock').open('r+b') as lock:msvcrt.locking(lock.fileno(),msvcrt.LK_NBLCK,1);msvcrt.locking(lock.fileno(),msvcrt.LK_UNLCK,1)
  archive(world,N/'archived-worlds'/a.stage/label)
 shutil.copytree(P,world);assert manifest(world)==expected
 if (p/'mvh-pack-fps.json').exists():archive(p/'mvh-pack-fps.json',out/'previous-fps.json')
 if a.grass_smoke and (p/'mvh-grass-native-smoke.json').exists():archive(p/'mvh-grass-native-smoke.json',out/'previous-grass-smoke.json')
 for modefile in ['mvh-chunk-purpose-request.json','mvh-chunk-purpose.json']:
  if (p/modefile).exists():archive(p/modefile,out/('previous-'+modefile))
 if a.chunk_purpose:
  assert not any(f.name=='r.32.32.mca' for f in P.rglob('*.mca')),'Chunk purpose target region was already generated in pristine input'
  (p/'mvh-chunk-purpose-request.json').write_text(json.dumps({'task':'mvh-cumulative-chunk-purpose-20261005','origin_x':1024,'origin_z':1024,'side':12})+'\n')
 for shot in (p/'screenshots').glob('*.png'):archive(shot,N/'archived-screenshots'/a.stage/label/shot.name)
 shutil.copy2(opts,p/'options.txt');shutil.copy2(opts,out/'options.txt')
 mods=[{'name':f.name,'sha256':hashlib.file_digest(f.open('rb'),'sha256').hexdigest()} for f in sorted((p/'mods').glob('*.jar'))]
 visual={}
 for line in opts.read_text().splitlines():
  key,_,v=line.partition(':')
  if key in ['renderDistance','simulationDistance','graphicsMode','mipmapLevels','entityDistanceScaling','biomeBlendRadius','particles','fov','maxFps','enableVsync','pauseOnLostFocus','resourcePacks']:visual[key]=v
 inputs={'instrumented_diagnostic':a.profile or a.export_palette or a.grass_smoke or a.heap_diagnostic or a.chunk_purpose,'zink_translation_requested':a.zink,'kind':kind,'stage':a.stage,'renderer_experiment':'actual renderer recorded in fps.json or native smoke report','minecraft':'1.20.1','forge':'47.4.26','java':'Temurin 17.0.20.1','maximum_heap_mib':8192,'jvm':['-Dorg.lwjgl.system.stackSize=1024'],'resolution':[1920,1080],'visual_options':visual,'world':worldname,'warmup_s':60,'capture_s':30,'mods':mods,'config_sha256':manifest(p/'config')}
 if a.chunk_purpose:
  snapshot=out/'config-input-private';snapshot.mkdir()
  for cfg in (p/'config').rglob('*'):
   if cfg.is_file() and not cfg.is_symlink() and not any(part in cfg.name.lower() for part in ['regcode','credential','account','token']):
    relative=cfg.relative_to(p/'config');target=snapshot/relative;target.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(cfg,target)
 if a.grass_smoke:inputs.update({'scenario':'Native grass reload/animation/trail/dimension smoke; no FPS acceptance','warmup_s':None,'capture_s':None});inputs['jvm'].append('-Dmvh.pack.grass.smoke=true')
 if a.chunk_purpose:inputs.update({'scenario':'Fixed originally ungenerated 144 chunk purpose measurement; no FPS accepted','warmup_s':20,'capture_s':None})
 if a.zink:
  mesa=Path('C:/Users/Owner/Documents/Codex/2026-10-04/task-4/mesa-zink-install/bin');pins={'opengl32.dll':'beddcbf6eebdefc58c4b9de6157d2dec8d3133867ed2bc72229aa095218b6523','libgallium_wgl.dll':'675bbd608cf6e043b325edaa9dbe8b5e80466f34a16188fc90df3daacb9b84da','z-1.dll':'0917ecf9b3f1081e31721d4c39b82e6ad3fe694244ec76b5936c4b21e76f70e5'}
  hashes={name:hashlib.file_digest((mesa/name).open('rb'),'sha256').hexdigest() for name in pins};assert hashes==pins,'Translation driver changed after verification';inputs['mesa_driver_sha256']=hashes;inputs['zink_descriptors']=a.zink_descriptors;inputs['jvm'].append('Verified local MesaBridgeAgent with Mesa 26.2.4 presentation patch; per-child GALLIUM_DRIVER=zink and private shader cache')
 if a.profile:inputs['jvm'].append('StartFlightRecording: private sanitized settings, dumponexit=true; diagnostic timing excluded from promotion comparisons')
 if a.heap_diagnostic:inputs['heap_diagnostic']='Owned JDK GC.class_histogram after 25 seconds of actual stable scene warmup. Managed live-object totals only; no object contents or heap dump; all FPS excluded from promotion.'
 (out/'inputs-private.json').write_text(json.dumps(inputs,indent=2));print('START',label,flush=True)
 start=int(time.time()*1000);owned_pids={};java_start=None;observed_screen=None;blocked_since=None;gpu_collector=None;heap_proc=None;heap_seen=None;heap_begin=None;heap_end=None;heap_handle=None
 with (out/'launcher-private.log').open('w') as log,(out/'gpu.csv').open('w') as gpu,(out/'cpu.csv').open('w') as cpu:
  gpu.write('epoch_ms,timestamp,gpu_percent,memory_percent,memory_mib,power_w,temperature_c,graphics_mhz\n');cpu.write('epoch_ms,system_cpu_percent,java_cpu_percent,java_rss_mib\n')
  childenv=os.environ.copy();command=('launch-cumulative-zink-profile' if a.profile else 'launch-cumulative-zink') if a.zink else ('launch-cumulative-profile' if a.profile else 'launch-cumulative')
  if a.grass_smoke:command='launch-cumulative-grass-smoke'
  if a.zink:
   childenv['GALLIUM_DRIVER']='zink';childenv['ZINK_DESCRIPTORS']=a.zink_descriptors;childenv['MESA_SHADER_CACHE_DIR']=str(N/'zink-shader-cache');childenv['PATH']=str(mesa)+os.pathsep+childenv.get('PATH','')
  if a.export_palette:
   assert a.zink and not a.profile;command='launch-cumulative-zink-export'
  launch=[str(exe),command]
  if a.profile and not a.zink:launch.append(a.stage+'-'+label)
  proc=subprocess.Popen(launch,stdout=log,stderr=log,cwd=str(D/'tools'),env=childenv)
  while proc.poll() is None:
   tick=time.monotonic();q=subprocess.run(['nvidia-smi','--query-gpu=timestamp,utilization.gpu,utilization.memory,memory.used,power.draw,temperature.gpu,clocks.gr','--format=csv,noheader,nounits'],capture_output=True,text=True);epoch=int(time.time()*1000);gpu.write(str(epoch)+','+q.stdout.strip()+'\n');gpu.flush()
   for pid,created in processes(row['id']):
    if pid not in owned_pids:
     try:
      jp=psutil.Process(pid);assert Path(jp.exe()).name.lower()=='javaw.exe' and abs(jp.create_time()-created)<3;owned_pids[pid]=jp;java_start=round(jp.create_time()*1000)
      if a.owned_gpu:
       assert gpu_collector is None
       counter=N/'diagnostics'/(a.stage+'-'+label+'-owned-gpu.csv');assert not counter.exists()
       gpu_collector=subprocess.Popen(['powershell.exe','-NoProfile','-NonInteractive','-WindowStyle','Hidden','-File',str(Path(__file__).resolve().parent/'Get-Owned-GpuCounters.ps1'),'-TaskJavaProcessId',str(pid),'-TaskJavaStartEpochMs',str(java_start),'-TaskOutputCsv',str(counter)],stdout=log,stderr=log,creationflags=subprocess.CREATE_NO_WINDOW)
     except psutil.NoSuchProcess:pass
   jpct=rss=0.
   for jp in owned_pids.values():
    try:jpct+=jp.cpu_percent();rss+=jp.memory_info().rss/1048576
    except psutil.NoSuchProcess:pass
   cpu.write(f'{epoch},{psutil.cpu_percent()},{jpct},{rss}\n');cpu.flush()
   if a.heap_diagnostic:
    game_log=p/'logs/latest.log'
    if heap_seen is None and java_start is not None and game_log.exists() and game_log.stat().st_mtime*1000>=start and '[MVH Pack] stable scene warmup started' in game_log.read_text(errors='replace'):heap_seen=time.monotonic()
    if heap_seen is not None and time.monotonic()-heap_seen>=25 and heap_proc is None:
     live=processes(row['id']);assert len(live)==1,'Heap diagnostic requires exactly one marker-owned client'
     pid,created=live[0];assert pid in owned_pids and pid>0
     owned=owned_pids[pid];assert owned.is_running() and abs(owned.create_time()-created)<3 and Path(owned.exe()).name.lower()=='javaw.exe'
     jcmd=Path('C:/Program Files/Eclipse Adoptium/jdk-17.0.20.101-hotspot/bin/jcmd.exe');assert jcmd.is_file() and Path(owned.exe()).resolve().parent==jcmd.resolve().parent
     heap_handle=(out/'heap-histogram-private.txt').open('w');heap_begin=epoch
     heap_proc=subprocess.Popen([str(jcmd),str(pid),'GC.class_histogram'],stdout=heap_handle,stderr=heap_handle,creationflags=subprocess.CREATE_NO_WINDOW)
     print('OWNED_LIVE_HEAP_DIAGNOSTIC_STARTED',label,flush=True)
    if heap_proc is not None and heap_end is None:
     if heap_proc.poll() is not None:
      heap_handle.close();assert heap_proc.returncode==0,'Owned heap histogram failed'
      heap_end=int(time.time()*1000);print('OWNED_LIVE_HEAP_DIAGNOSTIC_FINISHED',label,flush=True)
     elif epoch-heap_begin>20000:
      helper=psutil.Process(heap_proc.pid);assert Path(helper.exe()).resolve()==jcmd.resolve();helper.terminate();heap_proc.wait(timeout=10);heap_handle.close();raise RuntimeError('Only owned jcmd helper exceeded 20 seconds; no memory/FPS acceptance')
   heartbeat=p/'mvh-load-state.json'
   if heartbeat.exists():
    state=json.loads(heartbeat.read_text())
    if state['epoch_ms']>=start and epoch-state['epoch_ms']<15000:
     screen=state['screen_class']
     if screen!=observed_screen:print('LOADING_SCREEN',label,screen,flush=True);observed_screen=screen;blocked_since=epoch
     if screen in ['net.minecraftforge.client.gui.LoadingErrorScreen','net.minecraft.client.gui.screens.TitleScreen'] and epoch-blocked_since>30000 and not state['client_level_present']:
      (out/'BLOCKED_LOADING_SCREEN.json').write_text(json.dumps({'state':state,'reason':'QuickPlay blocked for 30 seconds; no loading error is accepted automatically; unloaded owned client receives normal close'},indent=2));retain_failed_log(p,out);close_unloaded_owned_client(p,owned_pids);raise RuntimeError('Cumulative launch blocked: '+screen)
   if epoch-start>240000 and java_start is None:
    helper=psutil.Process(proc.pid);assert Path(helper.exe()).resolve()==exe.resolve();assert not any(Path(child.exe()).name.lower() in ['java.exe','javaw.exe'] for child in helper.children(recursive=True))
    (out/'REJECTED.json').write_text(json.dumps({'reason':'Backend did not start Java within four minutes; no FPS capture; only the verified owned helper is stopped','launcher_pid':proc.pid,'epoch_ms':epoch},indent=2));proc.terminate();proc.wait(timeout=15);raise RuntimeError('Modrinth backend stalled before Java; inspect retained phase diagnostics')
   if epoch-start>600000:
    retain_failed_log(p,out);raise RuntimeError('Owned test exceeded 10 minutes; inspect retained loading diagnostics')
   time.sleep(max(0,1-(time.monotonic()-tick)))
 assert proc.returncode==0,'Modrinth backend launch failed'
 if gpu_collector is not None:assert gpu_collector.wait(timeout=15)==0,'Owned GPU counter collector failed; retain its private diagnostics'
 if a.chunk_purpose:
  report=p/'mvh-chunk-purpose.json';assert report.exists(),'Chunk purpose exited without report'
  result=json.loads(report.read_text());shutil.copy2(report,out/'CHUNKS.json');retain_failed_log(p,out)
  for shot in (p/'screenshots').glob('*.png'):shutil.copy2(shot,out/shot.name)
  (out/'DONE-CHUNKS.json').write_text(json.dumps({'normal_helper_exit':True,'passed':result['passed'],'no_fps_accepted':True,'launcher_start_epoch_ms':start,'java_start_epoch_ms':java_start},indent=2)+'\n')
  print('CHUNK_PURPOSE_RESULT',result['passed'],'CHUNKS',result['verified_full_lit_chunks'],'SECONDS',result['generation_wall_s'],'RENDERER',result['api_renderer'],flush=True)
  assert result['passed'] and result['api_renderer']=='VULKAN' and result['verified_full_lit_chunks']==144 and result['generation_wall_s']>0 and not result['fps_accepted'],'Chunk purpose failed; retain evidence and diagnose'
  continue
 if a.grass_smoke:
  report=p/'mvh-grass-native-smoke.json';assert report.exists(),'Grass smoke exited without a report'
  result=json.loads(report.read_text());shutil.copy2(report,out/'GRASS-SMOKE.json');retain_failed_log(p,out)
  for shot in (p/'screenshots').glob('*.png'):shutil.copy2(shot,out/shot.name)
  (out/'DONE-SMOKE.json').write_text(json.dumps({'launcher_start_epoch_ms':start,'java_start_epoch_ms':java_start,'normal_helper_exit':True,'passed':result['passed'],'no_fps_accepted':True},indent=2))
  print('GRASS_SMOKE_RESULT',result['passed'],'STEP',result['step'],'RENDERER',result['api_renderer'],flush=True)
  assert result['passed'] and result['api_renderer']=='VULKAN','Grass smoke failed; retained report and screenshots require diagnosis'
  continue
 if not (p/'mvh-pack-fps.json').exists():
  retain_failed_log(p,out);(out/'REJECTED.json').write_text(json.dumps({'reason':'Game exited without a frame capture; retained loading/crash diagnostics; no accepted FPS','epoch_ms':int(time.time()*1000)},indent=2));raise RuntimeError('Game exited before benchmark; inspect retained diagnostics')
 f=json.loads((p/'mvh-pack-fps.json').read_text());assert f['status']=='complete' and f['frames']>1000;assert f['camera']=='0.5,110.5,0.5 / yaw -68.7007 / pitch 9.999512'
 if a.heap_diagnostic:
  assert heap_end is not None and heap_end<f['capture_epoch_ms']-10000,'Heap diagnostic must finish with at least 10 seconds recovery before capture'
  histogram=(out/'heap-histogram-private.txt').read_text();matches=re.findall(r'^Total\s+(\d+)\s+(\d+)\s*$',histogram,re.M);assert len(matches)==1,'No unambiguous live-object totals'
  objects,livebytes=map(int,matches[0]);assert objects>0 and 0<livebytes<=8192*1048576
  metrics={'status':'complete','command':'GC.class_histogram default live objects','managed_live_objects':objects,'managed_live_bytes':livebytes,'managed_live_mib':livebytes/1048576,'diagnostic_duration_ms':heap_end-heap_begin,'capture_recovery_s':(f['capture_epoch_ms']-heap_end)/1000,'actual_renderer':f['api_renderer'],'only_marker_owned_client_targeted':True,'object_contents_or_heap_dump_collected':False,'fps_promotion_accepted':False,'scope':'Managed Java live-object histogram, not native memory, VRAM or working set. Intrusive diagnostic run; timing excluded from performance promotion.'}
  (out/'HEAP.json').write_text(json.dumps(metrics,indent=2)+'\n');print('OWNED_LIVE_HEAP_MIB',round(metrics['managed_live_mib'],2),flush=True)
 shutil.copy2(p/'mvh-pack-fps.json',out/'fps.json');text=(p/'logs/latest.log').read_text(errors='replace');lines=[redact_player_identifiers(x) for x in text.splitlines() if not re.search('arguments|accesstoken|xuid|username|uuid|setting user:|logged in with entity|joined the game|left the game|session id|bearer|authorization',x,re.I)];(out/'minecraft-sanitized.log').write_text('\n'.join(lines)+'\n',encoding='utf8')
 startup=re.findall(r'Total time to load game and open world was ([\d.]+) seconds',text);worldtime=re.findall(r'Time from main menu to in-game was ([\d.]+) seconds',text)
 timing={'launcher_start_epoch_ms':start,'java_start_epoch_ms':java_start,'scene_ready_epoch_ms':f['capture_epoch_ms']-60000,'modernfix_game_to_world_s':float(startup[-1]) if startup else None,'modernfix_menu_to_world_s':float(worldtime[-1]) if worldtime else None,'cache_state':'Repeated local launch; OS/filesystem caches retained; not a cold boot'}
 timing['launcher_to_scene_s']=(timing['scene_ready_epoch_ms']-start)/1000;timing['java_to_scene_s']=(timing['scene_ready_epoch_ms']-java_start)/1000 if java_start else None
 for shot in (p/'screenshots').glob('*.png'):shutil.copy2(shot,out/shot.name)
 critical=len(re.findall('RunningOnDifferentThreadException|Exception ticking world|ModelMissingException|Mixin apply failed',text));(out/'PLAYABILITY.json').write_text(json.dumps({'critical_error_occurrences':critical,'accepted':critical==0,'scope':'Automated scene capture and normal save/exit; extended play tests separate'}));
 (out/'DONE.json').write_text(json.dumps(timing,indent=2));print('DONE',label,'RENDERER',f['api_renderer'],'FPS',round(f['average_fps'],2),'1%LOW',round(f['one_percent_low_fps'],2),'STARTUP',timing['modernfix_game_to_world_s'],flush=True)
print('CUMULATIVE_STAGE_FINISHED',a.stage,flush=True)
