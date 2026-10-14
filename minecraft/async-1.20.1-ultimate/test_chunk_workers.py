#!/usr/bin/env python3
"""Exercise the actual mesh dispatcher against a forced poll/wakeup race."""
import argparse,subprocess,shutil,tempfile,json
from pathlib import Path
STUBS={
'com/google/common/collect/Queues.java': '''package com.google.common.collect; import java.util.concurrent.*;import java.util.concurrent.atomic.*;public class Queues { public static CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1); public static AtomicBoolean pause=new AtomicBoolean(true); public static <T> java.util.Queue<T> newLinkedBlockingDeque(){return new LinkedBlockingDeque<>();} public static <T> java.util.Queue<T> newConcurrentLinkedQueue(){return new ConcurrentLinkedQueue<T>(){public T poll(){T item=super.poll();if(item==null&&pause.compareAndSet(true,false)){entered.countDown();try{release.await();}catch(InterruptedException e){throw new RuntimeException(e);}}return item;}};} }''',
'net/vulkanmod/Initializer.java': '''package net.vulkanmod; public class Initializer { public static final Config CONFIG=new Config(); public static class Config { public boolean adaptiveChunkUploads=false; public int chunkUploadsPerFrame=16;} }''',
'net/vulkanmod/render/optimization/AdaptiveChunkUploadBudget.java': '''package net.vulkanmod.render.optimization;public class AdaptiveChunkUploadBudget {public static long uploadTimeBudgetNanos(int n){return Long.MAX_VALUE;}public static int chooseBudget(int a,int b){return a;}}''',
'org/jetbrains/annotations/Nullable.java': 'package org.jetbrains.annotations; public @interface Nullable {}',
'net/vulkanmod/render/chunk/build/thread/ThreadBuilderPack.java': '''package net.vulkanmod.render.chunk.build.thread;public class ThreadBuilderPack {public void closeAll(){}}''',
'net/vulkanmod/render/chunk/build/thread/BuilderResources.java': '''package net.vulkanmod.render.chunk.build.thread;public class BuilderResources {public static final java.util.concurrent.atomic.AtomicInteger created=new java.util.concurrent.atomic.AtomicInteger(),closed=new java.util.concurrent.atomic.AtomicInteger();private boolean freed;public BuilderResources(){created.incrementAndGet();}public void close(){if(freed)throw new AssertionError("double close");freed=true;closed.incrementAndGet();}}''',
'net/vulkanmod/render/chunk/build/task/ChunkTask.java': '''package net.vulkanmod.render.chunk.build.task;public class ChunkTask {public boolean highPriority;private final Runnable action;public ChunkTask(Runnable action){this.action=action;}public void markStarted(){}public void runTask(net.vulkanmod.render.chunk.build.thread.BuilderResources r){action.run();}public void discard(){} }''',
'net/vulkanmod/render/chunk/build/task/CompileResult.java': '''package net.vulkanmod.render.chunk.build.task;public class CompileResult {public static int released;public boolean fullUpdate=true;public net.vulkanmod.render.chunk.RenderSection renderSection=new net.vulkanmod.render.chunk.RenderSection();public java.util.EnumMap<net.vulkanmod.render.vertex.TerrainRenderType,net.vulkanmod.render.chunk.build.UploadBuffer> renderedLayers=new java.util.EnumMap<>(net.vulkanmod.render.vertex.TerrainRenderType.class);public boolean matchesCurrentSection(){return true;}public void releaseBuffers(){released++;}public void updateSection(){} }''',
'net/vulkanmod/render/chunk/build/UploadBuffer.java': 'package net.vulkanmod.render.chunk.build;public class UploadBuffer {}',
'net/vulkanmod/render/chunk/RenderSection.java': '''package net.vulkanmod.render.chunk;public class RenderSection {public ChunkArea getChunkArea(){return new ChunkArea();}public net.vulkanmod.render.chunk.buffer.DrawBuffers.DrawParameters getDrawParameters(net.vulkanmod.render.vertex.TerrainRenderType t){return new net.vulkanmod.render.chunk.buffer.DrawBuffers.DrawParameters();}}''',
'net/vulkanmod/render/chunk/ChunkArea.java': '''package net.vulkanmod.render.chunk;public class ChunkArea {public net.vulkanmod.render.chunk.buffer.DrawBuffers getDrawBuffers(){return new net.vulkanmod.render.chunk.buffer.DrawBuffers();}}''',
'net/vulkanmod/render/chunk/buffer/DrawBuffers.java': '''package net.vulkanmod.render.chunk.buffer;public class DrawBuffers {public void upload(net.vulkanmod.render.chunk.RenderSection s,net.vulkanmod.render.chunk.build.UploadBuffer b,net.vulkanmod.render.vertex.TerrainRenderType t){}public static class DrawParameters {public void reset(net.vulkanmod.render.chunk.ChunkArea a,net.vulkanmod.render.vertex.TerrainRenderType t){}}}''',
'net/vulkanmod/render/vertex/TerrainRenderType.java': '''package net.vulkanmod.render.vertex;public enum TerrainRenderType {SOLID,TRANSLUCENT;public static final TerrainRenderType[] VALUES=values();}'''
}
PROBE=r'''
import net.vulkanmod.render.chunk.build.TaskDispatcher;
import net.vulkanmod.render.chunk.build.task.*;
import net.vulkanmod.render.chunk.build.thread.BuilderResources;
import com.google.common.collect.Queues;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
public class ChunkWorkerProbe {
 static void check(boolean v,String m){if(!v)throw new AssertionError(m);}
 public static void main(String[] args)throws Exception {
  var dispatcher=new TaskDispatcher();dispatcher.createThreads(1);
  check(Queues.entered.await(5,TimeUnit.SECONDS),"worker did not reach empty poll");
  var done=new CountDownLatch(1);var scheduler=new Thread(()->dispatcher.schedule(new ChunkTask(done::countDown)));
  scheduler.start();
  long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(2);
  while(scheduler.getState()!=Thread.State.BLOCKED&&scheduler.isAlive()&&System.nanoTime()<deadline)Thread.yield();
  Queues.release.countDown();scheduler.join(5000);
  check(done.await(5,TimeUnit.SECONDS),"lost notification between poll and sleep");
  dispatcher.stopThreads();
  check(BuilderResources.created.get()==BuilderResources.closed.get(),"first stop leaked worker resources");
  dispatcher.createThreads(4);
  var count=new AtomicInteger();var all=new CountDownLatch(4000);
  var producers=new Thread[4];
  for(int p=0;p<4;p++){producers[p]=new Thread(()->{for(int i=0;i<1000;i++)dispatcher.schedule(new ChunkTask(()->{count.incrementAndGet();all.countDown();}));});producers[p].start();}
  for(var p:producers)p.join();
  check(all.await(10,TimeUnit.SECONDS)&&count.get()==4000,"lost or duplicated concurrent chunk tasks");
  var uploads=new Thread(()->{for(int i=0;i<200;i++)dispatcher.scheduleSectionUpdate(new CompileResult());});uploads.start();
  deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(10);
  while(uploads.isAlive()&&System.nanoTime()<deadline){dispatcher.updateSections();Thread.yield();}
  uploads.join(1000);check(!uploads.isAlive(),"pending uploads did not drain");
  while(dispatcher.updateSections()){}
  dispatcher.stopThreads();
  check(BuilderResources.created.get()==BuilderResources.closed.get(),"restart/stop leaked worker resources");
  System.out.println("PASS: forced wakeup race, 4000 concurrent tasks, bounded upload drain, restart and resource closure");
 }
}
'''
p=argparse.ArgumentParser();p.add_argument('source',type=Path);p.add_argument('--report',type=Path);a=p.parse_args()
with tempfile.TemporaryDirectory(prefix='hari-chunk-workers-') as tmp:
 root=Path(tmp)
 for name,body in STUBS.items():
  d=root/name;d.parent.mkdir(parents=True,exist_ok=True);d.write_text(body)
 for relative in ['forge/src/main/java/net/vulkanmod/render/chunk/build/TaskDispatcher.java','common/src/main/java/com/axalotl/async/common/CpuWorkBudget.java']:
  d=root/relative.split('/src/main/java/')[1];d.parent.mkdir(parents=True,exist_ok=True);d.write_bytes((a.source/relative).read_bytes())
 (root/'ChunkWorkerProbe.java').write_text(PROBE)
 compiler=['javac'] if shutil.which('javac') else ['java','com.sun.tools.javac.Main']
 subprocess.run(compiler+['--release','17','-d',str(root/'classes')]+list(map(str,root.rglob('*.java'))),check=True)
 run=subprocess.run(['java','-ea','-cp',str(root/'classes'),'ChunkWorkerProbe'],capture_output=True,text=True,check=True,timeout=30)
 print(run.stdout,end='')
 if a.report:a.report.parent.mkdir(parents=True,exist_ok=True);a.report.write_text(json.dumps({'passed':True,'concurrent_tasks':4000,'uploads':200,'coverage':['forced poll/notify race','exactly-once task completion','bounded upload drain','worker restart and resource cleanup'],'scope':'Actual production TaskDispatcher; thread scheduling is real, Minecraft/builder resources are checked doubles'},indent=2)+'\n')
