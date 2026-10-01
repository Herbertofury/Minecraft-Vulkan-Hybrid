#!/usr/bin/env python3
"""Actual managed waiter, owner queues, overdue-tick eligibility and failure barrier."""
import argparse,json,shutil,subprocess,tempfile
from pathlib import Path
STUBS={
'net/minecraft/server/level/ServerChunkCache.java':'''package net.minecraft.server.level; import java.util.concurrent.*; public class ServerChunkCache {public final ConcurrentLinkedQueue<Runnable> jobs=new ConcurrentLinkedQueue<>();public boolean pollTask(){Runnable job=jobs.poll();if(job==null)return false;job.run();return true;}}''',
'net/minecraft/server/MinecraftServer.java':'''package net.minecraft.server; import java.util.concurrent.*; import java.util.function.BooleanSupplier; import net.minecraft.server.level.ServerChunkCache; public class MinecraftServer {public final ConcurrentLinkedQueue<Runnable> jobs=new ConcurrentLinkedQueue<>();public final ServerChunkCache otherWorld=new ServerChunkCache(); public final Thread owner=Thread.currentThread();public int managedDepth,managedCalls; public void managedBlock(BooleanSupplier done){if(Thread.currentThread()!=owner)throw new AssertionError("off-owner managed block");managedDepth++;managedCalls++;try{long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);while(!done.getAsBoolean()){Runnable job=jobs.poll();if(job!=null)job.run();else if(!otherWorld.pollTask())java.util.concurrent.locks.LockSupport.parkNanos(25000);if(System.nanoTime()>deadline)throw new AssertionError("owner queues stranded");}}finally{managedDepth--;}}}''',
'net/minecraft/server/level/ServerLevel.java':'''package net.minecraft.server.level; import net.minecraft.server.MinecraftServer; public class ServerLevel {public final MinecraftServer server=new MinecraftServer();public final ServerChunkCache chunks=new ServerChunkCache();public ServerChunkCache getChunkSource(){return chunks;}public MinecraftServer getServer(){return server;}}''',
}
PROBE=r'''
import com.axalotl.async.common.*;
import net.minecraft.server.level.ServerLevel;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
public class WaitProbe {
 static void check(boolean v,String why){if(!v)throw new AssertionError(why);}
 static void await(ServerLevel world,List<? extends Future<?>> futures){C2meTaskWaiter.await(world,futures,TimeUnit.SECONDS.toNanos(2),n->{});}
 static Thread worker(Runnable action){Thread t=new AsyncWorkerThread(action,"C2ME-request-worker");t.setDaemon(true);t.start();return t;}
 public static void main(String[] args)throws Exception{
  ServerLevel old=new ServerLevel();CompletableFuture<Integer> pending=new CompletableFuture<>();CountDownLatch queued=new CountDownLatch(1);
  Thread stranded=worker(()->{old.chunks.jobs.add(()->old.server.jobs.add(()->pending.complete(17)));queued.countDown();pending.join();});
  check(queued.await(1,TimeUnit.SECONDS),"negative control not queued");old.chunks.pollTask();
  check(!pending.isDone()&&stranded.isAlive()&&!old.server.jobs.isEmpty(),"old chunk-only strategy did not strand server work");
  await(old,List.of(pending));stranded.join(1000);check(pending.join()==17&&!stranded.isAlive(),"negative control recovery");
  int requests=0;
  for(int count:new int[]{1,2,8}){
   ServerLevel world=new ServerLevel();List<Future<?>> futures=new ArrayList<>();AtomicInteger finished=new AtomicInteger();
   for(int w=0;w<count;w++){
    CompletableFuture<Void> done=new CompletableFuture<>();futures.add(done);
    worker(()->{try{for(int i=0;i<100;i++){final int expected=i;CompletableFuture<Integer> chunk=new CompletableFuture<>();
     world.chunks.jobs.add(()->{check(Thread.currentThread()==world.server.owner,"chunk owner");world.server.jobs.add(()->{check(world.server.managedDepth>0,"overdue owner task not eligible");world.server.otherWorld.jobs.add(()->chunk.complete(expected));});});
     check(chunk.join()==expected,"chunk result changed");finished.incrementAndGet();
    }done.complete(null);}catch(Throwable t){done.completeExceptionally(t);}});
   }
   await(world,futures);check(finished.get()==count*100&&world.server.jobs.isEmpty()&&world.server.otherWorld.jobs.isEmpty()&&world.chunks.jobs.isEmpty(),"request lost/unjoined");requests+=finished.get();
  }
  ServerLevel world=new ServerLevel();RuntimeException expected=new IllegalArgumentException("original-worker");CompletableFuture<Void> broken=new CompletableFuture<>();broken.completeExceptionally(expected);CompletableFuture<Void> tail=new CompletableFuture<>();
  worker(()->{try{Thread.sleep(50);tail.complete(null);}catch(Throwable t){tail.completeExceptionally(t);}});
  try{await(world,List.of(broken,tail));throw new AssertionError("worker failure swallowed");}catch(RuntimeException e){check(e==expected&&tail.isDone(),"failure escaped barrier or changed identity");}
  Error error=new LinkageError("original-error");CompletableFuture<Void> fatal=new CompletableFuture<>();fatal.completeExceptionally(error);
  try{await(world,List.of(fatal));throw new AssertionError("error swallowed");}catch(Error e){check(e==error,"error changed");}
  RuntimeException ownerError=new IllegalStateException("owner-task");CompletableFuture<Void> ownerTail=new CompletableFuture<>();world.server.jobs.add(()->{throw ownerError;});world.server.jobs.add(()->ownerTail.complete(null));
  try{await(world,List.of(ownerTail));throw new AssertionError("owner failure swallowed");}catch(RuntimeException e){check(e==ownerError&&ownerTail.isDone(),"owner failure escaped barrier");}
  CompletableFuture<Void> cancelled=new CompletableFuture<>();cancelled.cancel(false);
  try{await(world,List.of(cancelled));throw new AssertionError("cancelled work accepted");}catch(CancellationException expectedCancel){}
  CompletableFuture<Void> interrupted=new CompletableFuture<>();world.server.jobs.add(()->interrupted.complete(null));Thread.currentThread().interrupt();
  try{await(world,List.of(interrupted));throw new AssertionError("interruption lost");}catch(RuntimeException e){check(e.getCause() instanceof InterruptedException&&interrupted.isDone()&&Thread.currentThread().isInterrupted(),"interruption broke barrier");}Thread.interrupted();
  AtomicInteger warnings=new AtomicInteger();CompletableFuture<Void> slow=new CompletableFuture<>();world.server.jobs.add(()->slow.complete(null));C2meTaskWaiter.await(world,List.of(slow),-1,n->warnings.incrementAndGet());check(warnings.get()==1,"slow warning repeated or lost");
  int calls=world.server.managedCalls;await(world,List.of());check(world.server.managedCalls==calls&&world.server.managedDepth==0,"empty/reentrant block state");
  System.out.println("{\"passed\":true,\"old_chunk_only_queue_stall_reproduced\":true,\"cross_queue_chunk_requests\":"+requests+",\"worker_counts\":[1,2,8],\"overdue_owner_tasks_progress\":true,\"original_worker_and_owner_failures\":true,\"all_worker_barrier\":true,\"interruption_and_cancellation_preserved\":true}");
 }
}
'''
def main():
 p=argparse.ArgumentParser();p.add_argument('source',type=Path);p.add_argument('--report',type=Path);args=p.parse_args()
 source=args.source/'common/src/main/java/com/axalotl/async/common'
 assert 'if (AsyncCommon.HARICHUNK && waitWorld != null)' in (source/'ParallelProcessor.java').read_text()
 assert 'C2meTaskWaiter.await(waitWorld, futures, timeoutNs' in (source/'ParallelProcessor.java').read_text()
 with tempfile.TemporaryDirectory(prefix='hari-c2me-waiter-') as tmp:
  root=Path(tmp)
  for name,body in STUBS.items():
   dest=root/name;dest.parent.mkdir(parents=True,exist_ok=True);dest.write_text(body)
  for name in ['AsyncWorkerThread','C2meTaskWaiter','TaskFailures']:
   dest=root/f'com/axalotl/async/common/{name}.java';dest.parent.mkdir(parents=True,exist_ok=True);dest.write_bytes((source/f'{name}.java').read_bytes())
  (root/'WaitProbe.java').write_text(PROBE)
  compiler=['javac'] if shutil.which('javac') else ['java','com.sun.tools.javac.Main']
  subprocess.run(compiler+['--release','17','-d',str(root/'classes')]+list(map(str,root.rglob('*.java'))),check=True)
  run=subprocess.run(['java','-ea','-cp',str(root/'classes'),'WaitProbe'],capture_output=True,text=True,check=True,timeout=30)
  report=json.loads(run.stdout);report['scope']='Actual production waiter and failure barrier, real worker threads; checked Minecraft event-loop interfaces model overdue eligibility and cross-world/server queue dependencies. Native C2ME is a separate gate.'
 print(json.dumps(report,indent=2))
 if args.report:args.report.parent.mkdir(parents=True,exist_ok=True);args.report.write_text(json.dumps(report,indent=2)+'\n')
if __name__=='__main__':main()
