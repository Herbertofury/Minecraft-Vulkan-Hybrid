#!/usr/bin/env python3
"""Compile the real section graph/queue; check complete visibility and prior holes."""
import argparse, json, shutil, subprocess, tempfile
from pathlib import Path

STUBS = {
    'org/jetbrains/annotations/NotNull.java': 'package org.jetbrains.annotations; public @interface NotNull {}',
    'org/joml/FrustumIntersection.java': 'package org.joml; public class FrustumIntersection { public static final int INTERSECT=0; }',
    'net/minecraft/core/BlockPos.java': 'package net.minecraft.core; public class BlockPos { public int getY(){return 0;} }',
    'net/minecraft/core/SectionPos.java': 'package net.minecraft.core; public class SectionPos { public static int sectionToBlockCoord(int a,int b){return a*16+b;} }',
    'net/minecraft/util/Mth.java': 'package net.minecraft.util; public class Mth { public static int floor(double v){return (int)Math.floor(v);} }',
    'net/minecraft/world/phys/Vec3.java': 'package net.minecraft.world.phys; public class Vec3 { public double x,y,z; }',
    'net/minecraft/client/Camera.java': 'package net.minecraft.client; public class Camera { public net.minecraft.core.BlockPos getBlockPosition(){return new net.minecraft.core.BlockPos();} public net.minecraft.world.phys.Vec3 getPosition(){return new net.minecraft.world.phys.Vec3();} }',
    'net/minecraft/client/Minecraft.java': 'package net.minecraft.client; public class Minecraft { public boolean smartCull=true; public static Minecraft getInstance(){return new Minecraft();} public Profiler getProfiler(){return new Profiler();} public static class Profiler {public void popPush(String s){} public void push(String s){} public void pop(){}} }',
    'net/minecraft/client/renderer/culling/Frustum.java': 'package net.minecraft.client.renderer.culling; public class Frustum implements net.vulkanmod.interfaces.FrustumMixed { public net.vulkanmod.render.chunk.frustum.VFrustum customFrustum(){return new net.vulkanmod.render.chunk.frustum.VFrustum();} }',
    'net/minecraft/world/level/Level.java': 'package net.minecraft.world.level; public class Level {public State getBlockState(net.minecraft.core.BlockPos p){return new State();} public Dimension dimensionType(){return new Dimension();} public int getMinBuildHeight(){return 0;} public int getMaxBuildHeight(){return 256;} public static class State {public boolean isSolidRender(Level l,net.minecraft.core.BlockPos p){return false;}} public static class Dimension {public boolean hasSkyLight(){return true;}}}',
    'net/vulkanmod/Initializer.java': 'package net.vulkanmod; public class Initializer {public static final Config CONFIG=new Config(); public static class Config {public int advCulling=2;}}',
    'net/vulkanmod/interfaces/FrustumMixed.java': 'package net.vulkanmod.interfaces; public interface FrustumMixed {net.vulkanmod.render.chunk.frustum.VFrustum customFrustum();}',
    'net/vulkanmod/render/chunk/frustum/VFrustum.java': 'package net.vulkanmod.render.chunk.frustum; public class VFrustum {public boolean positiveXOnly; public VFrustum offsetToFullyIncludeCameraCube(int n){return this;} public boolean testFrustum(int x,int y,int z,int xx,int yy,int zz){return !positiveXOnly || x>=0;}}',
    'net/vulkanmod/render/chunk/build/RenderRegionBuilder.java': 'package net.vulkanmod.render.chunk.build; public class RenderRegionBuilder {}',
    'net/vulkanmod/render/chunk/build/TaskDispatcher.java': 'package net.vulkanmod.render.chunk.build; public class TaskDispatcher {public String getStats(){return "fixture";}}',
    'net/vulkanmod/render/optimization/AdaptiveChunkUploadBudget.java': 'package net.vulkanmod.render.optimization; public class AdaptiveChunkUploadBudget {public static int chooseRebuildScheduleBudget(int n){return n;}}',
    'net/vulkanmod/render/chunk/util/AreaSetQueue.java': 'package net.vulkanmod.render.chunk.util; public class AreaSetQueue {public AreaSetQueue(int n){} public void add(net.vulkanmod.render.chunk.ChunkArea a){} public void clear(){}}',
    'net/vulkanmod/render/chunk/ChunkArea.java': 'package net.vulkanmod.render.chunk; public class ChunkArea {public final net.vulkanmod.render.chunk.util.ResettableQueue<RenderSection> sectionQueue=new net.vulkanmod.render.chunk.util.ResettableQueue<>(); public byte inFrustum(int i){return 0;}}',
    'net/vulkanmod/render/chunk/ChunkAreaManager.java': 'package net.vulkanmod.render.chunk; public class ChunkAreaManager {public int size=1;public void resetQueues(){}}',
    'net/vulkanmod/render/chunk/SectionGrid.java': 'package net.vulkanmod.render.chunk; public class SectionGrid {public ChunkAreaManager getChunkAreaManager(){return new ChunkAreaManager();} public void updateFrustumVisibility(net.vulkanmod.render.chunk.frustum.VFrustum f){} public RenderSection getSectionAtBlockPos(net.minecraft.core.BlockPos p){return null;} public RenderSection getSectionAtBlockPos(int x,int y,int z){return null;} public int getSectionCount(){return 0;}}',
    'net/vulkanmod/render/chunk/WorldRenderer.java': 'package net.vulkanmod.render.chunk; public class WorldRenderer {public net.vulkanmod.render.chunk.build.RenderRegionBuilder renderRegionCache; public static WorldRenderer getInstance(){return new WorldRenderer();} public int getRenderDistance(){return 4;} public void scheduleGraphUpdate(){}}',
    'net/vulkanmod/render/chunk/SectionTraversalPolicy.java': 'package net.vulkanmod.render.chunk; public class SectionTraversalPolicy {public static boolean shouldUseDirectionalCulling(boolean a,boolean b,boolean c){return a&&!b;}}',
    'net/vulkanmod/render/chunk/RenderSection.java': '''package net.vulkanmod.render.chunk;
public class RenderSection {
 public int xOffset,yOffset,zOffset,frustumIndex; public byte mainDir,sourceDirs,directions,directionChanges,steps,adjDirs;
 public long visibility; public RenderSection adjDown,adjUp,adjNorth,adjSouth,adjWest,adjEast;
 public boolean empty,dirty=true,blockEntity; public int builds; public byte visibilityDirs=63; private short frame=-1;
 public final ChunkArea area=new ChunkArea();
 public ChunkArea getChunkArea(){return area;} public boolean isCompletelyEmpty(){return empty;}
 public boolean containsBlockEntities(){return blockEntity;} public boolean isDirty(){return dirty;}
 public byte getVisibilityDirs(){return visibilityDirs;} public byte getDirections(){return directions;}
 public short getLastFrame(){return frame;} public void setLastFrame(short v){frame=v;}
 public void addDir(byte d){sourceDirs|=1<<d;}
 public boolean rebuildChunkAsync(net.vulkanmod.render.chunk.build.TaskDispatcher d,net.vulkanmod.render.chunk.build.RenderRegionBuilder r){builds++;return true;}
 public void setNotDirty(){dirty=false;}
}''',
}

PROBE = r'''
import net.vulkanmod.render.chunk.*;
import net.vulkanmod.render.chunk.graph.SectionGraph;
import net.vulkanmod.render.chunk.frustum.VFrustum;
import java.lang.reflect.*;
import java.util.*;
public class GraphProbe {
 static Method init, first, traverse, rebuild; static Field frustum;
 static void check(boolean v,String s){if(!v)throw new AssertionError(s);}
 static RenderSection[][][] grid(int r){
  int n=r*2+1;var g=new RenderSection[n][n][n];
  for(int x=0;x<n;x++)for(int y=0;y<n;y++)for(int z=0;z<n;z++){
   var s=new RenderSection();s.xOffset=(x-r)*16;s.yOffset=(y-r)*16;s.zOffset=(z-r)*16;s.blockEntity=(x+y+z)%11==0;g[x][y][z]=s;
  }
  for(int x=0;x<n;x++)for(int y=0;y<n;y++)for(int z=0;z<n;z++){
   var s=g[x][y][z];
   if(y>0){s.adjDown=g[x][y-1][z];s.adjDirs|=1;}
   if(y<n-1){s.adjUp=g[x][y+1][z];s.adjDirs|=2;}
   if(z>0){s.adjNorth=g[x][y][z-1];s.adjDirs|=4;}
   if(z<n-1){s.adjSouth=g[x][y][z+1];s.adjDirs|=8;}
   if(x>0){s.adjWest=g[x-1][y][z];s.adjDirs|=16;}
   if(x<n-1){s.adjEast=g[x+1][y][z];s.adjDirs|=32;}
  }return g;
 }
 static int run(int mode,int radius,boolean positiveX,boolean closed,boolean repeated) throws Exception{
  net.vulkanmod.Initializer.CONFIG.advCulling=mode;
  var graph=new SectionGraph(new net.minecraft.world.level.Level(),new SectionGrid(),new net.vulkanmod.render.chunk.build.TaskDispatcher());
  var f=new VFrustum();f.positiveXOnly=positiveX;frustum.set(graph,f);var g=grid(radius);
  int expected=closed?1:(positiveX?radius+1:radius*2+1)*(radius*2+1)*(radius*2+1);
  if(closed)g[radius][radius][radius].visibilityDirs=0;
  int passes=repeated?3:1,count=0;
  for(int pass=0;pass<passes;pass++){
   init.invoke(graph);
   for(var a:g)for(var b:a)for(var s:b){s.area.sectionQueue.clear();s.dirty=true;s.builds=0;}
   var seed=g[radius][radius][radius];first.invoke(null,seed,graph.getLastFrame());graph.getSectionQueue().add(seed);
   traverse.invoke(graph);rebuild.invoke(graph);count=0;int entities=0;
   var seen=Collections.newSetFromMap(new IdentityHashMap<RenderSection,Boolean>());
   for(var a:g)for(var b:a)for(var s:b){
    int visits=s.area.sectionQueue.size();check(visits<=1,"duplicate visible section");
    if(visits==1){count++;check(seen.add(s),"duplicate identity");check(s.builds==1&&!s.dirty,"lost rebuild");if(s.blockEntity)entities++;}
    else check(s.builds==0,"invisible section built");
    if(positiveX&&s.xOffset<0)check(visits==0,"frustum rejection lost");
   }
   check(graph.getBlockEntitiesSections().size()==entities,"block entities missing/duplicated");
   if(!Boolean.getBoolean("baseline"))check(count==expected,"terrain missing mode="+mode+" got="+count+" expected="+expected);
  }return count;
 }
 static int chain(int mode)throws Exception{
  net.vulkanmod.Initializer.CONFIG.advCulling=mode;
  var graph=new SectionGraph(new net.minecraft.world.level.Level(),new SectionGrid(),new net.vulkanmod.render.chunk.build.TaskDispatcher());
  frustum.set(graph,new VFrustum());var path=new RenderSection[32];for(int i=0;i<path.length;i++)path[i]=new RenderSection();
  for(int i=0;i<path.length-1;i++){
   var a=path[i];var b=path[i+1];
   if(i<8){a.adjDown=b;a.adjDirs|=1;b.adjUp=a;b.adjDirs|=2;}
   else if(i%2==0){a.adjEast=b;a.adjDirs|=32;b.adjWest=a;b.adjDirs|=16;}
   else{a.adjSouth=b;a.adjDirs|=8;b.adjNorth=a;b.adjDirs|=4;}
  }
  init.invoke(graph);first.invoke(null,path[0],graph.getLastFrame());graph.getSectionQueue().add(path[0]);traverse.invoke(graph);rebuild.invoke(graph);
  int count=0;for(var s:path){check(s.area.sectionQueue.size()<=1,"chain duplicated");if(s.area.sectionQueue.size()==1){count++;check(s.builds==1,"chain rebuild lost");}}
  return count;
 }
 public static void main(String[] args)throws Exception{
  init=SectionGraph.class.getDeclaredMethod("initUpdate");first=SectionGraph.class.getDeclaredMethod("initFirstNode",RenderSection.class,short.class);
  traverse=SectionGraph.class.getDeclaredMethod("updateRenderChunks");rebuild=SectionGraph.class.getDeclaredMethod("scheduleRebuilds");
  frustum=SectionGraph.class.getDeclaredField("frustum");for(var m:new Method[]{init,first,traverse,rebuild})m.setAccessible(true);frustum.setAccessible(true);
  if(Boolean.getBoolean("baseline")){int count=chain(2);check(count<32,"old hole not reproduced");System.out.println("{\"old_visible_sections\":"+count+",\"expected\":32}");return;}
  int cases=0,sections=0;
  for(int mode:new int[]{1,2,3,10})for(int radius:new int[]{3,7}){sections+=run(mode,radius,false,false,true)*3;cases+=3;}
  for(int mode:new int[]{1,2,3,10}){check(chain(mode)==32,"reachable diagonal chain dropped");cases++;sections+=32;}
  run(2,3,true,false,false);cases++;run(2,3,false,true,false);cases++;
  System.out.println("{\"passed\":true,\"cases\":"+cases+",\"visible_section_checks\":"+sections+",\"config_modes\":[1,2,3,10],\"frustum_and_visibility_preserved\":true,\"rebuilds_and_block_entities_exact_once\":true,\"queue_reset_and_growth\":true}");
 }
}
'''

def check(source, graph_path, baseline):
    with tempfile.TemporaryDirectory(prefix='hari-graph-') as temp:
        root=Path(temp)
        for name,text in STUBS.items():
            p=root/name;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(text)
        actual=source/'forge/src/main/java'
        for name in ['net/vulkanmod/render/chunk/util/ResettableQueue.java','net/vulkanmod/render/chunk/graph/GraphDirections.java']:
            p=root/name;p.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(actual/name,p)
        p=root/'net/vulkanmod/render/chunk/graph/SectionGraph.java';p.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(graph_path,p)
        (root/'GraphProbe.java').write_text(PROBE)
        compiler=['javac'] if shutil.which('javac') else ['java','com.sun.tools.javac.Main']
        compiled=subprocess.run(compiler+['-d',str(root/'classes')]+[str(p) for p in root.rglob('*.java')],capture_output=True,text=True)
        if compiled.returncode:raise RuntimeError(compiled.stdout+compiled.stderr)
        command=['java','-cp',str(root/'classes')]
        if baseline:command.insert(1,'-Dbaseline=true')
        result=subprocess.run(command+['GraphProbe'],capture_output=True,text=True,timeout=30)
        if result.returncode:raise RuntimeError(result.stdout+result.stderr)
        return json.loads(result.stdout)

def main():
    p=argparse.ArgumentParser();p.add_argument('source',type=Path);p.add_argument('--baseline',type=Path,required=True);p.add_argument('--report',type=Path);a=p.parse_args()
    old=check(a.source,a.baseline,True)
    result=check(a.source,a.source/'forge/src/main/java/net/vulkanmod/render/chunk/graph/SectionGraph.java',False)
    result['negative_control']=old
    result['scope']='Actual complete production SectionGraph, GraphDirections and ResettableQueue; checked Minecraft/frustum/build interfaces. Native terrain challenge remains separate.'
    if a.report:a.report.parent.mkdir(parents=True,exist_ok=True);a.report.write_text(json.dumps(result,indent=2)+'\n')
    print(json.dumps(result,indent=2))

if __name__=='__main__':main()
