"""Compile the actual replacement body and exercise Forge callback metadata/order."""
from pathlib import Path
import argparse,json,subprocess,tempfile
I=Path(__file__).resolve().parents[1]

def main():
    p=argparse.ArgumentParser();p.add_argument('--report',type=Path);a=p.parse_args()
    text=(I/'overlay/forge/src/main/java/net/vulkanmod/mixin/chunk/LevelRendererMixin.java').read_text()
    start=text.index('private void renderSectionLayer(');opening=text.index('{',start);depth=1;end=opening+1
    while depth:
        if text[end]=='{':depth+=1
        elif text[end]=='}':depth-=1
        end+=1
    body=text[opening+1:end-1]
    with tempfile.TemporaryDirectory(prefix='mvh-forge-stage-') as tmp:
        root=Path(tmp);sources=[]
        def source(name,value):
            f=root/name;f.parent.mkdir(parents=True,exist_ok=True);f.write_text(value);sources.append(str(f))
        source('net/minecraftforge/client/ForgeHooksClient.java','''package net.minecraftforge.client;public class ForgeHooksClient{public static Object expectedRenderer,expectedPose,expectedProjection,expectedCamera,expectedFrustum;public static int calls;public static void dispatchRenderStage(Object type,Object renderer,Object pose,Object projection,int tick,Object camera,Object frustum){if(renderer!=expectedRenderer||pose!=expectedPose||projection!=expectedProjection||tick!=73||camera!=expectedCamera||frustum!=expectedFrustum)throw new AssertionError("Forge callback metadata changed");calls++;test.Probe.events.add("stage");}}''')
        prefix='''package test;import java.util.*;import net.minecraftforge.client.ForgeHooksClient;
public class Probe extends LevelRenderer {
 public static List<String> events=new ArrayList<>();private WorldRenderer worldRenderer=new WorldRenderer();private int f_109477_=73;private Minecraft f_109461_=new Minecraft();private Object frustum=new Object();public Object getFrustum(){return frustum;}
 private void invoke(RenderType renderType,PoseStack poseStack,double camX,double camY,double camZ,Matrix4f projectionMatrix,CallbackInfo ci){'''
        suffix='''}
 public static void main(String[]args){Probe p=new Probe();RenderType type=new RenderType();PoseStack pose=new PoseStack();Matrix4f projection=new Matrix4f();CallbackInfo ci=new CallbackInfo();ForgeHooksClient.expectedRenderer=p;ForgeHooksClient.expectedPose=pose;ForgeHooksClient.expectedProjection=projection;ForgeHooksClient.expectedCamera=p.f_109461_.gameRenderer.getMainCamera();ForgeHooksClient.expectedFrustum=p.frustum;p.invoke(type,pose,1,2,3,projection,ci);if(!events.equals(List.of("native","dynamic","schematic","stage","cancel"))||ForgeHooksClient.calls!=1||!ci.canceled)throw new AssertionError("missing, duplicate or misplaced Forge stage callback "+events);System.out.println("ACTUAL_NATIVE_BODY_FORGE_STAGE_ONCE_METADATA_ORDER_PASS");}
}
class LevelRenderer{}class RenderType{}class PoseStack{}class Matrix4f{}
class Minecraft{GameRenderer gameRenderer=new GameRenderer();}class GameRenderer{Object camera=new Object();Object getMainCamera(){return camera;}}
class WorldRenderer{void renderSectionLayer(Object t,Object p,double x,double y,double z,Object m){Probe.events.add("native");}}
class DynamicLightsBridge{static void updateAllDynamicLights(Object p){Probe.events.add("dynamic");}}
class LitematicaBridge{static void afterRenderChunkLayer(Object t,Object p,Object m){Probe.events.add("schematic");}}
class CallbackInfo{boolean canceled;void cancel(){canceled=true;Probe.events.add("cancel");}}
'''
        source('test/Probe.java',prefix+body+suffix)
        classes=root/'classes';subprocess.run(['javac','--release','17','-d',str(classes),*sources],check=True)
        subprocess.run(['java','-ea','-cp',str(classes),'test.Probe'],check=True,timeout=30)
    if a.report:
        a.report.parent.mkdir(parents=True,exist_ok=True);a.report.write_text(json.dumps({'passed':True,'scope':'Actual native renderSectionLayer replacement body, with API type stubs: terrain before exactly one Forge callback, original tick/camera/frustum/pose/projection identity, preserved existing compatibility bridges and cancel preventing vanilla double-dispatch. Real Forge stage/grass GPU rendering separate.'},indent=2)+'\n')

if __name__=='__main__':main()
