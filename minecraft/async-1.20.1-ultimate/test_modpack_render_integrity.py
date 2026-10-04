#!/usr/bin/env python3
"""Run production readback/state methods and biome tint code against causal failures."""
import argparse, hashlib, json, subprocess, tempfile
from pathlib import Path
from test_swapchain_submissions import method

def run_java(files, main):
    with tempfile.TemporaryDirectory() as tmp:
        root = Path(tmp)
        for name, body in files.items():
            p = root / name; p.parent.mkdir(parents=True, exist_ok=True); p.write_text(body)
        subprocess.run(['java','--module','jdk.compiler/com.sun.tools.javac.Main','-d',str(root/'classes')]
                       + [str(p) for p in root.rglob('*.java')], check=True)
        return subprocess.check_output(['java','-ea','-cp',str(root/'classes'),main], text=True, stderr=subprocess.PIPE).strip()

STUBS = {
'net/minecraft/core/BlockPos.java': '''package net.minecraft.core; public class BlockPos {protected int x,y,z;public BlockPos(int x,int y,int z){this.x=x;this.y=y;this.z=z;}public int getX(){return x;}public int getY(){return y;}public int getZ(){return z;}public static class MutableBlockPos extends BlockPos {public MutableBlockPos(){super(0,0,0);}public MutableBlockPos set(int x,int y,int z){this.x=x;this.y=y;this.z=z;return this;}}}''',
'net/minecraft/core/Holder.java': '''package net.minecraft.core;public class Holder<T>{private final T v;public Holder(T v){this.v=v;}public T value(){return v;}}''',
'net/minecraft/world/level/ColorResolver.java': '''package net.minecraft.world.level;import net.minecraft.world.level.biome.Biome;public interface ColorResolver{int getColor(Biome b,double x,double z);}''',
'net/minecraft/world/level/biome/Biome.java': '''package net.minecraft.world.level.biome;public class Biome{public final int y;public Biome(int y){this.y=y;}public int getGrassColor(double x,double z){return y<<16;}public int getFoliageColor(){return y<<8;}public int getWaterColor(){return y;}}''',
'net/minecraft/world/level/Level.java': '''package net.minecraft.world.level;import net.minecraft.core.*;import net.minecraft.world.level.biome.Biome;public class Level{public int offset;public Holder<Biome> getBiome(BlockPos p){return new Holder<>(new Biome(p.getY()+offset));}}''',
'net/minecraft/client/renderer/BiomeColors.java': '''package net.minecraft.client.renderer;import net.minecraft.world.level.ColorResolver;public class BiomeColors{public static final ColorResolver GRASS_COLOR_RESOLVER=(b,x,z)->b.getGrassColor(x,z),FOLIAGE_COLOR_RESOLVER=(b,x,z)->b.getFoliageColor(),WATER_COLOR_RESOLVER=(b,x,z)->b.getWaterColor();}''',
'net/vulkanmod/render/chunk/WorldRenderer.java': '''package net.vulkanmod.render.chunk;import net.minecraft.world.level.Level;public class WorldRenderer{public static final Level level=new Level();public static Level getLevel(){return level;}}''',
'net/minecraft/client/Minecraft.java': '''package net.minecraft.client;public class Minecraft{public static Minecraft getInstance(){throw new AssertionError("must use build's captured radius");}}''',
'net/minecraft/util/Mth.java': 'package net.minecraft.util;public class Mth{}',
}

PROBE = r'''
import net.minecraft.core.*;import net.minecraft.world.level.*;import net.minecraft.world.level.biome.*;
import net.minecraft.client.renderer.BiomeColors;import net.vulkanmod.render.chunk.build.TintCache;
import net.vulkanmod.render.chunk.WorldRenderer;import com.axalotl.async.forge.client.*;
public class TintProbe {
 static void check(boolean v,String m){if(!v)throw new AssertionError(m);}
 public static class Snapshot {public Holder<Biome> getBiome(int x,int y,int z){return new Holder<>(new Biome(y+40));}}
 public static class Slice {private final Snapshot biomeSlice=new Snapshot();}
 static class Resolver implements ColorResolver {final int offset;int calls;Resolver(int o){offset=o;}public int getColor(Biome b,double x,double z){calls++;return ((b.y+offset)<<16)|(((int)x&255)<<8)|((int)z&255);}public boolean equals(Object o){return o instanceof Resolver;}public int hashCode(){return 1;}}
 public static void main(String[] args){
  TintCache cache=new TintCache();cache.init(0,0,0,0);
  check(cache.getColor(new BlockPos(1,1,1),BiomeColors.GRASS_COLOR_RESOLVER)==1<<16,"first height");
  check(cache.getColor(new BlockPos(1,7,1),BiomeColors.GRASS_COLOR_RESOLVER)==7<<16,"layers aliased");
  Resolver a=new Resolver(0),b=new Resolver(10);
  check(cache.getColor(new BlockPos(2,7,3),a)==(7<<16|2<<8|3),"original custom resolver lost");
  check(cache.getColor(new BlockPos(2,7,3),b)==(17<<16|2<<8|3),"resolver identity collapsed");
  int calls=a.calls;cache.getColor(new BlockPos(3,7,4),a);check(a.calls==calls,"cache does not reuse layer");
  WorldRenderer.level.offset=20;cache.init(0,0,0,0);
  check(cache.getColor(new BlockPos(2,7,3),a)==(27<<16|2<<8|3),"stale pooled section colors");
  WorldRenderer.level.offset=0;
  for(int radius:new int[]{1,2,7}){
   cache.init(radius,0,0,0);
   for(int y:new int[]{0,7,15}){
    BlockPos p=new BlockPos(8,y,8);int actual=cache.getColor(p,a)&0xffffff;
    int expected=ModdedBiomeTint.blend(p,a,radius,q->new Biome(q.getY()));
    check(actual==expected,"coordinate-dependent same-biome blending");
   }
  }
  var snapshot=RubidiumBiomeAccess.snapshot(new Slice());
  check(ModdedBiomeTint.blend(new BlockPos(4,3,6),a,0,snapshot)==(43<<16|4<<8|6),"snapshot biome lookup");
  ColorResolver nonlinear=(bi,x,z)->((int)(x*x*z+bi.y*37)&255)<<16|((int)(z*z+x*17)&255)<<8|((int)(x*z*23)&255);
  for(int radius:new int[]{1,2,7}){
   int width=16+2*radius;int[] colors=new int[width*width];
   for(int z=0;z<width;z++)for(int x=0;x<width;x++)colors[x+z*width]=nonlinear.getColor(new Biome(5),x-radius,z-radius);
   ModdedBiomeTint.blur(colors,width,radius);
   for(int z=0;z<16;z++)for(int x=0;x<16;x++)check((colors[x+radius+(z+radius)*width]&0xffffff)==ModdedBiomeTint.blend(new BlockPos(x,5,z),nonlinear,radius,q->new Biome(5)),"integral blend differs from vanilla average");
  }
  RuntimeException original=new IllegalArgumentException("resolver failure");
  try{ModdedBiomeTint.blend(new BlockPos(0,0,0),(bi,x,z)->{throw original;},0,snapshot);throw new AssertionError("failure hidden");}catch(RuntimeException e){check(e==original,"resolver failure changed");}
  System.out.println("CUSTOM_TINT_SNAPSHOT_HEIGHT_IDENTITY_REUSE_BLEND_PASSED");
 }
}
'''

STATE = r'''
public class StateProbe {
 static StateProbe INSTANCE=new StateProbe();boolean recordingCmds=true;float depthBiasUnits,depthBiasFactor;VkCommandBuffer currentCmdBuffer=new VkCommandBuffer();
 static float units,factor,line;static int calls;
 static void vkCmdSetDepthBias(VkCommandBuffer b,float u,float c,float f){units=u;factor=f;calls++;}
 static void vkCmdSetLineWidth(VkCommandBuffer b,float v){line=v;}
METHODS
 public static void main(String[] args){
  setDepthBias(2.5f,-3);units=0;factor=0;resetDynamicState(INSTANCE.currentCmdBuffer);
  if(units!=2.5f||factor!=-3||line!=1)throw new AssertionError("readback erased bias or line state");
  INSTANCE.recordingCmds=false;int before=calls;setDepthBias(0,0);if(calls!=before)throw new AssertionError("command outside recording");
  INSTANCE.recordingCmds=true;resetDynamicState(INSTANCE.currentCmdBuffer);if(units!=0||factor!=0)throw new AssertionError("disabled offset not preserved");
  System.out.println("COMMAND_RESTART_PRESERVES_CURRENT_STATE_PASSED");
 }
}class VkCommandBuffer{}
'''

SPAWN = r'''
import java.util.*;import java.util.function.*;
public class SpawnProbe {
 List<LevelChunk> harimt$randomTickChunks=new ArrayList<>();List<Runnable> harimt$spawnTasks=new ArrayList<>();Level level=new Level();
METHOD
 public static void main(String[] args){
  SpawnProbe phase=new SpawnProbe();Thread owner=Thread.currentThread();int[] completed={0};
  com.axalotl.async.common.AsyncCommon.HARICHUNK=true;
  for(int i=0;i<10000;i++)phase.harimt$spawnTasks.add(()->{if(Thread.currentThread()!=owner)throw new AssertionError("off-owner spawn");completed[0]++;});
  phase.harimt$flushParallelChunkWork(new CallbackInfo());
  if(completed[0]!=10000||!phase.harimt$spawnTasks.isEmpty()||ParallelProcessor.dispatches!=0)throw new AssertionError("lost, duplicated, or handed-off C2ME spawns");
  com.axalotl.async.common.AsyncCommon.HARICHUNK=false;
  for(int i=0;i<10000;i++)phase.harimt$spawnTasks.add(()->{if(Thread.currentThread()!=owner)throw new AssertionError("Aether spawn predicate requested chunk off-owner");completed[0]++;});
  phase.harimt$flushParallelChunkWork(new CallbackInfo());
  if(completed[0]!=20000||ParallelProcessor.dispatches!=0)throw new AssertionError("lost, duplicated, or dispatched ordinary Forge spawn callbacks");
  com.axalotl.async.common.AsyncCommon.HARICHUNK=true;
  RuntimeException original=new IllegalArgumentException("native spawn failure");phase.harimt$spawnTasks.add(()->{throw original;});
  try{phase.harimt$flushParallelChunkWork(new CallbackInfo());throw new AssertionError("spawn failure hidden");}catch(RuntimeException e){if(e!=original)throw new AssertionError("original spawn failure changed");}
  System.out.println("FORGE_20000_ORIGINAL_OWNER_SPAWNS_PASSED");
 }
}class CallbackInfo{}class Inject{}class At{}
class GameRules{static final int RULE_RANDOMTICKING=1;int getInt(int x){return 3;}}
class Pos{int x,z;}class Chunks{boolean hasChunk(int x,int z){return true;}}
class Level{GameRules getGameRules(){return new GameRules();}Chunks getChunkSource(){return new Chunks();}void tickChunk(LevelChunk c,int s){}}
class LevelChunk{Level getLevel(){return new Level();}Pos getPos(){return new Pos();}}
class ParallelProcessor{static int dispatches;static <T> void forEachParallel(Level l,List<T> xs,Consumer<T> fn){dispatches++;Thread worker=new Thread(()->{for(T x:xs)fn.accept(x);},"spawn-worker-negative-control");worker.setUncaughtExceptionHandler((t,e)->failure=e);worker.start();try{worker.join();}catch(InterruptedException e){throw new RuntimeException(e);}if(failure!=null){Throwable cause=failure;failure=null;if(cause instanceof RuntimeException r)throw r;if(cause instanceof Error r)throw r;throw new RuntimeException(cause);}}static volatile Throwable failure;}
'''

def main():
    p=argparse.ArgumentParser();p.add_argument('source',type=Path);p.add_argument('--report',type=Path,required=True);a=p.parse_args()
    java=a.source/'forge/src/main/java';renderer=(java/'net/vulkanmod/vulkan/Renderer.java').read_text()
    fb=(java/'net/vulkanmod/vulkan/framebuffer/Framebuffer.java').read_text()
    creation=method(fb,'createImages')
    assert '.setUsage(VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT | VK_IMAGE_USAGE_SAMPLED_BIT | VK_IMAGE_USAGE_TRANSFER_SRC_BIT)' in creation
    swapchain=(java/'net/vulkanmod/vulkan/framebuffer/SwapChain.java').read_text()
    start=swapchain.index('            int requiredImageUsage = ')
    end=swapchain.index('            createInfo.imageUsage(requiredImageUsage);',start)+len('            createInfo.imageUsage(requiredImageUsage);')
    usage=swapchain[start:end]
    surface_probe='''public class SurfaceProbe {
      static final int VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT=16,VK_IMAGE_USAGE_SAMPLED_BIT=4,VK_IMAGE_USAGE_TRANSFER_SRC_BIT=1;
      static class Caps{int supported;Caps(int s){supported=s;}int supportedUsageFlags(){return supported;}}
      static class Surface{Caps capabilities;Surface(int s){capabilities=new Caps(s);}}
      static class Info{int usage;void imageUsage(int flags){usage=flags;}}
      static int create(int flags){Surface surfaceProperties=new Surface(flags);Info createInfo=new Info();__PRODUCTION_BODY__ return createInfo.usage;}
      public static void main(String[] args){if(create(0x1f)!=0x15)throw new AssertionError("main image missing readback usage");try{create(0x14);throw new AssertionError("unsupported image usage requested");}catch(IllegalStateException expected){}System.out.println("SURFACE_READBACK_CAPABILITIES_PASSED");}
    }'''.replace('__PRODUCTION_BODY__',usage)
    assert run_java({'SurfaceProbe.java':surface_probe},'SurfaceProbe')=='SURFACE_READBACK_CAPABILITIES_PASSED'
    resume=method(renderer,'flushForReadback')
    assert resume.index('resetDynamicState(currentCmdBuffer)') < resume.index('resumeFramebuffer.beginRenderPass')
    assert run_java({'StateProbe.java':STATE.replace('METHODS',method(renderer,'resetDynamicState')+'\n'+method(renderer,'setDepthBias'))},'StateProbe')=='COMMAND_RESTART_PRESERVES_CURRENT_STATE_PASSED'
    files=dict(STUBS)
    for name in ['net/vulkanmod/render/chunk/build/TintCache.java','net/vulkanmod/render/chunk/build/biome/BoxBlur.java','com/axalotl/async/forge/client/ModdedBiomeTint.java','com/axalotl/async/forge/client/RubidiumBiomeAccess.java']:
        files[name]=(java/name).read_text()
    files['TintProbe.java']=PROBE
    assert run_java(files,'TintProbe')=='CUSTOM_TINT_SNAPSHOT_HEIGHT_IDENTITY_REUSE_BLEND_PASSED'
    spawn=(a.source/'common/src/main/java/com/axalotl/async/common/mixin/server/ServerChunkCacheMixin.java').read_text()
    assert run_java({'SpawnProbe.java':SPAWN.replace('METHOD',method(spawn,'harimt$flushParallelChunkWork')),
                     'com/axalotl/async/common/AsyncCommon.java':'package com.axalotl.async.common;public class AsyncCommon{public static boolean HARICHUNK;}'},'SpawnProbe')=='FORGE_20000_ORIGINAL_OWNER_SPAWNS_PASSED'
    old_spawn=method(spawn,'harimt$flushParallelChunkWork').replace('for (Runnable task : tasks) task.run();',
        'if (com.axalotl.async.common.AsyncCommon.HARICHUNK) { for (Runnable task : tasks) task.run(); } else { ParallelProcessor.forEachParallel(this.level, tasks, Runnable::run); }')
    try:
        run_java({'SpawnProbe.java':SPAWN.replace('METHOD',old_spawn),
                  'com/axalotl/async/common/AsyncCommon.java':'package com.axalotl.async.common;public class AsyncCommon{public static boolean HARICHUNK;}'},'SpawnProbe')
    except subprocess.CalledProcessError as failure:
        assert 'Aether spawn predicate requested chunk off-owner' in failure.stderr, failure.stderr
    else:
        raise AssertionError('Old off-owner natural spawn negative control did not fail')
    report={'passed':True,'framebuffer_readback_usage':True,'readback_restart_restores_bias_and_line_width':True,
            'swapchain_readback_usage_and_surface_capability_check':True,
            'custom_resolver_original_colors':True,'separate_height_layers':True,'resolver_identity_and_pooled_cache_invalidation':True,
            'snapshot_lookup_and_original_failures':True,'blend_radii':[0,1,2,7],
            'c2me_original_spawns_on_owner':10000,'ordinary_forge_original_spawns_on_owner':10000,'original_spawn_failures_surface':True,'old_non_c2me_spawn_negative_control':True,
            'renderer_sha256':hashlib.sha256(renderer.encode()).hexdigest(),
            'scope':'Production state/tint/snapshot Java code with Minecraft/Vulkan API doubles; real Rubidium mixin and native Vulkan validation are separate required gates.'}
    a.report.parent.mkdir(parents=True,exist_ok=True);a.report.write_text(json.dumps(report,indent=2)+'\n');print(json.dumps(report,indent=2))
if __name__=='__main__':main()
