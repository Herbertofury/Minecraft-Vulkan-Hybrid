"""Real JOML projection/rotation changes and owner/copy/invalidation contract of production state."""
from pathlib import Path
import argparse,hashlib,json,os,subprocess,tempfile,urllib.request
I=Path(__file__).resolve().parents[1];PIN='cac9f22f83a7aa33eebda73c16ff5261e3cb4911b6bafcf4c79ea486099d0c9a'
parser=argparse.ArgumentParser();parser.add_argument('--joml',type=Path);parser.add_argument('--report',type=Path);args=parser.parse_args()
source=(I/'overlay/forge/src/main/java/net/vulkanmod/render/chunk/frustum/FrustumVisibilityState.java').read_text()
world=(I/'overlay/forge/src/main/java/net/vulkanmod/render/chunk/WorldRenderer.java').read_text()
assert '!nativeFrustum.matchesVisibilityState(this.visibilityState, this.minecraft.smartCull, spectator)' in world
assert 'nativeFrustum.captureVisibilityState(this.visibilityState, this.minecraft.smartCull, spectator)' in world
assert 'this.visibilityState.invalidate();' in world
probe='''import org.joml.*;import net.vulkanmod.render.chunk.frustum.FrustumVisibilityState;
public class FrustumProbe{
 static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
 public static void main(String[]args)throws Exception{
  var state=new FrustumVisibilityState();var view=new Matrix4f().rotateY(.35f);var projection=new Matrix4f().perspective(1.1f,16f/9f,.05f,512f);var clip=new Matrix4f(projection).mul(view);
  check(!state.matches(clip,true,false),"Uninitialized state reused");state.capture(clip,true,false);check(state.matches(new Matrix4f(clip),true,false),"Identical values not reusable");
  var zoom=new Matrix4f().perspective(.75f,16f/9f,.05f,512f).mul(view);check(!state.matches(zoom,true,false),"FOV changed without graph invalidation");
  var aspect=new Matrix4f().perspective(1.1f,4f/3f,.05f,512f).mul(view);check(!state.matches(aspect,true,false),"Aspect change missed");
  var rotated=new Matrix4f(projection).mul(new Matrix4f().rotateY(.35001f));check(!state.matches(rotated,true,false),"Small rotation missed");
  check(!state.matches(clip,false,false)&&!state.matches(clip,true,true),"Culling/spectator mode missed");
  var original=new Matrix4f(clip);clip.m00(clip.m00()+.1f);check(state.matches(original,true,false)&&!state.matches(clip,true,false),"Caller matrix aliases snapshot");
  state.invalidate();check(!state.matches(original,true,false),"World/resource reset missed");
  state.capture(new Matrix4f().m00(Float.NaN),true,false);check(!state.matches(new Matrix4f().m00(Float.NaN),true,false),"NaN matrix accepted");
  state.capture(new Matrix4f().m33(Float.POSITIVE_INFINITY),true,false);check(!state.matches(new Matrix4f().m33(Float.POSITIVE_INFINITY),true,false),"Infinite matrix accepted");
  for(int i=0;i<1000;i++){var candidate=new Matrix4f().perspective(.8f+i*.0001f,16f/9f,.05f,512f).rotateY(i*.001f);state.capture(candidate,true,false);check(state.matches(new Matrix4f(candidate),true,false),"Exact projection rejected");candidate.m20(candidate.m20()+.00001f);check(!state.matches(candidate,true,false),"Subtle matrix change missed");}
  Throwable[] error={null};Thread other=new Thread(()->{try{state.capture(original,true,false);}catch(Throwable e){error[0]=e;}});other.start();other.join();check(error[0] instanceof IllegalStateException,"Off-owner snapshot write allowed");
  check(state.captures()==1003,"Capture lifecycle count changed");System.out.println("EXACT_FRUSTUM_FOV_ASPECT_ROTATION_MODE_COPY_OWNER_1000_PASS");
 }
}'''
variants=[('production',source,True),('projection_check_lost',source.replace('&& this.matrix.equals(current)','&& true'),False),('mode_check_lost',source.replace('this.smartCull==smartCull && this.spectator==spectator','true'),False)]
results=[]
with tempfile.TemporaryDirectory(prefix='mvh-frustum-') as folder:
    base=Path(folder);jar=args.joml
    if jar is None:jar=base/'joml.jar';jar.write_bytes(urllib.request.urlopen('https://repo.maven.apache.org/maven2/org/joml/joml/1.10.5/joml-1.10.5.jar',timeout=60).read())
    assert hashlib.sha256(jar.read_bytes()).hexdigest()==PIN
    for label,code,expected in variants:
        root=base/label;root.mkdir();sources=[]
        for name,value in [('net/vulkanmod/render/chunk/frustum/FrustumVisibilityState.java',code),('com/mojang/blaze3d/systems/RenderSystem.java','package com.mojang.blaze3d.systems;public class RenderSystem{static final Thread owner=Thread.currentThread();public static void assertOnRenderThread(){if(Thread.currentThread()!=owner)throw new IllegalStateException("Wrong owner");}}'),('FrustumProbe.java',probe)]:
            path=root/name;path.parent.mkdir(parents=True,exist_ok=True);path.write_text(value);sources.append(str(path))
        classes=root/'classes';subprocess.run(['javac','--release','17','-cp',str(jar),'-d',str(classes),*sources],check=True,capture_output=True,text=True)
        result=subprocess.run(['java','-ea','-cp',str(classes)+os.pathsep+str(jar),'FrustumProbe'],capture_output=True,text=True)
        assert (result.returncode==0)==expected,(label,result.stdout,result.stderr)
        results.append({'variant':label,'semantic_regression_passed':result.returncode==0,'expected_pass':expected})
report={'status':'PASS','joml_sha256':PIN,'scope':'Actual production visibility state with real JOML and modeled render owner; exact projection/mode changes, copy ownership, reset and invalid/non-owner rejection. Actual in-game FOV/terrain parity remains separate.','results':results}
if args.report:args.report.parent.mkdir(parents=True,exist_ok=True);args.report.write_text(json.dumps(report,indent=2)+'\n')
print('FRUSTUM_VISIBILITY_PRODUCTION_NEGATIVE_CONTROLS_PASS',len(results))
