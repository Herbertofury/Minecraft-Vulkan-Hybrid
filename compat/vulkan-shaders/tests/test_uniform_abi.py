"""Compile the pinned full Iris reference and reflect actual native shaderc SPIR-V."""
from pathlib import Path
import argparse,hashlib,importlib.util,json,os,subprocess,tempfile,urllib.request
HERE=Path(__file__).resolve().parent;H=HERE.parent
spec=importlib.util.spec_from_file_location('mvh_native_compiler',HERE/'test_native_compiler.py');base=importlib.util.module_from_spec(spec);spec.loader.exec_module(base)
def main():
    ap=argparse.ArgumentParser();ap.add_argument('--reference-source',type=Path);ap.add_argument('--download-reference',action='store_true');ap.add_argument('--lwjgl-classpath',required=True);ap.add_argument('--export-spv',type=Path);ap.add_argument('--report',type=Path);a=ap.parse_args()
    assert bool(a.reference_source)!=a.download_reference
    original=a.reference_source.read_bytes() if a.reference_source else urllib.request.urlopen(base.URL,timeout=40).read()
    assert hashlib.sha256(original).hexdigest()==base.SHA
    with tempfile.TemporaryDirectory(prefix='mvh-iris-uniform-patch-') as tmp:
        root=Path(tmp);p=root/base.REL;p.parent.mkdir(parents=True);p.write_bytes(original)
        for name in ['iris-spirv-full-input-key.patch','iris-spirv-owned-bounded-cache.patch']:
            patch=H/'reference-patches'/name
            subprocess.run(['git','apply','--check',str(patch.resolve())],cwd=root,check=True,capture_output=True)
            subprocess.run(['git','apply',str(patch.resolve())],cwd=root,check=True,capture_output=True)
        compiler=p.read_text(encoding='utf8')
    helper=H/'uniforms/src/mvhshadercompat/SpirvUniformBlock.java';body=helper.read_text();cache=(H/'reference-patches/NativeSpirvCache.java').read_text()
    variants=[('reflected_production',body,True),('packed_mat3',body.replace('c*f.matrixStride+r*4','c*f.rows*4+r*4'),False),('packed_array',body.replace('a*f.arrayStride+c*f.matrixStride','a*f.rows*f.columns*4+c*f.matrixStride'),False),('matrix_transposed',body.replace('c*f.matrixStride+r*4','r*f.matrixStride+c*4'),False)]
    results=[]
    for name,code,expected in variants:
        assert name=='reflected_production' or code!=body
        with tempfile.TemporaryDirectory(prefix='mvh-iris-native-uniform-') as tmp:
            root=Path(tmp);sources=base.fixture(root,compiler,cache)
            for rel,content in [('mvhshadercompat/SpirvUniformBlock.java',code),('NativeUniformProbe.java',(HERE/'NativeUniformProbe.java').read_text())]:
                p=root/'src/main/java'/rel;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(content,encoding='utf8');sources.append(str(p))
            classes=root/'classes';built=subprocess.run(['javac','--release','17','-cp',a.lwjgl_classpath,'-d',str(classes),*sources],capture_output=True,text=True)
            assert built.returncode==0,built.stderr
            export=[str(a.export_spv.resolve())] if expected and a.export_spv else []
            run=subprocess.run(['java','-ea','-cp',str(classes)+os.pathsep+a.lwjgl_classpath,'NativeUniformProbe',str(HERE/'gpu-reference-sources/reference-uniform-fragment.glsl'),*export],capture_output=True,text=True,timeout=60)
            assert (run.returncode==0)==expected,(name,run.stdout,run.stderr)
            if not expected:assert 'AssertionError' in run.stderr
            results.append({'variant':name,'passed':run.returncode==0,'expected':expected})
    report={'passed':True,'compiler_sha256':hashlib.sha256(compiler.encode()).hexdigest(),'helper_sha256':hashlib.sha256(body.encode()).hexdigest(),'shader_sha256':hashlib.sha256((HERE/'gpu-reference-sources/reference-uniform-fragment.glsl').read_bytes()).hexdigest(),'native_sdk':'Official shaderc/LWJGL3.3.6, Java17, isolated CPU process','members':7,'ubo_bytes':192,'member_offsets':[0,12,16,64,112,176,184],'matrix_stride':16,'array_stride':16,'cases':results,'scope':'Actual compiled SPIR-V descriptor/block/member/stride reflection and CPU writes; 32-bit scalar/vector, column-major float matrices and one-dimensional fixed arrays only. Unsupported layouts reject before upload. No full SPIR-V validator, arbitrary shader-provider, name-to-uniform mapping, GPU/FPS/shader-pack acceptance.'}
    if a.report:assert not a.report.exists();a.report.write_text(json.dumps(report,indent=2)+'\n')
    print('NATIVE_REFLECTED_UNIFORM_ABI_PASS',len(results),'variants; native offsets, matrix/array padding, writes and malformed input rejection')
if __name__=='__main__':main()
