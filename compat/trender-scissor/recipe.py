"""Pin outer/nested/classes; preserve every original byte except the four audited UI calls."""
from pathlib import Path
import argparse,copy,hashlib,io,json,os,subprocess,tempfile,zipfile
H=Path(__file__).resolve().parent
OUTER='5d5dc228dc18422fd8a81cff63e654abb07ca4507b317db114b220aa3fc67d3b'
ENTRY='META-INF/jars/TRender-1.0.17-1.20.1-forge-SNAPSHOT.jar'
INNER='fdfe53b0eff6d7a23f6a785698fc531ef47d026e9ed4fa7a8560b51ba6d32337'
CLASSES={'dev/tr7zw/trender/gui/client/CottonClientScreen.class':'a5e92a312231d049cb6f3d6d51764ae3df5f5645a88dcf65780b0fd28c1258f3','dev/tr7zw/trender/gui/client/Scissors.class':'a6893d6816d0336a7c52de5e47a6d3e1f2e6fe3a9ea65ee79bc22812d2222ed7'}
HELPER='mvhtrendercompat/NativeScissorBridge.class'
def sha(data):return hashlib.sha256(data).hexdigest()
def check(z):
    names=z.namelist();assert len(names)==len(set(names))
    assert not any(n.upper().endswith(('.SF','.RSA','.DSA','.EC')) for n in names),'Signed input needs separate review'
def main():
    p=argparse.ArgumentParser();p.add_argument('original',type=Path);p.add_argument('output',type=Path);p.add_argument('--helper',type=Path,required=True);p.add_argument('--asm-classpath',required=True);a=p.parse_args()
    assert a.original.resolve()!=a.output.resolve() and not a.output.exists()
    before=a.original.read_bytes();assert sha(before)==OUTER
    helper_data=a.helper.read_bytes()
    with zipfile.ZipFile(io.BytesIO(helper_data)) as helper:
        check(helper);compiled=helper.read(HELPER);assert json.loads(helper.read('pack.mcmeta'))['pack']['pack_format']==15
        assert b'org/lwjgl/opengl/' not in compiled,'Runtime helper must not contain direct GL calls'
    changed={};report={'source_sha256':OUTER,'nested_original_sha256':INNER,'helper_jar_sha256':sha(helper_data),'helper_class_sha256':sha(compiled),'operation':'Only four INVOKESTATIC owners change, exact descriptors/capability retained; original clipping math, flush, widgets/assets remain intact','renderer_included':False,'full_pack_or_performance_accepted':False,'changed_classes':{}}
    with tempfile.TemporaryDirectory(prefix='mvh-trender-') as tmp:
        work=Path(tmp);classes=work/'classes'
        subprocess.run(['javac','--release','17','-cp',a.asm_classpath,'-d',str(classes),str(H/'src/PatchTRenderScissor.java')],check=True)
        with zipfile.ZipFile(io.BytesIO(before)) as outer:
            check(outer);nested=outer.read(ENTRY);assert sha(nested)==INNER
            with zipfile.ZipFile(io.BytesIO(nested)) as source:
                check(source);assert HELPER not in source.namelist()
                for name,pin in CLASSES.items():
                    original=source.read(name);assert sha(original)==pin
                    inp=work/(Path(name).stem+'.class');out=work/(Path(name).stem+'-patched.class');inp.write_bytes(original)
                    subprocess.run(['java','-cp',str(classes)+os.pathsep+a.asm_classpath,'PatchTRenderScissor',str(inp),str(out)],check=True)
                    changed[name]=out.read_bytes();report['changed_classes'][name]={'before_sha256':pin,'after_sha256':sha(changed[name])}
                buffer=io.BytesIO()
                with zipfile.ZipFile(buffer,'w',compression=zipfile.ZIP_DEFLATED) as target:
                    for info in source.infolist():target.writestr(copy.copy(info),changed.get(info.filename,source.read(info.filename)))
                    target.writestr(HELPER,compiled)
                    target.writestr('META-INF/MVH-SCISSOR-LICENSE.txt',(H/'LICENSE').read_bytes())
                    target.writestr('META-INF/MVH-SCISSOR-SOURCE.java',(H/'src/main/java/mvhtrendercompat/NativeScissorBridge.java').read_bytes())
                fixed=buffer.getvalue()
                with zipfile.ZipFile(io.BytesIO(fixed)) as verify:
                    for name in source.namelist():assert verify.read(name)==changed.get(name,source.read(name)),name
            report['nested_patched_sha256']=sha(fixed);a.output.parent.mkdir(parents=True,exist_ok=True)
            with zipfile.ZipFile(a.output,'w',compression=zipfile.ZIP_DEFLATED) as target:
                for info in outer.infolist():target.writestr(copy.copy(info),fixed if info.filename==ENTRY else outer.read(info.filename))
                target.writestr('META-INF/MVH-NATIVE-TR-SCISSOR.json',json.dumps(report,indent=2))
                if 'pack.mcmeta' not in outer.namelist():target.writestr('pack.mcmeta',json.dumps({'pack':{'pack_format':15,'description':'Pinned EntityCulling with native TRender scissor bridge'}}))
            with zipfile.ZipFile(a.output) as verify:
                for name in outer.namelist():assert verify.read(name)==(fixed if name==ENTRY else outer.read(name)),name
                assert json.loads(verify.read('pack.mcmeta'))['pack']['pack_format']==15
    assert a.original.read_bytes()==before
    report['output_sha256']=sha(a.output.read_bytes());a.output.with_suffix('.manifest.json').write_text(json.dumps(report,indent=2)+'\n')
    print('PRIVATE_TR_RENDER_SCISSOR_CANDIDATE',report['output_sha256'])
if __name__=='__main__':main()
