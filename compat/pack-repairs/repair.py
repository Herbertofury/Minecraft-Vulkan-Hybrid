"""Generate a private repair from the exact owned original; never overwrite it."""
from pathlib import Path
import argparse, copy, hashlib, json, re, zipfile
SOURCES = {
    'transformer': ('cataclysm-to-magic-fix-1.1.0.jar', 'be9cc0afc969a7f50b7aaa4d5d29bdbf83fa392c5d6bc2e83c9a06c32f0320b9'),
    'models': ('create-automotives-1.20.1-V2.jar', 'b9f72f47cfcd7c54aa0c3e5f600aa34622ed4bc6fcbac0a41e431e07f1871f97'),
}
def main():
    p=argparse.ArgumentParser();p.add_argument('kind',choices=SOURCES);p.add_argument('source',type=Path);p.add_argument('output',type=Path);a=p.parse_args()
    expected_name,expected_hash=SOURCES[a.kind]
    assert a.source.name==expected_name, 'Unreviewed input filename'
    digest=hashlib.file_digest(a.source.open('rb'),'sha256').hexdigest()
    assert digest==expected_hash, 'Unreviewed input bytes'
    assert a.output.resolve()!=a.source.resolve() and not a.output.exists(), 'Use a fresh output path'
    prefix='assets/automotives/models/item/vehicles/boilerbox/';changes=[];added=[]
    with zipfile.ZipFile(a.source) as src,zipfile.ZipFile(a.output,'x') as dst:
        for info in src.infolist():
            old=src.read(info.filename);new=old
            if a.kind=='transformer' and info.filename=='coremods/traveloptics_cataclysm_compat.js':
                assert old.count(b'remapClass(classNode);')==4
                new=old.replace(b'remapClass(classNode);',b'remapClass(classNode); return classNode;')
            if a.kind=='models' and info.filename.startswith(prefix) and info.filename.endswith('.json'):
                if info.filename.endswith('/boilerbox.json'):
                    assert new.count(b'"automotives:textures/vehicles/boilerbox"')==1
                    new=new.replace(b'"automotives:textures/vehicles/boilerbox"',b'"automotives:vehicles/boilerbox",')
                new=new.replace(b'mts:item/vehicles/boilerbox/',b'automotives:item/vehicles/boilerbox/')
                model=json.loads(new)
                if 'model' in model:
                    model['model']='automotives:models/item/vehicles/boilerbox/boilerbox.obj';new=json.dumps(model,indent=2).encode()
            if new!=old:
                changes.append({'entry':info.filename,'before_sha256':hashlib.sha256(old).hexdigest(),'after_sha256':hashlib.sha256(new).hexdigest()})
            dst.writestr(copy.copy(info),new)
        if a.kind=='models':
            obj=src.read(prefix+'boilerbox.obj').decode('utf8');materials=set(re.findall(r'^usemtl (.+)$',obj,re.M));assert len(materials)==1
            mtl='\n'.join('newmtl '+m+'\nKa 1 1 1\nKd 1 1 1\nKs 0 0 0\nd 1\nillum 1\nmap_Kd automotives:vehicles/boilerbox\n' for m in sorted(materials))
            name=prefix+'boilerbox.mtl';assert name not in src.namelist()
            info=zipfile.ZipInfo(name,src.getinfo(prefix+'boilerbox.obj').date_time);dst.writestr(info,mtl);added.append(name)
    assert len(changes)==(1 if a.kind=='transformer' else 3), 'Unexpected patch scope'
    with zipfile.ZipFile(a.source) as src,zipfile.ZipFile(a.output) as dst:
        assert dst.namelist()==src.namelist()+added
        for name in src.namelist():
            if name not in {r['entry'] for r in changes}:assert dst.read(name)==src.read(name)
    report={'source':a.source.name,'source_sha256':digest,'output':a.output.name,'output_sha256':hashlib.file_digest(a.output.open('rb'),'sha256').hexdigest(),'changes':changes,'added':added,'license':'Original terms retained; repaired private assets are not redistributed'}
    a.output.with_suffix('.manifest.json').write_text(json.dumps(report,indent=2)+'\n')
    print(json.dumps(report,indent=2))
if __name__=='__main__':main()
