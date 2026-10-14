#!/usr/bin/env python3
"""Verify and package complete merged source with the immutable reconstruction recipe."""
import argparse, hashlib, json, zipfile
from pathlib import Path
from package_vulkan_performance import source_file

def sha(data):
    return hashlib.sha256(data).hexdigest()

def main():
    p=argparse.ArgumentParser()
    for name in ('source','project','evidence','checkpoint','output'):
        p.add_argument('--'+name,required=True,type=Path)
    args=p.parse_args()
    checkpoint=json.loads(args.checkpoint.read_text())
    proof=json.loads((args.evidence/'SOURCE-RECIPE-REPRODUCTION.json').read_text())
    if not proof['passed'] or proof['product_commit']!=checkpoint['product_commit']:
        raise SystemExit('Source recipe proof does not match the tested product')
    if checkpoint['ci']['compile_repro']['state']!='pass':
        raise SystemExit('Reproducible-build acceptance is incomplete')
    entries={}
    actual={}
    for path in sorted(args.source.rglob('*')):
        if path.is_file() and source_file(path.relative_to(args.source)):
            relative=path.relative_to(args.source).as_posix()
            data=path.read_bytes();actual[relative]=sha(data);entries['source/'+relative]=data
    if actual!=proof['source_hashes'] or len(actual)!=checkpoint['source_verification']['complete_source_files']:
        raise SystemExit('Complete source differs from the verified immutable reconstruction')
    for path in sorted(args.project.rglob('*')):
        relative=path.relative_to(args.project)
        # The source package carries code/recipe/docs. Runtime and historical proof remain in the complete evidence collection.
        if path.is_file() and source_file(relative) and relative.parts[0]!='evidence':
            entries['reconstruction/'+relative.as_posix()]=path.read_bytes()
    for directory in ('workflows','qa'):
        for path in sorted((args.evidence/directory).rglob('*')):
            if path.is_file() and source_file(path.relative_to(args.evidence)):
                entries['ci/'+path.relative_to(args.evidence).as_posix()]=path.read_bytes()
    entries['SOURCE-RECIPE-REPRODUCTION.json']=(args.evidence/'SOURCE-RECIPE-REPRODUCTION.json').read_bytes()
    entries['SOURCE-COMPLETENESS.json']=(args.evidence/'SOURCE-COMPLETENESS.json').read_bytes()
    entries['CHECKPOINT.json']=args.checkpoint.read_bytes()
    entries['README.txt']=(
        'Hari 2.4.1 Vulkan hybrid complete source — Minecraft 1.20.1 / Forge 47.4.23 / Java 17.\n'
        'source/ is the complete merged Gradle project, byte-verified against a fresh 23-step immutable reconstruction.\n'
        'Build: cd source; chmod +x gradlew; ./gradlew --no-daemon clean :forge:build --stacktrace\n'
        'Windows: cd source then gradlew.bat --no-daemon clean :forge:build --stacktrace\n'
        'reconstruction/ preserves the versioned recipe and licenses; ci/ contains executable build and runtime QA definitions.\n'
        'SOURCE-RECIPE-REPRODUCTION.json maps every source file to its SHA-256. SHA256SUMS.txt verifies every archive entry.\n'
        'This source package contains no runtime logs, screenshots, historical diagnostics or user worlds. Full proof is retained separately.\n'
        'The clean CI builds reproduce the exact release JAR recorded in CHECKPOINT.json.\n'
    ).encode()
    entries['SHA256SUMS.txt']=''.join(sha(data)+'  '+name+'\n' for name,data in sorted(entries.items())).encode()
    args.output.parent.mkdir(parents=True,exist_ok=True)
    with zipfile.ZipFile(args.output,'w',zipfile.ZIP_DEFLATED,compresslevel=9) as z:
        for name,data in sorted(entries.items()):
            info=zipfile.ZipInfo(name,(2026,10,1,0,0,0));info.compress_type=zipfile.ZIP_DEFLATED
            info.external_attr=(0o100755 if name.endswith('/gradlew') else 0o100644)<<16
            z.writestr(info,data,compresslevel=9)
    with zipfile.ZipFile(args.output) as z:
        if z.testzip() is not None:raise SystemExit('Source archive CRC failure')
        for line in z.read('SHA256SUMS.txt').decode().splitlines():
            digest,name=line.split('  ',1)
            if sha(z.read(name))!=digest:raise SystemExit('Source archive entry changed: '+name)
    print(json.dumps({'file':str(args.output.resolve()),'bytes':args.output.stat().st_size,'sha256':sha(args.output.read_bytes()),'complete_source_files':len(actual),'manifest_entries':len(entries)-1,'runtime_diagnostics_included':False},indent=2))

if __name__=='__main__':main()
