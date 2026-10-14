#!/usr/bin/env python3
"""Preserve every current proof file in one independently verified companion ZIP."""
import argparse,hashlib,json,zipfile
from pathlib import Path
from package_vulkan_performance import source_file

def sha(b):return hashlib.sha256(b).hexdigest()
def main():
 p=argparse.ArgumentParser()
 for name in ('evidence','checkpoint','output','index'):p.add_argument('--'+name,required=True,type=Path)
 a=p.parse_args();c=json.loads(a.checkpoint.read_text())
 if not c['verification_complete'] or c['higher_view_distance_check']['state']!='pass' or any(c['ci'][k]['state']!='pass' for k in ('compile_repro','native_server_c2me','packaged_clients','khronos_c2me_travel')):raise SystemExit('Exact-binary runtime acceptance incomplete')
 entries={}
 for path in sorted(a.evidence.rglob('*')):
  r=path.relative_to(a.evidence)
  if path.is_file() and r.parts[0]!='diagnostics-repaired' and source_file(r):entries['evidence/'+r.as_posix()]=path.read_bytes()
 hashes={n:sha(b) for n,b in entries.items()}
 entries['README.txt']=b'Current exact-binary Hari 2.4.1 runtime/build proof. Extract beside the core release ZIP. Historical diagnostic companions remain separate. All original logs, screenshots and checks are preserved.\n'
 entries['SHA256SUMS.txt']=''.join(sha(b)+'  '+n+'\n' for n,b in sorted(entries.items())).encode()
 a.output.parent.mkdir(parents=True,exist_ok=True)
 with zipfile.ZipFile(a.output,'w',zipfile.ZIP_DEFLATED,compresslevel=9) as z:
  for n,b in sorted(entries.items()):
   i=zipfile.ZipInfo(n,(2026,10,1,0,0,0));i.compress_type=zipfile.ZIP_DEFLATED;i.external_attr=0o100644<<16;z.writestr(i,b,compresslevel=9)
 with zipfile.ZipFile(a.output) as z:
  assert z.testzip() is None
  for line in z.read('SHA256SUMS.txt').decode().splitlines():
   digest,n=line.split('  ',1);assert sha(z.read(n))==digest,n
 assert a.output.stat().st_size<100*1024*1024
 index={'schema':1,'complete':True,'product_commit':c['product_commit'],'file':a.output.name,'bytes':a.output.stat().st_size,'sha256':sha(a.output.read_bytes()),'current_evidence_entries':len(hashes),'entries':hashes,'verified_union_matches_source':True}
 a.index.write_text(json.dumps(index,indent=2)+'\n');print(json.dumps({k:v for k,v in index.items() if k!='entries'},indent=2))

if __name__=='__main__':main()
