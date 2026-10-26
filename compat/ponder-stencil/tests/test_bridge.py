"""Actual bridge selector, render ownership, lazy GL linkage and original error identity."""
from pathlib import Path
import argparse,json,subprocess,tempfile
H=Path(__file__).resolve().parents[1]
PROBE='''
import mvhpondercompat.NativeStencilBridge;
import net.vulkanmod.compat.UniversalRendererGate;
import net.vulkanmod.vulkan.VRenderSystem;
import probe.Trace;
public class BridgeProbe {
 public static void main(String[] ignored){
  NativeStencilBridge.glEnable(2960);NativeStencilBridge.glDisable(2960);
  if(!Trace.calls.equals("vk+;vk-;")||Trace.glLoaded)throw new AssertionError("Native route must not initialize GL");
  try{NativeStencilBridge.glEnable(3042);throw new AssertionError("Unknown capability");}catch(IllegalArgumentException expected){}
  com.mojang.blaze3d.systems.RenderSystem.owner=false;
  try{NativeStencilBridge.glDisable(2960);throw new AssertionError("Wrong owner");}catch(IllegalStateException expected){}
  com.mojang.blaze3d.systems.RenderSystem.owner=true;
  RuntimeException fault=new RuntimeException("same native fault");Trace.fault=fault;
  try{NativeStencilBridge.glEnable(2960);throw new AssertionError("Fault swallowed");}catch(RuntimeException actual){if(actual!=fault)throw new AssertionError("Native fault identity");}
  Trace.fault=null;UniversalRendererGate.nativeMode=false;
  NativeStencilBridge.glEnable(2960);NativeStencilBridge.glDisable(2960);
  if(!Trace.glLoaded||!Trace.calls.equals("vk+;vk-;gl+2960;gl-2960;"))throw new AssertionError("Original fallback");
  Trace.fault=fault;
  try{NativeStencilBridge.glDisable(2960);throw new AssertionError("GL fault swallowed");}catch(RuntimeException actual){if(actual!=fault)throw new AssertionError("GL fault identity");}
  Trace.fault=null;long[] counts=NativeStencilBridge.callCounts();if(counts[0]!=2||counts[1]!=2)throw new AssertionError("Only successful calls counted");
  UniversalRendererGate.nativeMode=true;NativeStencilBridge.glEnable(2960);
  if(!Trace.calls.endsWith("vk+;"))throw new AssertionError("Selector route");
 }
}
'''
def main():
 ap=argparse.ArgumentParser();ap.add_argument('--report',type=Path);a=ap.parse_args()
 body=(H/'src/main/java/mvhpondercompat/NativeStencilBridge.java').read_text()
 variants=[('production',body,True),('capability_guard_removed',body.replace('if(capability!=0x0B90)','if(false)'),False),('owner_guard_removed',body.replace('RenderSystem.assertOnRenderThread();',''),False),('native_path_initializes_gl',body.replace('check(capability);','check(capability); Object eager=Original.ENABLE;'),False),('selector_stuck_on_native',body.replace('UniversalRendererGate.vulkanRendererEnabled()','true'),False)]
 results=[]
 for label,code,expected in variants:
  with tempfile.TemporaryDirectory(prefix='mvh-ponder-stencil-') as tmp:
   root=Path(tmp);sources=[]
   fixtures={'mvhpondercompat/NativeStencilBridge.java':code,'BridgeProbe.java':PROBE,
    'probe/Trace.java':'package probe;public final class Trace{public static String calls="";public static boolean glLoaded;public static RuntimeException fault;public static void add(String s){if(fault!=null)throw fault;calls+=s;}}',
    'com/mojang/blaze3d/systems/RenderSystem.java':'package com.mojang.blaze3d.systems;public final class RenderSystem{public static boolean owner=true;public static void assertOnRenderThread(){if(!owner)throw new IllegalStateException("owner");}}',
    'net/vulkanmod/compat/UniversalRendererGate.java':'package net.vulkanmod.compat;public final class UniversalRendererGate{public static boolean nativeMode=true;public static boolean vulkanRendererEnabled(){return nativeMode;}}',
    'net/vulkanmod/vulkan/VRenderSystem.java':'package net.vulkanmod.vulkan;import probe.Trace;public final class VRenderSystem{public static void enableStencilTest(){Trace.add("vk+;");}public static void disableStencilTest(){Trace.add("vk-;");}}',
    'org/lwjgl/opengl/GL11.java':'package org.lwjgl.opengl;import probe.Trace;public final class GL11{static{Trace.glLoaded=true;}public static void glEnable(int c){Trace.add("gl+"+c+";");}public static void glDisable(int c){Trace.add("gl-"+c+";");}}'}
   for name,text in fixtures.items():
    p=root/name;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(text);sources.append(str(p))
   classes=root/'classes';subprocess.run(['javac','--release','17','-d',str(classes),*sources],check=True,capture_output=True)
   trial=subprocess.run(['java','-ea','-cp',str(classes),'BridgeProbe'],capture_output=True,text=True,timeout=30);passed=trial.returncode==0
   assert passed==expected,(label,trial.stdout,trial.stderr)
   if not expected:assert 'AssertionError' in trial.stderr,'Control must reject behavior'
   results.append({'variant':label,'behavior_passed':passed,'expected':expected})
 report={'passed':True,'variants':results,'scope':'Production bridge against renderer/owner/fault fixtures; known original GL linkage is lazy, exact stencil capability, original error identity. Actual GPU and Ponder default-method tests remain separate.'}
 if a.report:a.report.parent.mkdir(parents=True,exist_ok=True);a.report.write_text(json.dumps(report,indent=2)+'\n')
 print('PONDER_STENCIL_BRIDGE_CONTRACT_PASS',len(variants))
if __name__=='__main__':main()
