"""Production session gate: native requirements block fallback and bypasses."""
from pathlib import Path
import argparse,json,subprocess,tempfile,zipfile
I=Path(__file__).resolve().parents[1];R=I.parents[1]
ap=argparse.ArgumentParser();ap.add_argument('--report',type=Path);a=ap.parse_args()
with tempfile.TemporaryDirectory(prefix='mvh-native-required-') as tmp:
    p=Path(tmp);classes=p/'classes';files=[]
    for name in ['UniversalRendererGate.java','InactiveModernFixGlAudit.java','InactiveGrassComputeGlAudit.java','InactiveIxerisMacOsGlAudit.java']:
        f=p/name;f.write_bytes((I/'overlay/forge/src/main/java/net/vulkanmod/compat'/name).read_bytes());files.append(str(f))
    f=p/'Probe.java';f.write_text('''import net.vulkanmod.compat.UniversalRendererGate;public class Probe{public static void main(String[]a){try{boolean enabled=UniversalRendererGate.vulkanRendererEnabled();if(a[0].equals("blocked"))throw new AssertionError("Requirement silently accepted");if(enabled!=Boolean.parseBoolean(a[0]))throw new AssertionError("Unexpected renderer");System.out.println("RESULT="+enabled);}catch(IllegalStateException e){if(!a[0].equals("blocked")||!e.getMessage().contains(a[1]))throw e;System.out.println("EXPECTED_BLOCK="+e.getMessage());}}}''');files.append(str(f))
    subprocess.run(['javac','--release','17','-d',str(classes),*files],check=True,capture_output=True)
    rel=Path('assets/vulkanmod/compat/harimt_supported_gl_methods.properties');c=classes/rel;c.parent.mkdir(parents=True);c.write_bytes((R/'source/forge/src/main/resources'/rel).read_bytes())
    cases=[('clean-native','require',None,False,'true',''),('legacy-auto-conflict','auto',None,True,'false',''),('required-conflict','require',None,True,'blocked','Native Vulkan is required'),('file-requires-auto','auto','true',True,'blocked','Native Vulkan is required'),('file-forbids-force','force','true',False,'blocked','forced compatibility bypass is forbidden'),('required-off','off','true',False,'blocked','forced off'),('file-cannot-weaken-require','require','false',True,'blocked','Native Vulkan is required'),('invalid-policy','auto','invalid',False,'blocked','Invalid native renderer policy'),('unreadable-policy','auto','directory',False,'blocked','Cannot inspect required native renderer policy')]
    result=[]
    for label,mode,policy,conflict,expected,reason in cases:
        game=p/label;game.mkdir()
        if conflict:
            mods=game/'mods';mods.mkdir()
            with zipfile.ZipFile(mods/'fixture-oculus.jar','w') as z:z.writestr('META-INF/mods.toml','[[mods]]\nmodId="oculus"\n')
        if policy is not None:
            q=game/'config/harimt-native-required.properties';q.parent.mkdir()
            if policy=='directory':q.mkdir()
            else:q.write_text('requireNative='+policy+'\n')
        run=subprocess.run(['java','-Dharimt.vulkan.mode='+mode,'-Dharimt.vulkan.compatCache=false','-cp',str(classes),'Probe',expected,reason],cwd=game,check=True,capture_output=True,text=True)
        result.append({'case':label,'result':run.stdout.strip()})
report={'passed':True,'cases':result,'scope':'Actual production renderer gate; conflict/native/config/forced-bypass/I/O controls. No Minecraft, GPU, gameplay compatibility or performance claim.'}
if a.report:a.report.parent.mkdir(parents=True,exist_ok=True);a.report.write_text(json.dumps(report,indent=2)+'\n')
print(json.dumps(report,indent=2))
