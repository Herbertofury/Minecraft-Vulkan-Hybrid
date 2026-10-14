"""Remove only the pinned Indigo implementation; retain the other FFAPI modules."""
from pathlib import Path
import argparse,hashlib,json,zipfile
SOURCE_SHA256='3dd3f93c31122d928297220601f521e6a8a20356bafc7819bf4ddce4c2bb085d'
MODULE='META-INF/jarjar/fabric-renderer-indigo-1.5.3+b5b2da4177.jar'
MODULE_SHA256='c4da5cf224f8cbaadd280d0b695aa8c54b925cd2eca967fb5d48fe16e8c0d491'
METADATA='META-INF/jarjar/metadata.json'

def patch(source,output):
    assert source.resolve()!=output.resolve() and not output.exists()
    assert hashlib.sha256(source.read_bytes()).hexdigest()==SOURCE_SHA256
    with zipfile.ZipFile(source) as z:
        assert z.testzip() is None and len(z.namelist())==len(set(z.namelist()))
        assert not any(n.upper().endswith(('.SF','.RSA','.DSA','.EC')) for n in z.namelist())
        assert hashlib.sha256(z.read(MODULE)).hexdigest()==MODULE_SHA256
        metadata=json.loads(z.read(METADATA));original=metadata['jars']
        removed=[m for m in original if m['path']==MODULE]
        assert len(original)==45 and len(removed)==1
        metadata['jars']=[m for m in original if m['path']!=MODULE]
        replacements={METADATA:(json.dumps(metadata,indent=2)+'\n').encode()}
        if 'pack.mcmeta' not in z.namelist():
            replacements['pack.mcmeta']=json.dumps({'pack':{'pack_format':15,'description':'Forgified Fabric API 0.92.6+1.11.15; Indigo renderer removed for the explicitly required native Hari session. Original remaining APIs/assets/notices retained.'}},indent=2).encode()+b'\n'
        else:
            pack=json.loads(z.read('pack.mcmeta'))
            if pack['pack']['pack_format']!=15:
                pack['pack']['pack_format']=15;replacements['pack.mcmeta']=(json.dumps(pack,indent=2)+'\n').encode()
        with zipfile.ZipFile(output,'w') as out:
            for entry in z.infolist():
                if entry.filename==MODULE:continue
                out.writestr(entry,replacements.get(entry.filename,z.read(entry)))
            for name,data in replacements.items():
                if name not in z.namelist():out.writestr(name,data)
    with zipfile.ZipFile(source) as a,zipfile.ZipFile(output) as b:
        assert b.testzip() is None and MODULE not in b.namelist()
        assert {n for n in a.namelist() if n!=MODULE}<=set(b.namelist())
        changed=[n for n in a.namelist() if n!=MODULE and a.read(n)!=b.read(n)]
        assert set(changed)<=set(replacements)
        retained={m['path']:hashlib.sha256(b.read(m['path'])).hexdigest() for m in metadata['jars']}
        assert all(a.read(n)==b.read(n) for n in retained)
        assert json.loads(b.read('pack.mcmeta'))['pack']['pack_format']==15
    return {'source_sha256':SOURCE_SHA256,'output_sha256':hashlib.sha256(output.read_bytes()).hexdigest(),'removed_module':removed[0],'removed_module_sha256':MODULE_SHA256,'changed_entries':changed,'added_entries':[n for n in replacements if n not in a.namelist()],'retained_modules':retained,'remaining_module_count':44,'all_other_code_assets_notices_equal':True,'native_runtime_accepted':False,'scope':'Explicit user removal of Indigo only, not removal of FFAPI or the Fabric Rendering API. Native renderer still requires production gate and actual feature validation.'}

if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('source',type=Path);p.add_argument('output',type=Path);a=p.parse_args();proof=patch(a.source,a.output)
    a.output.with_suffix('.manifest.json').write_text(json.dumps(proof,indent=2)+'\n');print(json.dumps(proof,indent=2))
