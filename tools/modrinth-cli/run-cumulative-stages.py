from pathlib import Path
import argparse,json,subprocess,sys,statistics,hashlib,sqlite3,psutil,shutil,datetime
W=Path(__file__).resolve().parent;D=Path('C:/Users/Owner/Desktop/Minecraft Vulkan Hybrid/benchmarks');C=D/'cumulative';CLI=D/'tools/mvh_pack_cli.exe';P=json.loads((C/'instance.json').read_text());ROOT=Path(P['path']);DB=Path('C:/Users/Owner/AppData/Roaming/ModrinthApp/app.db');LIVE=Path('C:/Users/Owner/AppData/Roaming/ModrinthApp/profiles/Noxviola')
ap=argparse.ArgumentParser();ap.add_argument('--start',type=int,default=2);ap.add_argument('--end',type=int,default=15);ap.add_argument('--setup-config',action='store_true');a=ap.parse_args();assert 2<=a.start<=a.end<=351
assert json.loads((ROOT/'MVH-CUMULATIVE-IDENTITY.json').read_text())=={'task':'mvh-cumulative-20261005','id':P['id'],'source':'local:2c68d7c5-8fae-4984-ad3e-c76db50b66c3'};assert ROOT.resolve()!=LIVE.resolve()
def inactive():
 with sqlite3.connect(f'file:{DB.as_posix()}?mode=ro',uri=True) as db:assert all(not psutil.pid_exists(r[0]) for r in db.execute('select pid from processes where instance_id=?',(P['id'],)))
def run(stage,repeats):
 subprocess.run([sys.executable,'-X','utf8',str(W/'run-cumulative-benchmark.py'),'--stage',stage,'--repeat',str(repeats)],cwd=W,check=True)
 return [json.loads(p.read_text()) for p in sorted((C/'runs'/stage).glob('*/fps.json'))]
def private_file(f):return any(x in f.name.lower() for x in ['regcode','credential','account','token']) or '.enderloom-workbench' in f.parts
def manifest(root):return {f.relative_to(root).as_posix():hashlib.file_digest(f.open('rb'),'sha256').hexdigest() for f in root.rglob('*') if f.is_file() and not private_file(f)}
def med(frames):return statistics.median(f['average_fps'] for f in frames)
def record_frames(record):
 if 'effective_trial_dirs' in record:return [json.loads((C/'runs'/p/'fps.json').read_text()) for p in record['effective_trial_dirs']]
 return [json.loads(p.read_text()) for p in sorted((C/'runs'/record.get('effective_run_stage',record['stage'])).glob('*/fps.json'))]

if a.setup_config:
 inactive();backup=C/'config-before-original-pack';assert not backup.exists();shutil.copytree(ROOT/'config',backup);source=LIVE/'config';before=manifest(source)
 for f in source.rglob('*'):
  if f.is_file() and not private_file(f):
   assert not f.is_symlink();relative=f.relative_to(source);target=ROOT/'config'/relative;target.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(f,target)
 assert before==manifest(source),'Source configs changed during snapshot'
 e=ROOT/'config/entityculling.json'
 if e.exists():
  j=json.loads(e.read_text());j['entityWhitelist']+= [x for x in ['mts:builder_existing','mts:builder_rendering','mts:builder_seat'] if x not in j['entityWhitelist']];e.write_text(json.dumps(j,indent=2)+'\n')
 (C/'original-config-snapshot-private.json').write_text(json.dumps({'source_sha256':before,'owned_result_sha256':manifest(ROOT/'config'),'backup_sha256':manifest(backup)},indent=2)+'\n');run('001b-original-pack-config',3)
plan=json.loads((C/'plan.json').read_text());progress=C/'progress.json';records=json.loads(progress.read_text()) if progress.exists() else []
previous=[json.loads(p.read_text()) for p in sorted((C/'runs/001b-original-pack-config').glob('*/fps.json'))] or [json.loads(p.read_text()) for p in sorted((C/'runs/001-integrated-vanilla-base').glob('*/fps.json'))];assert len(previous)>=3
for prior in sorted(records,key=lambda r:int(r['stage'].split('-')[0])):
 if int(prior['stage'].split('-')[0])>=a.start:break
 assert prior['status'] in ['PASS_WITH_RECORDED_LIMITS','EXCLUDED_BY_USER','REJECTED_NATIVE_PERFORMANCE_MOD','DEFERRED_SHADER_COMPATIBILITY'],'An earlier unresolved stage blocks cumulative progression'
 previous=record_frames(prior);assert previous and all(x['api_renderer']=='VULKAN' for x in previous)
for row in plan['ordered']:
 if row['index']<a.start or row['index']>a.end:continue
 stage=row['stage'];completed=next((r for r in records if r['stage']==stage),None)
 if completed:
  assert completed['status'] in ['PASS_WITH_RECORDED_LIMITS','EXCLUDED_BY_USER','REJECTED_NATIVE_PERFORMANCE_MOD','DEFERRED_SHADER_COMPATIBILITY'],'Unresolved stage requires diagnosis';previous=record_frames(completed);continue
 inactive();assert not (C/'runs'/stage).exists(),'Existing failed/incomplete stage must be preserved and diagnosed'
 source=LIVE/'mods'/row['file'];patches={'cataclysm-to-magic-fix-1.1.0.jar':W/'inventory/noxviola/repairs/cataclysm-to-magic-fix-1.1.0-MVH-TransformerReturn-v1.jar','create-automotives-1.20.1-V2.jar':W/'inventory/noxviola/repairs/create-automotives-1.20.1-V2-MVH-BoilerboxModels-v2.jar'}
 source=patches.get(row['file'],source);digest=hashlib.file_digest(source.open('rb'),'sha256').hexdigest();assert source!=LIVE/'mods'/row['file'] or digest==row['sha256']
 inputs={'stage':stage,'add':[{'path':str(source),'sha256':digest}],'disable':[]};file=C/'stage-inputs'/(stage+'.json');assert not file.exists();file.write_text(json.dumps(inputs,indent=2)+'\n')
 print('ADDING',stage,'mod IDs',','.join(row['mods']),flush=True)
 with (C/(stage+'-add-private.log')).open('w') as log:subprocess.run([str(CLI),'add-cumulative',str(file)],stdout=log,stderr=log,cwd=W,check=True)
 try:frames=run(stage,1)
 except Exception:
  records.append({'stage':stage,'file':row['file'],'status':'FAILED_LAUNCH_OR_CAPTURE_REQUIRES_DIAGNOSIS'});progress.write_text(json.dumps(records,indent=2)+'\n');raise
 changed=frames[0]['api_renderer']!=previous[-1]['api_renderer'];loss=med(frames)<med(previous)*.92;below_target=med(frames)<2432
 if (loss or below_target) and not changed:frames=run(stage,3)
 critical=any(not json.loads(p.read_text())['accepted'] for p in (C/'runs'/stage).glob('*/PLAYABILITY.json'))
 status='RENDERER_TRANSITION_REQUIRES_COMPATIBILITY_WORK' if changed else ('REPRODUCED_DROP_REQUIRES_FIX' if med(frames)<med(previous)*.92 else ('PLAYABILITY_FAILURE_REQUIRES_FIX' if critical else ('TARGET_SHORTFALL_REQUIRES_DIAGNOSIS' if med(frames)<2432 else 'PASS_WITH_RECORDED_LIMITS')))
 record={'stage':stage,'file':row['file'],'mod_ids':row['mods'],'original_sha256':row['sha256'],'installed_sha256':digest,'previous_median_fps':med(previous),'median_fps':med(frames),'change_percent':(med(frames)/med(previous)-1)*100,'renderer':frames[-1]['api_renderer'],'repeats':len(frames),'status':status};records.append(record);progress.write_text(json.dumps(records,indent=2)+'\n');print('STAGE_RESULT',json.dumps(record),flush=True)
 if status!='PASS_WITH_RECORDED_LIMITS':print('PAUSED_FOR_DIAGNOSIS',stage,flush=True);break
 previous=frames
print('REQUESTED_CUMULATIVE_BATCH_FINISHED',flush=True)
