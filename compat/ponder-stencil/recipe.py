"""Exact byte-pinned private Ponder/Create repair. No upstream class executes or disappears."""
from pathlib import Path
import argparse,copy,hashlib,io,json,os,re,subprocess,tempfile,zipfile
H=Path(__file__).resolve().parent
OUTER='6fbb910c367dbce8e4fc7e5bf64b6edd4de980906ed00af8e47e4af843c0d9b0'
ENTRY='META-INF/jarjar/Ponder-Forge-1.20.1-1.0.91.jar'
INNER='86e6b64372aba6d9c56f2c35725ea26d8febf2c75eed9950566e7f2849443b34'
CLASS='net/createmod/catnip/gui/element/StencilElement.class'
CLASS_PIN='c801e58dca82473c70ff8e6030f0dc53fb76ba60eedb8e0247b9029cb70aa73f'
HELPERS={'mvhpondercompat/NativeStencilBridge.class','mvhpondercompat/NativeStencilBridge$Original.class'}
def sha(data):return hashlib.sha256(data).hexdigest()
def safe(z):
    names=z.namelist();assert len(names)==len(set(names))
    assert not any(n.upper().endswith(('.SF','.RSA','.DSA','.EC')) for n in names),'Signed input requires separate handling'
def main():
    ap=argparse.ArgumentParser();ap.add_argument('original',type=Path);ap.add_argument('output',type=Path);ap.add_argument('--standalone',type=Path);ap.add_argument('--helper',type=Path,required=True);ap.add_argument('--asm-classpath',required=True);a=ap.parse_args()
    assert a.original.resolve()!=a.output.resolve() and not a.output.exists()
    if a.standalone:assert not a.standalone.exists() and a.standalone.resolve()!=a.output.resolve()
    before=a.original.read_bytes();assert sha(before)==OUTER
    helper=a.helper.read_bytes()
    with zipfile.ZipFile(io.BytesIO(helper)) as compiled:
        safe(compiled);assert json.loads(compiled.read('pack.mcmeta'))['pack']['pack_format']==15
        classes={n:compiled.read(n) for n in HELPERS}
        assert all(b'org/lwjgl/opengl/' not in b for b in classes.values()),'No direct unguarded GL calls in helper'
    report={'source_sha256':OUTER,'nested_original_sha256':INNER,'class_original_sha256':CLASS_PIN,'helper_jar_sha256':sha(helper),'helper_class_sha256':{n:sha(b) for n,b in classes.items()},'changed_instructions':4,'changed_constant_pool_method_references':2,'operation':'Only four literal GL_STENCIL_TEST instruction owners change; nested pack_format 8 becomes 15. Geometry, clear/mask/compare/fail ops, flush, defaults and all other upstream entries are unchanged. Known original OpenGL route remains lazy and selected. Flywheel/complete Create/shader/FPS acceptance remains pending.','full_create_native_accepted':False}
    with tempfile.TemporaryDirectory(prefix='mvh-ponder-stencil-patch-') as tmp,zipfile.ZipFile(io.BytesIO(before)) as outer:
        safe(outer);nested=outer.read(ENTRY);assert sha(nested)==INNER
        root=Path(tmp);built=root/'classes'
        subprocess.run(['javac','--release','17','-cp',a.asm_classpath,'-d',str(built),str(H/'src/PatchPonderStencil.java')],check=True)
        with zipfile.ZipFile(io.BytesIO(nested)) as source:
            safe(source);original=source.read(CLASS);assert sha(original)==CLASS_PIN
            assert not any(n.startswith('mvhpondercompat/') for n in source.namelist())
            original_pack=source.read('pack.mcmeta');assert json.loads(original_pack)['pack']['pack_format']==8
            fixed_pack,count=re.subn(rb'("pack_format"\s*:\s*)8\b',rb'\g<1>15',original_pack);assert count==1
            report['resource_pack_metadata']={'before_format':8,'after_format':15,'before_sha256':sha(original_pack),'after_sha256':sha(fixed_pack),'original_description_preserved':True}
            inp=root/'StencilElement.class';out=root/'StencilElement-patched.class';inp.write_bytes(original)
            subprocess.run(['java','-cp',str(built)+os.pathsep+a.asm_classpath,'PatchPonderStencil',str(inp),str(out)],check=True)
            changed=out.read_bytes();report['class_patched_sha256']=sha(changed)
            buffer=io.BytesIO()
            with zipfile.ZipFile(buffer,'w',compression=zipfile.ZIP_DEFLATED) as target:
                for info in source.infolist():target.writestr(copy.copy(info),changed if info.filename==CLASS else fixed_pack if info.filename=='pack.mcmeta' else source.read(info.filename))
                for name,data in classes.items():target.writestr(name,data)
                for name,path in [('META-INF/MVH-PONDER-STENCIL-LICENSE.txt',H/'LICENSE'),('META-INF/MVH-PONDER-UPSTREAM-MIT.txt',H/'LICENSE-UPSTREAM-PONDER-MIT.txt'),('META-INF/MVH-PONDER-STENCIL-SOURCE.java',H/'src/main/java/mvhpondercompat/NativeStencilBridge.java')]:target.writestr(name,path.read_bytes())
                target.writestr('META-INF/MVH-PONDER-STENCIL.json',json.dumps(report,indent=2))
                if 'pack.mcmeta' not in source.namelist():target.writestr('pack.mcmeta',json.dumps({'pack':{'pack_format':15,'description':'Pinned Ponder 1.0.91 with native stencil bridge'}}))
            fixed=buffer.getvalue()
            with zipfile.ZipFile(io.BytesIO(fixed)) as verify:
                for name in source.namelist():assert verify.read(name)==(changed if name==CLASS else fixed_pack if name=='pack.mcmeta' else source.read(name)),name
                assert json.loads(verify.read('pack.mcmeta'))['pack']['pack_format']==15
        report['nested_patched_sha256']=sha(fixed);a.output.parent.mkdir(parents=True,exist_ok=True)
        with zipfile.ZipFile(a.output,'w',compression=zipfile.ZIP_DEFLATED) as target:
            for info in outer.infolist():target.writestr(copy.copy(info),fixed if info.filename==ENTRY else outer.read(info.filename))
            target.writestr('META-INF/MVH-PONDER-STENCIL-REPAIR.json',json.dumps(report,indent=2))
            if 'pack.mcmeta' not in outer.namelist():target.writestr('pack.mcmeta',json.dumps({'pack':{'pack_format':15,'description':'Pinned Create 6.0.8 with Ponder native stencil bridge; Flywheel native port pending'}}))
        with zipfile.ZipFile(a.output) as verify:
            for name in outer.namelist():assert verify.read(name)==(fixed if name==ENTRY else outer.read(name)),name
            assert json.loads(verify.read('pack.mcmeta'))['pack']['pack_format']==15
        if a.standalone:a.standalone.parent.mkdir(parents=True,exist_ok=True);a.standalone.write_bytes(fixed)
    report['output_sha256']=sha(a.output.read_bytes());a.output.with_suffix('.manifest.json').write_text(json.dumps(report,indent=2)+'\n')
    print('PRIVATE_PONDER_REPAIR_COMPLETE',report['output_sha256'],'UPSTREAM_ENTRIES_PRESERVED')
if __name__=='__main__':main()
