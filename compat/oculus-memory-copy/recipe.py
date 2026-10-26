"""Recoverable, whole-JAR-pinned private Oculus repair; no renderer is included."""
from pathlib import Path
import argparse, copy, hashlib, json, subprocess, tempfile, zipfile

HERE=Path(__file__).resolve().parent
PIN='2e7e4e713effee15c2dfd2f32e9fe30f71dbc88a53ad70bdf994f6853f818e30'
TARGET='net/irisshaders/batchedentityrendering/mixin/MixinBufferBuilder_SegmentRendering.class'
HELPER='mvhoculuscompat/MemoryCopy.class'

def main():
    p=argparse.ArgumentParser();p.add_argument('original',type=Path);p.add_argument('output',type=Path);p.add_argument('--asm-classpath',required=True);a=p.parse_args()
    assert a.original.resolve()!=a.output.resolve() and not a.output.exists(), 'Use a new recoverable output'
    before=hashlib.sha256(a.original.read_bytes()).hexdigest();assert before==PIN,'Unreviewed Oculus build'
    with tempfile.TemporaryDirectory(prefix='mvh-oculus-copy-') as tmp:
        work=Path(tmp);classes=work/'classes'
        subprocess.run(['javac','-encoding','UTF-8','--release','17','-cp',a.asm_classpath,'-d',str(classes),str(HERE/'src/PatchMemoryOwner.java'),str(HERE/'src/mvhoculuscompat/MemoryCopy.java')],check=True)
        with zipfile.ZipFile(a.original) as source:
            names=source.namelist();assert len(names)==len(set(names))
            assert HELPER not in names and not any(n.upper().endswith(('.SF','.RSA','.DSA','.EC')) for n in names), 'Signed or previously patched input requires separate review'
            original=source.read(TARGET);(work/'before.class').write_bytes(original)
            subprocess.run(['java','-cp',str(classes)+__import__('os').pathsep+a.asm_classpath,'PatchMemoryOwner',str(work/'before.class'),str(work/'after.class')],check=True)
            patched=(work/'after.class').read_bytes();assert b'net/caffeinemc/mods/sodium/api/memory/MemoryIntrinsics' not in patched
            a.output.parent.mkdir(parents=True,exist_ok=True)
            report={'source_sha256':PIN,'changed_original_entries':[TARGET],'operation':'One INVOKESTATIC owner changed; copyMemory(JJI)V descriptor retained; same JDK Unsafe.copyMemory operation','before_class_sha256':hashlib.sha256(original).hexdigest(),'after_class_sha256':hashlib.sha256(patched).hexdigest(),'renderer_included':False,'status':'Startup repair candidate; native shader capability is not established'}
            with zipfile.ZipFile(a.output,'w',compression=zipfile.ZIP_DEFLATED) as out:
                for info in source.infolist():out.writestr(copy.copy(info),patched if info.filename==TARGET else source.read(info.filename))
                out.writestr(HELPER,(classes/HELPER).read_bytes())
                out.writestr('META-INF/MVH-OCULUS-MEMORY-REPAIR.json',json.dumps(report,indent=2))
                out.writestr('META-INF/MVH-OCULUS-MEMORY-COPY-LICENSE.txt',(HERE/'LICENSE').read_bytes())
                out.writestr('META-INF/MVH-OCULUS-MEMORY-COPY-SOURCE.java',(HERE/'src/mvhoculuscompat/MemoryCopy.java').read_bytes())
                if 'pack.mcmeta' not in names:out.writestr('pack.mcmeta',json.dumps({'pack':{'pack_format':15,'description':'Oculus with pinned vertex-memory compatibility; upstream assets preserved'}}))
            with zipfile.ZipFile(a.output) as fixed:
                for name in names:assert fixed.read(name)==(patched if name==TARGET else source.read(name)),name
                assert json.loads(fixed.read('pack.mcmeta'))['pack']['pack_format']==15
    assert hashlib.sha256(a.original.read_bytes()).hexdigest()==before
    digest=hashlib.sha256(a.output.read_bytes()).hexdigest();print('OCULUS_PRIVATE_REPAIR_SHA256',digest)
    a.output.with_suffix('.manifest.json').write_text(json.dumps({**report,'output_sha256':digest,'output_bytes':a.output.stat().st_size},indent=2))
if __name__=='__main__':main()
