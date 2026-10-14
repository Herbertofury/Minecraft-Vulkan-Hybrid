#!/usr/bin/env python3
"""Preserve all historical evidence in independently readable ZIPs below the upload limit."""
import argparse, hashlib, json, zipfile, zlib
from pathlib import Path

PREFIX = 'evidence/diagnostics-repaired/'

def sha(data):return hashlib.sha256(data).hexdigest()

def write_zip(path, entries):
    manifest=''.join(sha(data)+'  '+name+'\n' for name,data in sorted(entries.items())).encode()
    with zipfile.ZipFile(path,'w',zipfile.ZIP_DEFLATED,compresslevel=9) as z:
        for name,data in sorted({**entries,'SHA256SUMS.txt':manifest}.items()):
            info=zipfile.ZipInfo(name,(2026,10,1,0,0,0));info.compress_type=zipfile.ZIP_DEFLATED;info.external_attr=0o100644<<16
            z.writestr(info,data,compresslevel=9)
    with zipfile.ZipFile(path) as z:
        assert z.testzip() is None
        for line in z.read('SHA256SUMS.txt').decode().splitlines():
            digest,name=line.split('  ',1);assert sha(z.read(name))==digest,name

def main():
    p=argparse.ArgumentParser();p.add_argument('--diagnostics',required=True,type=Path);p.add_argument('--output-dir',required=True,type=Path);p.add_argument('--index',required=True,type=Path);p.add_argument('--product-commit',required=True);args=p.parse_args()
    limit=90*1024*1024;args.output_dir.mkdir(parents=True,exist_ok=True)
    # Reserve a MiB for manifests and ZIP metadata, after measuring each entry's actual deflate size.
    files=[x for x in sorted(args.diagnostics.rglob('*')) if x.is_file()]
    volumes=[];current={};size=0;source_hashes={}
    for path in files:
        name=PREFIX+path.relative_to(args.diagnostics).as_posix();data=path.read_bytes()
        estimate=len(zlib.compress(data,9))+2*len(name.encode())+110
        if estimate>limit-1024*1024:raise SystemExit('A diagnostic entry needs a larger-file strategy: '+name)
        if current and size+estimate>limit-1024*1024:volumes.append(current);current={};size=0
        current[name]=data;source_hashes[name]=sha(data);size+=estimate
    if current:volumes.append(current)
    receipts=[];readback_hashes={}
    for i,entries in enumerate(volumes,1):
        filename=f'Hari-2.4.1-historical-diagnostics-{i:02d}.zip';path=args.output_dir/filename
        entries['README.txt']=('Historical diagnostics for Hari 2.4.1. This volume is part of the complete release collection.\n'
                             'Every old failure, screenshot and log is retained for provenance, not current acceptance.\n'
                             'Use COMPANION-ARCHIVES.json in the core archive to verify the complete collection.\n').encode()
        write_zip(path,entries);assert path.stat().st_size<limit
        mapped={name:sha(data) for name,data in entries.items() if name.startswith(PREFIX)}
        for name,digest in mapped.items():
            assert name not in readback_hashes;readback_hashes[name]=digest
        receipts.append(dict(file=filename,bytes=path.stat().st_size,sha256=sha(path.read_bytes()),diagnostic_entries=len(mapped),entries=mapped))
    assert source_hashes==readback_hashes,'diagnostic collection lost or changed an entry'
    index=dict(schema=1,product_commit=args.product_commit,complete=True,all_source_diagnostic_entries=len(source_hashes),
               historical_evidence_only=True,volumes=receipts,verified_union_matches_source=True)
    args.index.parent.mkdir(parents=True,exist_ok=True);args.index.write_text(json.dumps(index,indent=2)+'\n')
    print(json.dumps({**index,'volumes':[{k:v for k,v in x.items() if k!='entries'} for x in receipts]},indent=2))

if __name__=='__main__':main()
