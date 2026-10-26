"""Document metadata-only adapters that must not be mistaken for native custom-shader acceptance."""
from pathlib import Path
import argparse,hashlib,json,os,shutil,subprocess,tempfile,zipfile
P=Path(__file__).resolve().parent
HARI='551d6047f3d7638a964050ca7f00c6e4eeb56a2c432b43f452f9ded68229e3a9'
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def probe(hari,fastutil,output):
    assert sha(hari)==HARI and not output.exists()
    # This exact MC dependency is already installed. Its Maven SHA1 is verified as well.
    assert hashlib.sha1(fastutil.read_bytes()).hexdigest()=='bb7ea75ecdb216654237830b3a96d87ad91f8cc5'
    java=Path(os.environ['JAVA_HOME'])/'bin';suffix='.exe' if os.name=='nt' else ''
    with tempfile.TemporaryDirectory(prefix='mvh-program-gap-') as t:
        cp=os.pathsep.join(map(str,[hari,fastutil]));q=Path(t)
        subprocess.run([str(java/('javac'+suffix)),'--release','17','-cp',cp,'-d',str(q),str(P/'NativeProgramGapProbe.java')],check=True,capture_output=True)
        result=subprocess.run([str(java/('java'+suffix)),'-cp',str(q)+os.pathsep+cp,'NativeProgramGapProbe'],check=True,capture_output=True,text=True,timeout=30)
    values=dict(line.split('=',1) for line in result.stdout.splitlines())
    assert set(values)=={'status_before_compile','invalid_glsl_compile_status','invalid_glsl_log_length','empty_program_link_status','unsubmitted_fence_wait_result','unsubmitted_fence_status'}
    observed={k:int(v) for k,v in values.items()}
    assert observed=={'status_before_compile':1,'invalid_glsl_compile_status':1,'invalid_glsl_log_length':0,'empty_program_link_status':1,'unsubmitted_fence_wait_result':0x911A,'unsubmitted_fence_status':0x9119},observed
    with zipfile.ZipFile(hari) as z:classes={name:hashlib.sha256(z.read('net/vulkanmod/gl/'+name+'.class')).hexdigest() for name in ['GlProgram','GlSync','GlVertexArray']}
    report={'hari_sha256':HARI,'fastutil_sha256':sha(fastutil),'class_sha256':classes,'actual_production_adapter_observations':observed,'custom_shader_or_fence_native_accepted':False,'minecraft_or_opengl_launched':False,'third_party_mod_classes_loaded':False,'native_gate_changed':False,'scope':'Pinned actual Hari23 metadata adapters accept invalid GLSL/empty links and signal handles without submitting work. This CPU diagnostic establishes a missing real custom-program/synchronization dependency, not graphics compatibility. No aliases/contracts are whitelisted from these results. Vanilla native pipelines and the bounded Flywheel engine use separate real Vulkan paths.'}
    output.parent.mkdir(parents=True,exist_ok=True);output.write_text(json.dumps(report,indent=2)+'\n',encoding='utf8')
    print('PINNED_NATIVE_PROGRAM_AND_FENCE_GAP_CONFIRMED; NO_NATIVE_COMPATIBILITY_ACCEPTED')
if __name__=='__main__':
    ap=argparse.ArgumentParser();ap.add_argument('hari',type=Path);ap.add_argument('fastutil',type=Path);ap.add_argument('report',type=Path);a=ap.parse_args();probe(a.hari,a.fastutil,a.report)
