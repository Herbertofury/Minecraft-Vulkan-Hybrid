#!/usr/bin/env python3
"""Reproduce the observed passenger-spawn lock cycle using the actual mixin."""
import argparse
import json
import shutil
import subprocess
import tempfile
from pathlib import Path

STUBS = {
    'com/axalotl/async/common/AsyncCommon.java': 'package com.axalotl.async.common; public class AsyncCommon {public static boolean HARICHUNK=true;}',
    'com/axalotl/async/common/ParallelProcessor.java': 'package com.axalotl.async.common; public class ParallelProcessor {public static final Object LOCK=new Object(); public static Object getEntityOperationLock(Object world){return LOCK;}}',
    'com/axalotl/async/common/config/AsyncConfig.java': 'package com.axalotl.async.common.config; public class AsyncConfig {public static final Flag disabled=new Flag(false),enableAsyncSpawn=new Flag(true); public static class Flag {public boolean value; Flag(boolean v){value=v;} public Boolean getValue(){return value;}}}',
    'net/minecraft/server/level/ServerLevel.java': 'package net.minecraft.server.level; public class ServerLevel {public final Object source; public ServerLevel(Object source){this.source=source;} public Object getChunkSource(){return source;}}',
    'net/minecraft/world/level/ServerLevelAccessor.java': 'package net.minecraft.world.level; import net.minecraft.world.entity.Entity; import net.minecraft.server.level.ServerLevel; public interface ServerLevelAccessor {ServerLevel getLevel(); boolean addFreshEntity(Entity e); default void addFreshEntityWithPassengers(Entity e){throw new AssertionError("wrong implementation");}}',
    'net/minecraft/world/entity/Entity.java': 'package net.minecraft.world.entity; import java.util.stream.Stream; public class Entity {public final int id; public final Entity[] tree; public Entity(int id,Entity... tree){this.id=id;this.tree=tree;} public Stream<Entity> getSelfAndPassengers(){return Stream.concat(Stream.of(this),Stream.of(tree));}}',
    'org/spongepowered/asm/mixin/Mixin.java': 'package org.spongepowered.asm.mixin; public @interface Mixin {Class<?>[] value();}',
    'org/spongepowered/asm/mixin/Overwrite.java': 'package org.spongepowered.asm.mixin; public @interface Overwrite {}',
}
PROBE = r'''
import com.axalotl.async.common.*;
import com.axalotl.async.common.config.AsyncConfig;
import com.axalotl.async.common.mixin.spawn.ServerLevelAccessorMixin;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ServerLevelAccessor;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.lang.management.*;

public class SpawnProbe {
 static final BlockingQueue<Runnable> jobs=new LinkedBlockingQueue<>();
 static final CountDownLatch queued=new CountDownLatch(1);
 static final AtomicBoolean queuedHoldingLock=new AtomicBoolean();
 static final AtomicReference<Throwable> failure=new AtomicReference<>();
 static final RuntimeException expected=new IllegalArgumentException("original-spawn-failure");
 static Thread owner;
 static class OwnerQueue implements ChunkOwnerExecutor {
  public Executor harimt$ownerExecutor(){return task->{if(Thread.holdsLock(ParallelProcessor.LOCK))queuedHoldingLock.set(true);jobs.add(task);queued.countDown();};}
 }
 static class World implements ServerLevelAccessor,ServerLevelAccessorMixin {
  final ServerLevel level=new ServerLevel(new OwnerQueue());
  final List<Integer> added=Collections.synchronizedList(new ArrayList<>());
  public ServerLevel getLevel(){return level;}
  public void addFreshEntityWithPassengers(Entity e){ServerLevelAccessorMixin.super.addFreshEntityWithPassengers(e);}
  public boolean addFreshEntity(Entity e){
   if(C2meThreadBoundary.mustHandoff())return C2meThreadBoundary.call(level,()->addFreshEntity(e));
   synchronized(ParallelProcessor.LOCK){
    if(AsyncCommon.HARICHUNK)check(Thread.currentThread()==owner,"off-owner spawn");
    if(e.id==-1)throw expected;
    added.add(e.id);return true;
   }
  }
 }
 static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);}
 static Thread worker(Runnable action){Thread t=new AsyncWorkerThread(()->{try{action.run();}catch(Throwable e){failure.set(e);}},"spawn-worker");t.setDaemon(true);t.start();return t;}
 static Entity tree(int first){return new Entity(first,new Entity(first+1),new Entity(first+2),new Entity(first+3));}
 static void join(Thread t)throws Exception{t.join(5000);check(!t.isAlive(),"spawn deadlock");if(failure.get()!=null)throw new AssertionError(failure.get());}
 public static void main(String[] args)throws Exception{
  owner=new Thread(()->{try{while(true)jobs.take().run();}catch(Throwable e){failure.set(e);}},"world-owner");owner.setDaemon(true);owner.start();
  World world=new World();
  if(args[0].equals("negative")){
   Thread t=worker(()->world.addFreshEntityWithPassengers(tree(0)));
   check(queued.await(2,TimeUnit.SECONDS),"negative control did not enqueue");
   long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(2);
   while(owner.getState()!=Thread.State.BLOCKED&&System.nanoTime()<deadline)Thread.sleep(1);
   ThreadInfo info=ManagementFactory.getThreadMXBean().getThreadInfo(owner.getId());
   check(queuedHoldingLock.get()&&owner.getState()==Thread.State.BLOCKED&&info.getLockOwnerId()==t.getId()&&t.isAlive(),"negative control failed to reproduce actual lock cycle");
   System.out.println("{\"old_outer_lock_deadlock_reproduced\":true}");return;
  }
  int next=0,trees=0;
  for(int workers:new int[]{1,2,8}){
   List<Thread> threads=new ArrayList<>();
   for(int w=0;w<workers;w++){final int start=next;next+=200;trees+=50;
    threads.add(worker(()->{for(int i=0;i<50;i++)world.addFreshEntityWithPassengers(tree(start+i*4));}));
   }
   for(Thread t:threads)join(t);
  }
  check(world.added.size()==next&&new HashSet<>(world.added).size()==next,"passenger loss/duplication");
  for(int i=0;i<world.added.size();i+=4){int first=world.added.get(i);check(first%4==0,"parent ordering");for(int p=1;p<4;p++)check(world.added.get(i+p)==first+p,"passenger ordering");}
  for(int mode=0;mode<2;mode++){
   AsyncConfig.disabled.value=mode==0;AsyncConfig.enableAsyncSpawn.value=mode!=1;
   final int first=next;join(worker(()->world.addFreshEntityWithPassengers(tree(first))));next+=4;trees++;
  }
  AsyncConfig.disabled.value=false;AsyncConfig.enableAsyncSpawn.value=true;
  join(worker(()->{try{world.addFreshEntityWithPassengers(new Entity(-1));throw new AssertionError("failure swallowed");}catch(RuntimeException e){check(e==expected,"failure identity changed");}}));
  check(!queuedHoldingLock.get(),"handoff held dimension monitor");
  AsyncCommon.HARICHUNK=false;
  final int first=next;join(worker(()->world.addFreshEntityWithPassengers(tree(first))));next+=4;trees++;
  check(world.added.size()==next&&jobs.isEmpty(),"unjoined/changed non-C2ME spawn");
  System.out.println("{\"passed\":true,\"passenger_trees\":"+trees+",\"entities_exact_once\":"+next+",\"original_failure_identity\":true,\"handoff_before_lock\":true,\"disabled_and_non_c2me_paths\":true}");
 }
}
'''

HANDOFF = '''        ServerLevelAccessor self = (ServerLevelAccessor) this;
        if (com.axalotl.async.common.C2meThreadBoundary.mustHandoff()) {
            com.axalotl.async.common.C2meThreadBoundary.run(self.getLevel(), () -> self.addFreshEntityWithPassengers(entity));
            return;
        }
'''

def main():
    parser=argparse.ArgumentParser()
    parser.add_argument('source',type=Path)
    parser.add_argument('--report',type=Path)
    args=parser.parse_args()
    mixin=args.source/'common/src/main/java/com/axalotl/async/common/mixin/spawn/ServerLevelAccessorMixin.java'
    body=mixin.read_text()
    assert body.count(HANDOFF)==1, 'Actual spawn ownership repair missing or changed'
    reports={}
    with tempfile.TemporaryDirectory(prefix='hari-spawn-lock-') as tmp:
        for variant in ['negative','positive']:
            root=Path(tmp)/variant;root.mkdir()
            for name,content in STUBS.items():
                dest=root/name;dest.parent.mkdir(parents=True,exist_ok=True);dest.write_text(content)
            for name in ['AsyncWorkerThread','ChunkOwnerExecutor','C2meThreadBoundary']:
                dest=root/f'com/axalotl/async/common/{name}.java'
                dest.write_bytes((args.source/f'common/src/main/java/com/axalotl/async/common/{name}.java').read_bytes())
            dest=root/'com/axalotl/async/common/mixin/spawn/ServerLevelAccessorMixin.java'
            dest.parent.mkdir(parents=True,exist_ok=True)
            dest.write_text(body.replace(HANDOFF,'',1) if variant=='negative' else body)
            (root/'SpawnProbe.java').write_text(PROBE)
            compiler=['javac'] if shutil.which('javac') else ['java','com.sun.tools.javac.Main']
            subprocess.run(compiler+['--release','17','-d',str(root/'classes')]+list(map(str,root.rglob('*.java'))),check=True)
            run=subprocess.run(['java','-ea','-cp',str(root/'classes'),'SpawnProbe',variant],capture_output=True,text=True,check=True,timeout=30)
            reports.update(json.loads(run.stdout))
    reports['scope']='Actual passenger-spawn mixin and owner boundary; real threads reproduce the native outer dimension-lock cycle. Packaged Minecraft+C2ME proof remains separate.'
    print(json.dumps(reports,indent=2))
    if args.report:
        args.report.parent.mkdir(parents=True,exist_ok=True);args.report.write_text(json.dumps(reports,indent=2)+'\n')

if __name__=='__main__':main()
