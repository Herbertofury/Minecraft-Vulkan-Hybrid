"""Build the real pinned Flywheel shader fixture; original Create assets remain local."""
from pathlib import Path
import argparse,hashlib,importlib.util,io,json,os,shutil,struct,subprocess,tempfile,urllib.request,zipfile
N=Path(__file__).resolve().parents[1];R=N.parents[1];H=R/'compat/vulkan-shaders'
spec=importlib.util.spec_from_file_location('mvh_native_compiler',H/'tests/test_native_compiler.py');base=importlib.util.module_from_spec(spec);spec.loader.exec_module(base)
def digest(raw):return hashlib.sha256(raw).hexdigest()
def assets(create,out):
    pin=json.loads((N/'ORIGIN.json').read_text());raw=create.read_bytes();assert digest(raw)==pin['original_create_sha256'],'Original Create input changed';z=zipfile.ZipFile(io.BytesIO(raw));fw=z.read('META-INF/jarjar/flywheel-forge-1.20.1-1.0.5.jar');assert digest(fw)==pin['original_nested_flywheel_sha256']
    for name,sha in pin['original_create_assets'].items():
        b=z.read(name);assert digest(b)==sha;(out/Path(name).name).write_bytes(b)
    model=json.loads((out/'shaft.json').read_text());e=model['elements'][0];assert len(model['elements'])==1 and e['from']==[6,0,6] and e['to']==[10,16,10]
    x0,y0,z0=[v/16 for v in e['from']];x1,y1,z1=[v/16 for v in e['to']]
    faces={'north':([(x1,y1,z0),(x1,y0,z0),(x0,y0,z0),(x0,y1,z0)],(0,0,-1)), 'south':([(x0,y1,z1),(x0,y0,z1),(x1,y0,z1),(x1,y1,z1)],(0,0,1)), 'east':([(x1,y1,z1),(x1,y0,z1),(x1,y0,z0),(x1,y1,z0)],(1,0,0)), 'west':([(x0,y1,z0),(x0,y0,z0),(x0,y0,z1),(x0,y1,z1)],(-1,0,0)), 'up':([(x0,y1,z0),(x0,y1,z1),(x1,y1,z1),(x1,y1,z0)],(0,1,0)), 'down':([(x0,y0,z1),(x0,y0,z0),(x1,y0,z0),(x1,y0,z1)],(0,-1,0))}
    vertices=bytearray();indices=bytearray()
    for face,(positions,normal) in faces.items():
        f=e['faces'][face];u0,v0,u1,v1=[v/16 for v in f['uv']];texture=int(f['texture'][1:]);offset=len(vertices)//36
        for p,uv in zip(positions,[(u0,v0),(u0,v1),(u1,v1),(u1,v0)]):vertices+=struct.pack('<8fI',p[0]-.5,p[1],p[2]-.5,*uv,*normal,texture)
        indices+=struct.pack('<6I',offset,offset+1,offset+2,offset,offset+2,offset+3)
    (out/'shaft.vertices').write_bytes(vertices);(out/'shaft.indices').write_bytes(indices)
def main():
    p=argparse.ArgumentParser();p.add_argument('--reference-source',type=Path);p.add_argument('--download-reference',action='store_true');p.add_argument('--lwjgl-classpath',required=True);p.add_argument('--output',type=Path,required=True);p.add_argument('--create-jar',type=Path);p.add_argument('--report',type=Path);a=p.parse_args()
    assert bool(a.reference_source)!=a.download_reference;assert not a.output.exists(),'Use a fresh fixture directory';a.output.mkdir(parents=True)
    pin=json.loads((N/'ORIGIN.json').read_text());assert all(digest((N/'vendor/flywheel-1.0.5'/f['path']).read_bytes())==f['sha256'] for f in pin['original_files'])
    original=a.reference_source.read_bytes() if a.reference_source else urllib.request.urlopen(base.URL,timeout=40).read();assert digest(original)==base.SHA
    with tempfile.TemporaryDirectory(prefix='mvh-native-flywheel-compiler-') as td:
        t=Path(td);file=t/base.REL;file.parent.mkdir(parents=True);file.write_bytes(original)
        for name in ['iris-spirv-full-input-key.patch','iris-spirv-owned-bounded-cache.patch']:subprocess.run(['git','apply',str((H/'reference-patches'/name).resolve())],cwd=t,check=True,capture_output=True)
        compiler=file.read_text(encoding='utf8');sources=base.fixture(t,compiler,(H/'reference-patches/NativeSpirvCache.java').read_text());sources.extend([str(H/'uniforms/src/mvhshadercompat/SpirvUniformBlock.java'),str(N/'tests/NativeCullLayoutProbe.java')])
        compiled=subprocess.run(['javac','--release','17','-cp',a.lwjgl_classpath,'-d',str(t/'classes'),*sources],capture_output=True,text=True);assert compiled.returncode==0,compiled.stderr
        run=subprocess.run(['java','-ea','-cp',str(t/'classes')+os.pathsep+a.lwjgl_classpath,'NativeCullLayoutProbe',str(N/'shaders'),str(a.output.resolve())],capture_output=True,text=True,timeout=60);assert run.returncode==0,(run.stdout,run.stderr);print(run.stdout.strip())
        # A lost parent-struct base offset must fail the actual compiled frame layout.
        helper=(H/'uniforms/src/mvhshadercompat/SpirvUniformBlock.java').read_text();broken=helper.replace('offset=Math.addExact(base,source.get(35))','offset=source.get(35)');assert broken!=helper
        bad=t/'broken/mvhshadercompat/SpirvUniformBlock.java';bad.parent.mkdir(parents=True);bad.write_text(broken,encoding='utf8')
        build=subprocess.run(['javac','--release','17','-cp',str(t/'classes')+os.pathsep+a.lwjgl_classpath,'-d',str(t/'broken-classes'),str(bad),str(N/'tests/NativeCullLayoutProbe.java')],capture_output=True,text=True);assert build.returncode==0,build.stderr
        rejected=subprocess.run(['java','-ea','-cp',str(t/'broken-classes')+os.pathsep+str(t/'classes')+os.pathsep+a.lwjgl_classpath,'NativeCullLayoutProbe',str(N/'shaders'),str(t)],capture_output=True,text=True,timeout=60);assert rejected.returncode!=0 and ('Overlapping or reordered' in rejected.stderr or 'AssertionError' in rejected.stderr),(rejected.stdout,rejected.stderr)
    for f in (N/'shaders').iterdir():shutil.copy2(f,a.output/f.name)
    if a.create_jar:assets(a.create_jar,a.output)
    outputs={f.name:digest(f.read_bytes()) for f in a.output.iterdir()};pinned=json.loads((N/'PINNED-GPU-FIXTURE-MANIFEST.json').read_text());matches=outputs==pinned['outputs']
    if matches:(a.output/'MANIFEST.json').write_bytes((N/'PINNED-GPU-FIXTURE-MANIFEST.json').read_bytes())
    report={'passed':True,'original_components_verified':len(pin['original_files']),'compiler_commit':base.ORIGIN['commit'],'native_sdk':'Official LWJGL/shaderc3.3.6; Java17; CPU compile/reflection only','frame_uniform_bytes':864,'frame_uniform_leaves':46,'nested_base_offset_mutation_rejected':True,'pinned_windows_gpu_fixture_matches':matches,'private_original_assets_supplied':bool(a.create_jar),'outputs':outputs,'scope':'Four real shader stages,original vendor hashes,structured frame paths/writes and negative control. No GPU or complete Create/provider/FPS acceptance; private asset output must never be redistributed.'}
    if a.report:assert not a.report.exists();a.report.write_bytes((json.dumps(report,indent=2)+'\n').encode())
    print('NATIVE_FLYWHEEL_REPRODUCIBLE_FIXTURE_PASS; pinned GPU fixture matches:',matches)
if __name__=='__main__':main()
