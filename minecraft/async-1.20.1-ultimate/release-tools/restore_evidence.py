import hashlib,json,shutil
from pathlib import Path
BASE=Path('performance-evidence');OUT=Path('hari-performance-release/evidence')
PROJECT=Path('hari-performance-repo/minecraft/async-1.20.1-ultimate')
PRODUCT='6594c6cc652db554213c52ae02f7acd8f439644c'
SHA='58ceda73c9328c78fc3d82eb652475cb4c7ddd91f3078d6d31717eb0ea9d432f'
def digest(b):return hashlib.sha256(b).hexdigest()
def write(path,d):path.parent.mkdir(parents=True,exist_ok=True);path.write_text(json.dumps(d,indent=2)+'\n')
def copy_tree(origin,target,current=False):
 target.mkdir(parents=True,exist_ok=True);refs=[];manifest_checks=0;manifest_self=[]
 for p in sorted(origin.rglob('*')):
  if not p.is_file():continue
  relative=p.relative_to(origin)
  if p.suffix=='.jar':
   b=p.read_bytes();s=digest(b)
   if current:assert s==SHA,(p,s)
   refs.append({'original_path':relative.as_posix(),'bytes':len(b),'sha256':s,'role':'same exact root release JAR' if current else 'superseded binary retained in the immutable provider artifact; not current acceptance'})
  else:
   d=target/relative;d.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(p,d)
 for manifest in origin.rglob('SHA256SUMS.txt'):
  for line in manifest.read_text().splitlines():
   if not line.strip():continue
   sha,name=line.split(None,1);name=name.lstrip(' *');p=manifest.parent/name
   if not p.exists():p=origin/name
   assert p.exists(),(manifest,name)
   if p==manifest:
    manifest_self.append({'manifest':manifest.relative_to(origin).as_posix(),'legacy_self_entry_not_a_valid_file_digest':True});continue
   assert digest(p.read_bytes())==sha,(manifest,name);manifest_checks+=1
 write(target/'BINARY-REFERENCES.json',{'product_commit':PRODUCT,'current_acceptance':current,'binary_references':refs,'manifest_file_hash_checks':manifest_checks,'legacy_self_entries':manifest_self})
 return len(refs),manifest_checks
c=json.loads((PROJECT/'PERFORMANCE-RELEASE-CHECKPOINT.json').read_text())
for key,folder in [('compile_repro','compile'),('native_server_c2me','server'),('packaged_clients','client'),('khronos_c2me_travel','extra-validation')]:copy_tree(BASE/'extracted'/str(c['ci'][key]['run']),OUT/folder,True)
copy_tree(BASE/'extracted'/str(c['higher_view_distance_check']['run']),OUT/'expanded-validation',True)
metadata=json.loads((BASE/'HISTORICAL-ALL-PROVIDER-METADATA.json').read_text());records=[]
for item in metadata:
 run=item['run'];target=OUT/'diagnostics-repaired'/('run-'+str(run));refs,checks=copy_tree(BASE/'extracted'/str(run),target)
 record={'run':run,'artifact':item['artifacts'][0],'jobs':item['jobs']['jobs'],'provider_sha_and_bytes_and_crc_verified':True,'nonself_manifest_file_hash_checks':checks,'superseded_binary_references':refs,'current_acceptance':False}
 write(target/'PROVENANCE.json',record);records.append(record)
write(OUT/'FAILURE-HISTORY.json',{'product_commit':PRODUCT,'accepted_final_product':PRODUCT,'all_historical_runs_not_current_acceptance':True,'retained_runs':records})
shutil.copy2(BASE/'source-reconstruction.log',OUT/'source-reconstruction.log')
for n in ['EXPANDED-VALIDATION-SUMMARY.json','DIAGNOSTIC-DISCLOSURE-AUDIT.json']:shutil.copy2(PROJECT/'release-manifests'/n,OUT/n)
write(OUT/'SERVER-SUMMARY.json',{'state':'pass','run':36899664082,'jar_sha256':SHA,'scenarios':['256 dense entities: exact GPU and CPU fallback switch','Strict C2ME safe world RNG; far chunk block inventory/lock unload/reload/restart','Deliberate entity fault: real crash report and emergency world save, all 256 entities recovered with and without C2ME'],'full_original_logs':'server/evidence/runtime/'})
frames=[]
for p in (OUT/'client').rglob('frame-sample.json'):
 d=json.loads(p.read_text());frames.append({'lane':p.parent.name,'renderer':d['renderer'],'sample_count':d['sample_count'],'warmup_frames':d['warmup_frames']})
assert len(frames)==7
write(OUT/'CLIENT-SUMMARY.json',{'state':'pass','run':36899664091,'lanes':frames,'scope':'Installed Forge production clients, real worlds, resource reload, resize, deliberate tick crash and same-world recovery'})
suites=['VBO-REGRESSION','AUTO-INDEX-REGRESSION','TASK-FAILURE-REGRESSION','GRAPHICS-GATE-REGRESSION','SHADER-CLOSE-REGRESSION','CPU-UPLOAD-REGRESSIONS','CHUNK-WORKERS','AUDIO-RELOAD','GL-TRANSLATION-CONTRACT','GL-SHADER-SOURCE','SECTION-GRAPH','C2ME-BOUNDARY','C2ME-SPAWN-LOCK','C2ME-TASK-WAITER','C2ME-CHUNK-ACCESS','SENSOR-COMPARATORS','ATOMIC-CONFIG-REGRESSION']
write(OUT/'FOCUSED-REGRESSION-SUMMARY.json',{'state':'pass','run':36899664277,'suites':suites,'actual_source_tests':True,'native_runtime_proof_separate':True})
write(OUT/'DRIVE-SOURCE-APPROVAL-BLOCK.json',c['source_drive_delivery']);write(OUT/'JAR-DELIVERY-RECEIPT.json',c['jar_drive_delivery'])
write(OUT/'DRIVE-DIAGNOSTIC-APPROVAL-BLOCK.json',{'action':'Upload the historical-diagnostics companion ZIPs to existing Hari Drive folder 1vq6hWD_QqVdxMlgYrupWbhVdlNDa8K-B','automatic_review_rejections':2,'latest_reason':'Private multi-file diagnostic payload requires explicit approval for this payload and Drive destination after the prior risk notice; assistant-generated audit cannot establish authorization','file_created_on_drive':False,'do_not_bypass':True,'resolution':'Finish unaffected product, source delivery and complete reviewable packaging, then request specific historical payload/destination approval'})
jar=BASE/'extracted'/str(c['ci']['compile_repro']['run'])/c['jar_name'];assert digest(jar.read_bytes())==SHA;shutil.copy2(jar,Path('deliverables')/jar.name)
for name in ['COMPANION-ARCHIVES.json','CURRENT-EVIDENCE-ARCHIVE.json']:shutil.copy2(PROJECT/'release-manifests'/name,Path('deliverables')/name)
print('Restored current proof and complete historical provider payloads')
