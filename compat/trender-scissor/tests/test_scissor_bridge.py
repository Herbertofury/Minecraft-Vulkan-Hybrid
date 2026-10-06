"""Exercise actual helper dispatch, coordinate identity, render ownership and failures."""
from pathlib import Path
import argparse,json,subprocess,tempfile
H=Path(__file__).resolve().parents[1]
p=argparse.ArgumentParser();p.add_argument('--report',type=Path);a=p.parse_args()
code=(H/'src/main/java/mvhtrendercompat/NativeScissorBridge.java').read_text()
manager='''package com.mojang.blaze3d.platform;public class GlStateManager{public static boolean enabled;public static int[] box;public static RuntimeException fault;public static void _enableScissorTest(){if(fault!=null)throw fault;enabled=true;}public static void _disableScissorTest(){if(fault!=null)throw fault;enabled=false;}public static void _scissorBox(int x,int y,int w,int h){if(fault!=null)throw fault;box=new int[]{x,y,w,h};}}'''
owner='''package com.mojang.blaze3d.systems;public class RenderSystem{static final Thread owner=Thread.currentThread();public static void assertOnRenderThread(){if(Thread.currentThread()!=owner)throw new IllegalStateException("Wrong owner");}}'''
probe='''import mvhtrendercompat.NativeScissorBridge;import com.mojang.blaze3d.platform.GlStateManager;import java.util.*;
public class ScissorProbe{static void check(boolean b,String s){if(!b)throw new AssertionError(s);}public static void main(String[]a)throws Exception{
 for(int i=0;i<1000;i++){NativeScissorBridge.glEnable(3089);check(GlStateManager.enabled,"Enable dropped");int[] expected={i-500,1080-i,i%400,i%200};NativeScissorBridge.glScissor(expected[0],expected[1],expected[2],expected[3]);check(Arrays.equals(expected,GlStateManager.box),"Coordinates changed");NativeScissorBridge.glDisable(3089);check(!GlStateManager.enabled,"Disable dropped");}
 for(int cap:new int[]{0,3042,2929,3088,3090}){try{NativeScissorBridge.glEnable(cap);throw new AssertionError("Unknown capability accepted");}catch(IllegalArgumentException expected){}try{NativeScissorBridge.glDisable(cap);throw new AssertionError("Unknown capability accepted");}catch(IllegalArgumentException expected){}}
 RuntimeException original=new RuntimeException("Original state failure");GlStateManager.fault=original;try{NativeScissorBridge.glScissor(1,2,3,4);throw new AssertionError("Failure swallowed");}catch(RuntimeException e){check(e==original,"Failure identity changed");}GlStateManager.fault=null;
 Throwable[] failure={null};Thread other=new Thread(()->{try{NativeScissorBridge.glScissor(1,2,3,4);}catch(Throwable e){failure[0]=e;}});other.start();other.join();check(failure[0] instanceof IllegalStateException,"Off-owner call accepted");check(Arrays.equals(NativeScissorBridge.callCounts(),new long[]{1000,1000,1000}),"Only successful owner calls counted");System.out.println("SCISSOR_1000_EXACT_STATE_COORDINATES_OWNER_FAILURE_PASS");}}
'''
variants=[('production',code,True),('enable_dropped',code.replace('GlStateManager._enableScissorTest();',''),False),('owner_assertions_removed',code.replace('RenderSystem.assertOnRenderThread();',''),False)]
results=[]
with tempfile.TemporaryDirectory(prefix='mvh-tr-scissor-') as tmp:
    for label,text,expected in variants:
        root=Path(tmp)/label;root.mkdir();sources=[]
        for name,value in [('mvhtrendercompat/NativeScissorBridge.java',text),('com/mojang/blaze3d/platform/GlStateManager.java',manager),('com/mojang/blaze3d/systems/RenderSystem.java',owner),('ScissorProbe.java',probe)]:
            f=root/name;f.parent.mkdir(parents=True,exist_ok=True);f.write_text(value);sources.append(str(f))
        classes=root/'classes';subprocess.run(['javac','--release','17','-d',str(classes),*sources],check=True,capture_output=True,text=True)
        r=subprocess.run(['java','-ea','-cp',str(classes),'ScissorProbe'],capture_output=True,text=True);assert (r.returncode==0)==expected,(label,r.stdout,r.stderr)
        results.append({'variant':label,'passed':r.returncode==0,'expected_pass':expected})
report={'status':'PASS','scope':'Actual helper with modeled renderer state, 1000 unchanged coordinate/state cycles, unknown capabilities, render owner and original failure propagation. Actual Vulkan UI/game and FPS remain separate.','variants':results}
if a.report:a.report.parent.mkdir(parents=True,exist_ok=True);a.report.write_text(json.dumps(report,indent=2)+'\n')
print('TR_RENDER_SCISSOR_HELPER_PRODUCTION_AND_NEGATIVE_CONTROLS_PASS',len(results))
