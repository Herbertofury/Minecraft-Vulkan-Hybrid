#!/usr/bin/env python3
"""Actual chunk wrapper: loaded fast path, owner load path and stalled raw-chain control."""
import argparse, json, shutil, subprocess, tempfile
from pathlib import Path

STUBS = {
    'org/spongepowered/asm/mixin/Mixin.java': 'package org.spongepowered.asm.mixin; public @interface Mixin {Class<?>[] value(); int priority() default 1000;}',
    'org/spongepowered/asm/mixin/Shadow.java': 'package org.spongepowered.asm.mixin; public @interface Shadow {}',
    'org/spongepowered/asm/mixin/Final.java': 'package org.spongepowered.asm.mixin; public @interface Final {}',
    'com/llamalad7/mixinextras/injector/wrapmethod/WrapMethod.java': 'package com.llamalad7.mixinextras.injector.wrapmethod; public @interface WrapMethod {String[] method();}',
    'com/llamalad7/mixinextras/injector/wrapoperation/Operation.java': 'package com.llamalad7.mixinextras.injector.wrapoperation; public interface Operation<T>{T call(Object... args);}',
    'com/axalotl/async/common/AsyncCommon.java': 'package com.axalotl.async.common; public class AsyncCommon {public static boolean HARICHUNK=true;}',
    'net/minecraft/world/level/chunk/ChunkAccess.java': 'package net.minecraft.world.level.chunk; public class ChunkAccess {}',
    'net/minecraft/world/level/chunk/LevelChunk.java': 'package net.minecraft.world.level.chunk; public class LevelChunk extends ChunkAccess {}',
    'net/minecraft/world/level/chunk/ChunkStatus.java': 'package net.minecraft.world.level.chunk; public class ChunkStatus {public static final ChunkStatus FULL=new ChunkStatus(), LIGHT=new ChunkStatus();}',
    'net/minecraft/server/level/ServerChunkCache.java': '''package net.minecraft.server.level;
import com.axalotl.async.common.*; import com.axalotl.async.common.mixin.server.C2meChunkAccessMixin;
import net.minecraft.world.level.chunk.*; import java.util.concurrent.*;
public class ServerChunkCache extends C2meChunkAccessMixin implements ChunkOwnerExecutor {
 public final ConcurrentLinkedQueue<Runnable> jobs=new ConcurrentLinkedQueue<>();
 public final LevelChunk cached=new LevelChunk(), loaded=new LevelChunk();
 public ServerChunkCache(ServerLevel world){level=world;}
 public LevelChunk getChunkNow(int x,int z){return x==0?cached:null;}
 public Executor harimt$ownerExecutor(){return jobs::add;}
}''',
    'net/minecraft/server/level/ServerLevel.java': '''package net.minecraft.server.level;
public class ServerLevel {public final ServerChunkCache chunks=new ServerChunkCache(this); public ServerChunkCache getChunkSource(){return chunks;}}'''
}

PROBE = r'''
import com.axalotl.async.common.*; import com.axalotl.async.common.mixin.server.*;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.server.level.*; import net.minecraft.world.level.chunk.*;
import java.lang.reflect.*; import java.util.*; import java.util.concurrent.*; import java.util.concurrent.atomic.*;
public class ChunkProbe {
 static final Thread owner=Thread.currentThread();
 static final Method method; static {try{method=C2meChunkAccessMixin.class.getDeclaredMethod("harimt$c2meChunkRequest",int.class,int.class,ChunkStatus.class,boolean.class,Operation.class);method.setAccessible(true);}catch(Exception e){throw new AssertionError(e);}}
 static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);}
 static ChunkAccess request(ServerChunkCache cache,int x,int z,ChunkStatus status,boolean create,Operation<ChunkAccess> original){
  try{return (ChunkAccess)method.invoke(cache,x,z,status,create,original);}catch(InvocationTargetException e){if(e.getCause() instanceof RuntimeException r)throw r;if(e.getCause() instanceof Error r)throw r;throw new AssertionError(e.getCause());}catch(Exception e){throw new AssertionError(e);}
 }
 static void pump(ServerChunkCache cache,List<Thread> threads)throws Exception{
  long until=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);
  while(threads.stream().anyMatch(Thread::isAlive)){Runnable r=cache.jobs.poll();if(r!=null)r.run();else Thread.yield();check(System.nanoTime()<until,"chunk owner deadlock");}
  for(Thread t:threads)t.join();check(cache.jobs.isEmpty(),"owner request leaked");
 }
 public static void main(String[] args)throws Exception{
  ServerLevel world=new ServerLevel();ServerChunkCache cache=world.chunks;
  CompletableFuture<ChunkAccess> rawChain=new CompletableFuture<>();CountDownLatch enqueued=new CountDownLatch(1);AtomicBoolean queuedRequestRan=new AtomicBoolean();
  Operation<ChunkAccess> provider=a->{if(Thread.currentThread()==owner)return cache.loaded;cache.jobs.add(()->queuedRequestRan.set(true));enqueued.countDown();return rawChain.join();};
  Thread old=new AsyncWorkerThread(()->provider.call(1,2,ChunkStatus.FULL,true),"old-offthread-provider");old.setDaemon(true);old.start();check(enqueued.await(1,TimeUnit.SECONDS),"negative control unqueued");cache.jobs.remove().run();
  check(queuedRequestRan.get()&&old.isAlive()&&!rawChain.isDone(),"raw future-chain stall not reproduced");rawChain.complete(cache.loaded);old.join(1000);check(!old.isAlive(),"negative control cleanup");
  AtomicReference<Throwable> failure=new AtomicReference<>();AtomicInteger ownerLoads=new AtomicInteger(),cacheReads=new AtomicInteger();int requests=0;
  for(int n:new int[]{1,2,8}){
   List<Thread> threads=new ArrayList<>();
   for(int w=0;w<n;w++){
    Thread t=new AsyncWorkerThread(()->{try{for(int i=0;i<100;i++){
     Operation<ChunkAccess> original=a->{check(Thread.currentThread()==owner,"blocking chunk request reached C2ME offthread path");check((int)a[1]==27,"coordinate changed");ownerLoads.incrementAndGet();return (boolean)a[3]?cache.loaded:null;};
     check(request(cache,0,27,ChunkStatus.FULL,true,original)==cache.cached,"loaded identity/fast path");cacheReads.incrementAndGet();
     check(request(cache,1,27,ChunkStatus.FULL,true,original)==cache.loaded,"owner loading identity");
     check(request(cache,0,27,ChunkStatus.LIGHT,false,original)==null,"partial status wrongly used FULL cache");
     check(request(cache,1,27,ChunkStatus.FULL,false,original)==null,"non-creating result changed");
    }}catch(Throwable e){failure.compareAndSet(null,e);}},"native-chunk-worker-"+w);threads.add(t);t.start();
   }
   pump(cache,threads);if(failure.get()!=null)throw new AssertionError(failure.get());requests+=n*400;
  }
  check(ownerLoads.get()==3300&&cacheReads.get()==1100,"load loss/duplication/cache handoff");
  RuntimeException runtime=new IllegalStateException("original-owner-chunk");Error fatal=new LinkageError("original-owner-error");
  Thread errors=new AsyncWorkerThread(()->{
   try{request(cache,1,27,ChunkStatus.FULL,true,a->{throw runtime;});failure.set(new AssertionError("runtime lost"));}catch(RuntimeException e){if(e!=runtime)failure.set(new AssertionError("runtime replaced"));}
   try{request(cache,1,27,ChunkStatus.FULL,true,a->{throw fatal;});failure.set(new AssertionError("error lost"));}catch(Error e){if(e!=fatal)failure.set(new AssertionError("error replaced"));}
  },"chunk-failure-worker");errors.start();pump(cache,List.of(errors));if(failure.get()!=null)throw new AssertionError(failure.get());
  check(request(cache,0,27,ChunkStatus.FULL,true,a->{check(Thread.currentThread()==owner,"owner changed");return cache.loaded;})==cache.loaded,"owner path intercepted");
  Thread foreign=new Thread(()->{check(request(cache,0,27,ChunkStatus.FULL,true,a->{check(Thread.currentThread()!=owner,"foreign provider changed");return cache.loaded;})==cache.loaded,"foreign path intercepted");},"C2ME-worldgen-worker");foreign.start();foreign.join();
  AsyncCommon.HARICHUNK=false;
  Thread absent=new AsyncWorkerThread(()->{check(request(cache,0,27,ChunkStatus.FULL,true,a->{check(Thread.currentThread() instanceof AsyncWorkerThread,"non-C2ME handoff");return cache.loaded;})==cache.loaded,"non-C2ME path changed");},"without-c2me");absent.start();absent.join();check(cache.jobs.isEmpty(),"unnecessary owner handoff");
  System.out.println("{\"passed\":true,\"old_offthread_future_chain_stall_reproduced\":true,\"requests\":"+requests+",\"loaded_reads_without_handoff\":"+cacheReads.get()+",\"owner_loads\":"+ownerLoads.get()+",\"worker_counts\":[1,2,8],\"original_failure_identity\":true,\"noncreating_and_status_preserved\":true,\"other_provider_and_non_c2me_paths_retained\":true}");
 }
}
'''

def main():
    p=argparse.ArgumentParser();p.add_argument('source',type=Path);p.add_argument('--report',type=Path);args=p.parse_args()
    source=args.source/'common/src/main/java/com/axalotl/async/common'
    config=json.loads((args.source/'common/src/main/resources/harimt.common.mixins.json').read_text())
    assert 'server.C2meChunkAccessMixin' in config['mixins']
    with tempfile.TemporaryDirectory(prefix='hari-chunk-access-') as tmp:
        root=Path(tmp)
        for name,body in STUBS.items():
            dest=root/name;dest.parent.mkdir(parents=True,exist_ok=True);dest.write_text(body)
        for name in ['AsyncWorkerThread','ChunkOwnerExecutor','C2meThreadBoundary','mixin/server/C2meChunkAccessMixin']:
            dest=root/f'com/axalotl/async/common/{name}.java';dest.parent.mkdir(parents=True,exist_ok=True);dest.write_bytes((source/f'{name}.java').read_bytes())
        (root/'ChunkProbe.java').write_text(PROBE)
        compiler=['javac'] if shutil.which('javac') else ['java','com.sun.tools.javac.Main']
        subprocess.run(compiler+['--release','17','-d',str(root/'classes')]+list(map(str,root.rglob('*.java'))),check=True)
        run=subprocess.run(['java','-ea','-cp',str(root/'classes'),'ChunkProbe'],capture_output=True,text=True,check=True,timeout=30)
        report=json.loads(run.stdout);report['scope']='Actual production whole-method mixin and owner boundary; real 1/2/8 threads, checked chunk/provider interfaces. Exact packaged C2ME runtime remains a separate gate.'
    print(json.dumps(report,indent=2))
    if args.report:args.report.parent.mkdir(parents=True,exist_ok=True);args.report.write_text(json.dumps(report,indent=2)+'\n')

if __name__=='__main__':main()
