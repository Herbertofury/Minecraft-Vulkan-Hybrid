"""Test actual uniform update order and Vulkan clip conversion with real JOML."""
from pathlib import Path
import argparse,hashlib,json,os,subprocess,tempfile,urllib.request
I=Path(__file__).resolve().parents[1]
URL='https://repo.maven.apache.org/maven2/org/joml/joml/1.10.5/joml-1.10.5.jar'
PIN='cac9f22f83a7aa33eebda73c16ff5261e3cb4911b6bafcf4c79ea486099d0c9a'

def main():
    p=argparse.ArgumentParser();p.add_argument('--joml',type=Path);p.add_argument('--report',type=Path);a=p.parse_args()
    with tempfile.TemporaryDirectory(prefix='mvh-shader-matrices-') as tmp:
        root=Path(tmp);jar=a.joml
        if jar is None:jar=root/'joml.jar';jar.write_bytes(urllib.request.urlopen(URL,timeout=60).read())
        assert hashlib.sha256(jar.read_bytes()).hexdigest()==PIN,'JOML reference changed'
        sources=[]
        def source(name,text):
            f=root/name;f.parent.mkdir(parents=True,exist_ok=True);f.write_text(text);sources.append(str(f))
        source('com/mojang/blaze3d/systems/RenderSystem.java','package com.mojang.blaze3d.systems;public class RenderSystem{public static void assertOnRenderThread(){}}')
        source('net/minecraft/client/renderer/ShaderInstance.java','''package net.minecraft.client.renderer;import org.joml.Matrix4f;public class ShaderInstance{public MatrixUniform MODEL_VIEW_MATRIX=new MatrixUniform(),PROJECTION_MATRIX=new MatrixUniform();public float wind=3.7f;public int applies;public void apply(){applies++;if(MODEL_VIEW_MATRIX!=null)MODEL_VIEW_MATRIX.set(new Matrix4f().translation(99,0,0));if(PROJECTION_MATRIX!=null)PROJECTION_MATRIX.set(new Matrix4f());}public static class MatrixUniform{public Matrix4f value=new Matrix4f();public void set(Matrix4f matrix){value.set(matrix);}}}''')
        actual=root/'LegacyShaderMatrices.java';actual.write_bytes((I/'overlay/forge/src/main/java/net/vulkanmod/render/LegacyShaderMatrices.java').read_bytes());sources.append(str(actual))
        source('MatrixProbe.java','''import net.vulkanmod.render.LegacyShaderMatrices;import net.minecraft.client.renderer.ShaderInstance;import org.joml.*;public class MatrixProbe{
 static void check(boolean v,String s){if(!v)throw new AssertionError(s);}static void near(float a,float b,String s){check(java.lang.Math.abs(a-b)<0.0001f,s+" "+a+" "+b);}
 public static void main(String[]a){LegacyShaderMatrices helper=new LegacyShaderMatrices();ShaderInstance shader=new ShaderInstance();Matrix4f view=new Matrix4f().rotateY(.35f).translate(-2,3,-5),projection=new Matrix4f().perspective(1.1f,16f/9f,.05f,512f);Matrix4f beforeView=new Matrix4f(view),beforeProjection=new Matrix4f(projection);helper.apply(shader,view,projection);
 check(shader.applies==1&&shader.MODEL_VIEW_MATRIX.value.equals(view),"apply overwrote supplied draw view");check(shader.wind==3.7f,"mod-owned animation uniform changed");check(view.equals(beforeView)&&projection.equals(beforeProjection),"caller matrices mutated");
 for(int i=1;i<=1000;i++){Vector4f p=new Vector4f((i%11-5)*.013f,(i%7-3)*.017f,-.05f-i*.37f,1),gl=projection.transform(new Vector4f(p)),vk=shader.PROJECTION_MATRIX.value.transform(new Vector4f(p));near(vk.x,gl.x,"clip x");near(vk.y,gl.y,"clip y");near(vk.z,(gl.z+gl.w)*.5f,"Vulkan clip depth");near(vk.w,gl.w,"clip w");}
 Matrix4f saved=new Matrix4f(shader.PROJECTION_MATRIX.value);ShaderInstance other=new ShaderInstance();helper.apply(other,new Matrix4f(),new Matrix4f().ortho(-3,3,-4,4,.1f,100));check(shader.PROJECTION_MATRIX.value.equals(saved),"scratch aliases previous uniform");other.MODEL_VIEW_MATRIX=null;other.PROJECTION_MATRIX=null;helper.apply(other,view,projection);check(other.applies==2,"missing optional uniforms skipped shader apply");
 System.out.println("ACTUAL_SHADER_UNIFORM_DRAW_ORDER_CLIP_DEPTH_1000_POINTS_CALLER_STATE_PASS");}}''')
        classes=root/'classes';subprocess.run(['javac','--release','17','-cp',str(jar),'-d',str(classes),*sources],check=True)
        subprocess.run(['java','-ea','-cp',str(classes)+os.pathsep+str(jar),'MatrixProbe'],check=True,timeout=30)
        digest=hashlib.sha256(jar.read_bytes()).hexdigest()
    if a.report:
        a.report.parent.mkdir(parents=True,exist_ok=True);a.report.write_text(json.dumps({'passed':True,'joml_sha256':digest,'scope':'Actual production shader-matrix helper with real JOML and modeled ShaderInstance uniforms: apply order, Vulkan clip depth for 1000 points, original caller and mod-owned uniform preservation, optional uniforms and scratch ownership. GPU visual checks remain separate.'},indent=2)+'\n')

if __name__=='__main__':main()
