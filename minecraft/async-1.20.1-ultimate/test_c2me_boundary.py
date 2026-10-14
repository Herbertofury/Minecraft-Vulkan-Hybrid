#!/usr/bin/env python3
"""Real threads/owner queue, exact RNG sequence, lifecycle completion and original failures."""
import argparse,json,shutil,subprocess,tempfile
from pathlib import Path
STUBS={
'com/axalotl/async/common/AsyncCommon.java':'package com.axalotl.async.common; public class AsyncCommon { public static boolean HARICHUNK=true; }',
'net/minecraft/world/level/levelgen/PositionalRandomFactory.java':'package net.minecraft.world.level.levelgen; public interface PositionalRandomFactory {}',
'net/minecraft/util/RandomSource.java':'''package net.minecraft.util; import net.minecraft.world.level.levelgen.PositionalRandomFactory; public interface RandomSource { RandomSource fork();PositionalRandomFactory forkPositional();void setSeed(long seed);int nextInt();int nextInt(int bound);long nextLong();boolean nextBoolean();float nextFloat();double nextDouble();double nextGaussian(); }''',
'net/minecraft/server/level/ServerLevel.java':'''package net.minecraft.server.level; public class ServerLevel { public final Object source; public ServerLevel(Object source){this.source=source;} public Object getChunkSource(){return source;} }'''
}
PROBE=r'''
import com.axalotl.async.common.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.PositionalRandomFactory;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

public class BoundaryProbe {
 static final Thread owner=Thread.currentThread();
 static final Queue<Runnable> jobs=new ConcurrentLinkedQueue<>();
 static final AtomicInteger unsafe=new AtomicInteger(),calls=new AtomicInteger();
 static class OwnerQueue implements ChunkOwnerExecutor { public java.util.concurrent.Executor harimt$ownerExecutor(){return jobs::add;} }
 static class CheckedRandom implements RandomSource {
  final Random random;
  CheckedRandom(long seed){random=new Random(seed);}
  void check(){if(Thread.currentThread()!=owner){unsafe.incrementAndGet();throw new IllegalStateException("wrong owner");}calls.incrementAndGet();}
  public RandomSource fork(){check();return new CheckedRandom(random.nextLong());}
  public PositionalRandomFactory forkPositional(){check();return new PositionalRandomFactory(){};}
  public void setSeed(long seed){check();random.setSeed(seed);}
  public int nextInt(){check();return random.nextInt();}
  public int nextInt(int bound){check();return random.nextInt(bound);}
  public long nextLong(){check();return random.nextLong();}
  public boolean nextBoolean(){check();return random.nextBoolean();}
  public float nextFloat(){check();return random.nextFloat();}
  public double nextDouble(){check();return random.nextDouble();}
  public double nextGaussian(){check();return random.nextGaussian();}
 }
 static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);}
 static void pump(List<Thread> threads)throws Exception{
  long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(20);
  while(threads.stream().anyMatch(Thread::isAlive)){
   Runnable task=jobs.poll();if(task!=null)task.run();else Thread.sleep(1);
   if(System.nanoTime()>deadline)throw new AssertionError("owner/worker deadlock");
  }
  for(Thread t:threads)t.join();check(jobs.isEmpty(),"unjoined owner work");
 }
 public static void main(String[] args)throws Exception{
  ServerLevel world=new ServerLevel(new OwnerQueue());
  ServerOwnedRandom rng=new ServerOwnedRandom(world,new CheckedRandom(4));
  Random baseline=new Random(4);
  AtomicReference<Throwable> failure=new AtomicReference<>();
  AsyncWorkerThread sequence=new AsyncWorkerThread(()->{
   try{
    rng.setSeed(17);baseline.setSeed(17);
    for(int i=0;i<200;i++){
     check(rng.nextInt()==baseline.nextInt(),"integer sequence");
     check(rng.nextInt(27)==baseline.nextInt(27),"bounded sequence");
     check(rng.nextLong()==baseline.nextLong(),"long sequence");
     check(rng.nextBoolean()==baseline.nextBoolean(),"boolean sequence");
     check(rng.nextFloat()==baseline.nextFloat(),"float sequence");
     check(rng.nextDouble()==baseline.nextDouble(),"double sequence");
     check(rng.nextGaussian()==baseline.nextGaussian(),"Gaussian cached sequence");
    }
    check(rng.fork().nextInt()==new Random(baseline.nextLong()).nextInt(),"fork owner/sequence");
    check(rng.forkPositional()!=null,"positional fork");
   }catch(Throwable t){failure.set(t);}
  },"renamed-managed-worker");
  sequence.start();pump(List.of(sequence));if(failure.get()!=null)throw new AssertionError(failure.get());
  for(int count:new int[]{1,2,8}){
   AtomicInteger mutations=new AtomicInteger();Set<Integer> values=ConcurrentHashMap.newKeySet();List<Thread> threads=new ArrayList<>();
   for(int w=0;w<count;w++){
    Thread t=new AsyncWorkerThread(()->{try{for(int i=0;i<100;i++){
     int result=C2meThreadBoundary.call(world,()->{check(Thread.currentThread()==owner,"lifecycle ran on worker");return mutations.incrementAndGet();});
     values.add(result);
    }}catch(Throwable e){failure.set(e);}},"worker-"+w);threads.add(t);t.start();
   }
   pump(threads);if(failure.get()!=null)throw new AssertionError(failure.get());
   check(values.size()==count*100&&mutations.get()==count*100,"lifecycle loss/duplication");
  }
  RuntimeException expected=new IllegalArgumentException("original-runtime");Error fatal=new AssertionError("original-error");
  Thread t=new AsyncWorkerThread(()->{
   try{C2meThreadBoundary.run(world,()->{throw expected;});failure.set(new AssertionError("runtime lost"));}catch(RuntimeException e){if(e!=expected)failure.set(new AssertionError("runtime replaced"));}
   try{C2meThreadBoundary.call(world,()->{throw fatal;});failure.set(new AssertionError("error lost"));}catch(Error e){if(e!=fatal)failure.set(new AssertionError("error replaced"));}
  },"failure-worker");t.start();pump(List.of(t));if(failure.get()!=null)throw new AssertionError(failure.get());
  check(unsafe.get()==0,"C2ME delegate called off thread");
  check(C2meThreadBoundary.call(world,()->C2meThreadBoundary.call(world,()->17))==17,"nested owner handoff");
  AsyncCommon.HARICHUNK=false;
  AtomicBoolean inline=new AtomicBoolean();Thread normal=new AsyncWorkerThread(()->C2meThreadBoundary.run(world,()->inline.set(Thread.currentThread() instanceof AsyncWorkerThread)),"without-c2me");normal.start();normal.join(1000);
  check(inline.get()&&jobs.isEmpty(),"non-C2ME behavior changed");
  System.out.println("{\"passed\":true,\"rng_sequence_cases\":1400,\"owner_lifecycle_calls\":1100,\"unsafe_delegate_calls\":0,\"original_failure_identity\":true}");
 }
}
'''
def main():
 p=argparse.ArgumentParser();p.add_argument('source',type=Path);p.add_argument('--report',type=Path);args=p.parse_args()
 with tempfile.TemporaryDirectory(prefix='hari-c2me-boundary-') as tmp:
  root=Path(tmp)
  for name,body in STUBS.items():
   dest=root/name;dest.parent.mkdir(parents=True,exist_ok=True);dest.write_text(body)
  for name in ['AsyncWorkerThread','ChunkOwnerExecutor','C2meThreadBoundary','ServerOwnedRandom']:
   source=args.source/('common/src/main/java/com/axalotl/async/common/'+name+'.java');dest=root/('com/axalotl/async/common/'+name+'.java');dest.write_bytes(source.read_bytes())
  (root/'BoundaryProbe.java').write_text(PROBE)
  compiler=['javac'] if shutil.which('javac') else ['java','com.sun.tools.javac.Main']
  subprocess.run(compiler+['--release','17','-d',str(root/'classes')]+list(map(str,root.rglob('*.java'))),check=True)
  r=subprocess.run(['java','-ea','-cp',str(root/'classes'),'BoundaryProbe'],text=True,capture_output=True,check=True,timeout=30)
  result=json.loads(r.stdout);result['scope']='Actual production boundary and RNG wrapper; real worker/owner threads, checked Minecraft interfaces. Native C2ME mixin/runtime proof is separate.'
  print(json.dumps(result,indent=2))
  if args.report:args.report.parent.mkdir(parents=True,exist_ok=True);args.report.write_text(json.dumps(result,indent=2)+'\n')
if __name__=='__main__':main()
