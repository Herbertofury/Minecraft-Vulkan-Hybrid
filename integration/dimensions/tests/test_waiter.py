"""Reuse the accepted queue/failure tests and add a real dimension-owner boundary case."""
from pathlib import Path
import importlib.util, sys
R=Path(__file__).resolve().parents[3]
spec=importlib.util.spec_from_file_location('original',R/'minecraft/async-1.20.1-ultimate/test_c2me_task_waiter.py')
m=importlib.util.module_from_spec(spec);spec.loader.exec_module(m)
key='net/minecraft/server/MinecraftServer.java'
m.STUBS[key]=m.STUBS[key].replace('public int managedDepth','public boolean isSameThread(){return Thread.currentThread()==owner;} public int managedDepth')
key='net/minecraft/server/level/ServerChunkCache.java'
m.STUBS[key]=m.STUBS[key].replace('public class ServerChunkCache {','public class ServerChunkCache implements com.axalotl.async.common.ChunkOwnerExecutor {public volatile Thread owner=Thread.currentThread();public boolean harimt$ownerIsCurrentThread(){return owner==Thread.currentThread();}public java.util.concurrent.Executor harimt$ownerExecutor(){return jobs::add;}')
common='common/src/main/java/com/axalotl/async/common/ChunkOwnerExecutor.java'
m.STUBS['com/axalotl/async/common/ChunkOwnerExecutor.java']=(Path(sys.argv[1])/common).read_text()
case='''
  ServerLevel dimension=new ServerLevel();AtomicInteger packets=new AtomicInteger();dimension.server.jobs.add(packets::incrementAndGet);
  CompletableFuture<Void> local=new CompletableFuture<>();AtomicReference<Throwable> failure=new AtomicReference<>();AtomicBoolean negative=new AtomicBoolean();
  Thread dimensionThread=new Thread(()->{try{
   dimension.chunks.owner=Thread.currentThread();
   try{dimension.server.managedBlock(()->true);}catch(AssertionError expectedWrongOwner){negative.set(true);}
   dimension.chunks.jobs.add(()->local.complete(null));await(dimension,List.of(local));
  }catch(Throwable e){failure.set(e);}},"dimthread_server_regression");
  dimensionThread.start();dimensionThread.join(2000);
  check(!dimensionThread.isAlive()&&failure.get()==null&&local.isDone(),"dimension local queue stalled: "+failure.get());
  check(negative.get()&&packets.get()==0&&dimension.server.managedCalls==0&&dimension.server.jobs.size()==1,"global packet queue pumped from dimension thread");
  CompletableFuture<Void> mainDone=new CompletableFuture<>();dimension.server.jobs.add(()->mainDone.complete(null));dimension.chunks.owner=Thread.currentThread();await(dimension,List.of(mainDone));check(packets.get()==1,"packet lost or duplicated");
'''
m.PROBE=m.PROBE.replace('  System.out.println(',case+'  System.out.println(',1)
m.main()
