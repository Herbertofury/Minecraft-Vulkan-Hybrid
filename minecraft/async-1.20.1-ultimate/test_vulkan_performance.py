#!/usr/bin/env python3
"""Actual production queue, ownership, budget and upload implementations; checked GPU doubles."""
import argparse,json,shutil,subprocess,tempfile
from pathlib import Path

STUBS={
'org/lwjgl/system/MemoryStack.java': 'package org.lwjgl.system; public class MemoryStack implements AutoCloseable { public static MemoryStack stackPush(){return new MemoryStack();} public void close(){} }',
'org/lwjgl/vulkan/VkCommandBuffer.java': 'package org.lwjgl.vulkan; public class VkCommandBuffer {}',
'org/lwjgl/vulkan/VkBufferCopy.java': '''package org.lwjgl.vulkan; public class VkBufferCopy { public long src,dst,size; public VkBufferCopy srcOffset(long v){src=v;return this;} public VkBufferCopy dstOffset(long v){dst=v;return this;} public VkBufferCopy size(long v){size=v;return this;} public static Buffer calloc(int n,org.lwjgl.system.MemoryStack s){return new Buffer(n);} public static class Buffer { public VkBufferCopy[] values; public Buffer(int n){values=new VkBufferCopy[n];for(int i=0;i<n;i++)values[i]=new VkBufferCopy();} public VkBufferCopy get(int i){return values[i];} } }''',
'org/lwjgl/vulkan/VkMemoryBarrier.java': '''package org.lwjgl.vulkan; public class VkMemoryBarrier { public static Buffer calloc(int n,org.lwjgl.system.MemoryStack s){return new Buffer();} public static class Buffer { public int src,dst; public Buffer sType$Default(){return this;} public Buffer srcAccessMask(int v){src=v;return this;} public Buffer dstAccessMask(int v){dst=v;return this;} } }''',
'org/lwjgl/vulkan/VK10.java': '''package org.lwjgl.vulkan; import java.util.*; public class VK10 { public static final int VK_ACCESS_TRANSFER_WRITE_BIT=1,VK_ACCESS_TRANSFER_READ_BIT=2,VK_PIPELINE_STAGE_TRANSFER_BIT=4; public static final List<String> log=new ArrayList<>(); public static final List<Runnable> pending=new ArrayList<>(); public static void vkCmdCopyBuffer(VkCommandBuffer cb,long src,long dst,VkBufferCopy.Buffer copies){ long[][] regions=new long[copies.values.length][3];for(int i=0;i<regions.length;i++){var r=copies.values[i];regions[i]=new long[]{r.src,r.dst,r.size};} log.add("copy:"+regions.length); pending.add(()->{for(var r:regions)System.arraycopy(net.vulkanmod.vulkan.memory.Buffer.bytes.get(src),(int)r[0],net.vulkanmod.vulkan.memory.Buffer.bytes.get(dst),(int)r[1],(int)r[2]);}); } public static void vkCmdPipelineBarrier(VkCommandBuffer cb,int a,int b,int c,VkMemoryBarrier.Buffer barrier,Object e,Object f){if(barrier.src!=1||(barrier.dst&1)==0)throw new AssertionError("lost write dependency");log.add("barrier:"+barrier.dst);} public static void execute(){for(var r:pending)r.run();pending.clear();} }''',
'net/vulkanmod/vulkan/memory/Buffer.java': '''package net.vulkanmod.vulkan.memory; public class Buffer { public static final java.util.Map<Long,byte[]> bytes=new java.util.HashMap<>(); public long id; public int capacity; public Buffer(long id,int n){this.id=id;capacity=n;bytes.put(id,new byte[n]);} public long getId(){return id;} public int getBufferSize(){return capacity;} }''',
'net/vulkanmod/vulkan/memory/StagingBuffer.java': '''package net.vulkanmod.vulkan.memory; public class StagingBuffer extends Buffer { private int used,offset; public StagingBuffer(){super(100,4096);} public void align(int n){used=(used+n-1)/n*n;} public void copyBuffer(int n,java.nio.ByteBuffer src){if(used+n>capacity){id++;capacity=(capacity+n)*2;bytes.put(id,new byte[capacity]);}offset=used;src.duplicate().get(bytes.get(id),used,n);used+=n;} public long getOffset(){return offset;} }''',
'net/vulkanmod/vulkan/Vulkan.java': '''package net.vulkanmod.vulkan; public class Vulkan { public static net.vulkanmod.vulkan.memory.StagingBuffer staging=new net.vulkanmod.vulkan.memory.StagingBuffer(); public static net.vulkanmod.vulkan.memory.StagingBuffer getStagingBuffer(){return staging;} }''',
'net/vulkanmod/vulkan/queue/CommandPool.java': '''package net.vulkanmod.vulkan.queue; public class CommandPool { public static class CommandBuffer { private final org.lwjgl.vulkan.VkCommandBuffer handle=new org.lwjgl.vulkan.VkCommandBuffer(); public org.lwjgl.vulkan.VkCommandBuffer getHandle(){return handle;} } }''',
'net/vulkanmod/vulkan/queue/Queue.java': '''package net.vulkanmod.vulkan.queue; public class Queue { public CommandPool.CommandBuffer beginCommands(){return new CommandPool.CommandBuffer();} public void submitCommands(CommandPool.CommandBuffer cb){org.lwjgl.vulkan.VK10.execute();} }''',
'net/vulkanmod/vulkan/queue/TransferQueue.java': '''package net.vulkanmod.vulkan.queue; public class TransferQueue { public static void uploadBufferCmd(org.lwjgl.vulkan.VkCommandBuffer cb,long s,long so,long d,long o,long n){var r=org.lwjgl.vulkan.VkBufferCopy.calloc(1,org.lwjgl.system.MemoryStack.stackPush());r.get(0).srcOffset(so).dstOffset(o).size(n);org.lwjgl.vulkan.VK10.vkCmdCopyBuffer(cb,s,d,r);} }''',
'net/vulkanmod/vulkan/device/DeviceManager.java': '''package net.vulkanmod.vulkan.device; public class DeviceManager { public static net.vulkanmod.vulkan.queue.Queue getTransferQueue(){return new net.vulkanmod.vulkan.queue.Queue();} }''',
'net/vulkanmod/vulkan/Synchronization.java': '''package net.vulkanmod.vulkan; public class Synchronization { public static final Synchronization INSTANCE=new Synchronization(); public void addCommandBuffer(net.vulkanmod.vulkan.queue.CommandPool.CommandBuffer cb){} public void waitFences(){} }''',
}
PROBE=r"""
import com.axalotl.async.common.*;
import net.vulkanmod.render.chunk.buffer.*;
import net.vulkanmod.vulkan.memory.Buffer;
import org.lwjgl.vulkan.VK10;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.nio.ByteBuffer;
public class PerformanceProbe {
 static volatile long sink;
 static void check(boolean v,String m){if(!v)throw new AssertionError(m);}
 static ByteBuffer data(int n,int value){byte[] bytes=new byte[n];Arrays.fill(bytes,(byte)value);return ByteBuffer.wrap(bytes);}
 static void fresh(){VK10.log.clear();VK10.pending.clear();net.vulkanmod.vulkan.Vulkan.staging=new net.vulkanmod.vulkan.memory.StagingBuffer();UploadManager.createInstance();}
 static void queues() throws Exception {
  for(int workers:new int[]{1,2,8}) {
   var values=new ArrayList<Integer>();for(int i=0;i<10000;i++)values.add(i);
   var work=new IndexedWorkQueue<Integer>(values);values.clear();
   var counts=new AtomicIntegerArray(10000);var threads=new ArrayList<Thread>();
   for(int w=0;w<workers;w++){var t=new AsyncWorkerThread(()->{Integer i;while((i=work.poll())!=null)counts.incrementAndGet(i);},"renamed worker");threads.add(t);t.start();}
   for(var t:threads)t.join();for(int i=0;i<10000;i++)check(counts.get(i)==1,"missing/duplicate work "+i);
   check(work.poll()==null,"exhausted snapshot");
  }
  try{new IndexedWorkQueue<>(Arrays.asList(1,null));throw new AssertionError("null accepted");}catch(NullPointerException expected){}
  check(!(Thread.currentThread() instanceof AsyncWorkerThread),"main thread marked async");
  check(new AsyncWorkerThread(()->{},"anything") instanceof AsyncWorkerThread,"name-dependent ownership");
 }
 static void budgets(){
  for(int p:new int[]{1,2,4,8,16,32})for(boolean c:new boolean[]{false,true})for(boolean external:new boolean[]{false,true}){
   int n=CpuWorkBudget.entityWorkers(p,c,external);check(n>=1&&n<=p,"invalid entity budget");
   int mesh=CpuWorkBudget.meshWorkers(p,external);check(mesh>=1&&mesh<=p,"invalid mesh budget");
  }
  check(CpuWorkBudget.entityWorkers(32,true,true)==10&&CpuWorkBudget.meshWorkers(32,true)==10,"C2ME allocation");
 }
 static void uploads(){
  fresh();var dst=new Buffer(1,4096);var u=UploadManager.INSTANCE;
  for(int i=0;i<128;i++)u.recordUpload(dst,i*16,16,data(16,i));
  u.submitUploads();check(VK10.log.equals(List.of("copy:128")),"separate writes did not batch: "+VK10.log);
  for(int i=0;i<2048;i++)check(Buffer.bytes.get(1L)[i]==(byte)(i/16),"changed upload bytes");
  fresh();dst=new Buffer(2,4096);u=UploadManager.INSTANCE;
  u.recordUpload(dst,0,16,data(16,1));u.recordUpload(dst,8,16,data(16,2));u.recordUpload(dst,12,16,data(16,3));u.submitUploads();
  check(VK10.log.equals(List.of("copy:1","barrier:1","copy:1","barrier:1","copy:1")),"WAW order/barrier lost: "+VK10.log);
  check(Buffer.bytes.get(2L)[7]==1&&Buffer.bytes.get(2L)[11]==2&&Buffer.bytes.get(2L)[27]==3,"overlap output");
  fresh();var a=new Buffer(3,8192);var b=new Buffer(4,8192);u=UploadManager.INSTANCE;
  u.recordUpload(a,0,3000,data(3000,17));u.recordUpload(a,3000,3000,data(3000,18));u.copyBuffer(a,0,b,0,6000);u.recordUpload(b,4000,16,data(16,19));u.submitUploads();
  check(Buffer.bytes.get(4L)[0]==17&&Buffer.bytes.get(4L)[3999]==18&&Buffer.bytes.get(4L)[4000]==19&&Buffer.bytes.get(4L)[5999]==18,"growth/copy/overwrite output");
  check(VK10.log.contains("barrier:3"),"transfer read dependency lost");
  fresh();a=new Buffer(5,4096);b=new Buffer(6,4096);u=UploadManager.INSTANCE;
  u.recordUpload(a,0,16,data(16,7));u.recordUpload(b,0,16,data(16,8));u.recordUpload(a,16,16,data(16,9));u.submitUploads();
  check(VK10.log.equals(List.of("copy:1","copy:1","copy:1")),"unrelated buffer barrier");
  u.recordUpload(a,32,16,data(16,10));u.syncUploads();check(Buffer.bytes.get(5L)[32]==10,"next submission lost");
  try{u.recordUpload(a,4095,16,data(16,0));throw new AssertionError("OOB upload accepted");}catch(IllegalArgumentException expected){}
  var ranges=new UploadRanges();for(int i=0;i<100;i++)ranges.add(3,i*4,4);
  check(!ranges.overlaps(3,400,4)&&ranges.overlaps(3,399,4)&&!ranges.overlaps(4,0,4),"range boundary/growth");
  ranges.clear();check(!ranges.overlaps(3,0,4),"range reset");
 }
 static long benchmark(boolean indexed,List<Integer> values){
  long start=System.nanoTime(),sum=0;
  for(int repeat=0;repeat<20000;repeat++){
   if(indexed){var q=new IndexedWorkQueue<>(values);Integer i;while((i=q.poll())!=null)sum+=i;}
   else{var q=new ConcurrentLinkedQueue<>(values);Integer i;while((i=q.poll())!=null)sum+=i;}
  }
  sink=sum;return System.nanoTime()-start;
 }
 public static void main(String[] args)throws Exception{
  queues();budgets();uploads();
  var values=new ArrayList<Integer>();for(int i=0;i<256;i++)values.add(i);
  for(int i=0;i<4;i++){benchmark(false,values);benchmark(true,values);}
  long[] baseline=new long[7],candidate=new long[7];
  var bean=(com.sun.management.ThreadMXBean)java.lang.management.ManagementFactory.getThreadMXBean();bean.setThreadAllocatedMemoryEnabled(true);
  long allocationStart=bean.getThreadAllocatedBytes(Thread.currentThread().getId());benchmark(false,values);long baselineBytes=bean.getThreadAllocatedBytes(Thread.currentThread().getId())-allocationStart;
  allocationStart=bean.getThreadAllocatedBytes(Thread.currentThread().getId());benchmark(true,values);long candidateBytes=bean.getThreadAllocatedBytes(Thread.currentThread().getId())-allocationStart;
  for(int i=0;i<7;i++){if(i%2==0){baseline[i]=benchmark(false,values);candidate[i]=benchmark(true,values);}else{candidate[i]=benchmark(true,values);baseline[i]=benchmark(false,values);}}
  Arrays.sort(baseline);Arrays.sort(candidate);
  System.out.println("{\"passed\":true,\"work_items_per_sample\":5120000,\"baseline_median_ns\":"+baseline[3]+",\"candidate_median_ns\":"+candidate[3]+",\"baseline_allocated_bytes\":"+baselineBytes+",\"candidate_allocated_bytes\":"+candidateBytes+",\"disjoint_upload_regions\":128,\"candidate_copy_commands\":1,\"candidate_disjoint_barriers\":0}");
 }
}
"""
def main():
 p=argparse.ArgumentParser();p.add_argument('source',type=Path);p.add_argument('--report',type=Path);args=p.parse_args()
 with tempfile.TemporaryDirectory(prefix='hari-performance-') as tmp:
  root=Path(tmp)
  for name,body in STUBS.items():
   dest=root/name;dest.parent.mkdir(parents=True,exist_ok=True);dest.write_text(body)
  production=['common/src/main/java/com/axalotl/async/common/'+x+'.java' for x in ['IndexedWorkQueue','AsyncWorkerThread','CpuWorkBudget']]
  production+=['forge/src/main/java/net/vulkanmod/render/chunk/buffer/'+x+'.java' for x in ['UploadRanges','UploadCopies','UploadManager']]
  for relative in production:
   path=args.source/relative;dest=root/relative.split('/src/main/java/')[1];dest.parent.mkdir(parents=True,exist_ok=True);dest.write_bytes(path.read_bytes())
  (root/'PerformanceProbe.java').write_text(PROBE)
  compiler=['javac'] if shutil.which('javac') else ['java','com.sun.tools.javac.Main']
  subprocess.run(compiler+['--release','17','-d',str(root/'classes')]+list(map(str,root.rglob('*.java'))),check=True)
  run=subprocess.run(['java','-Xms256m','-Xmx256m','-Dharimt.qa.performance=true','-ea','-cp',str(root/'classes'),'PerformanceProbe'],capture_output=True,text=True,check=True,timeout=60)
  result=json.loads(run.stdout);result['scope']='Actual production Java; real concurrent queue checks and alternating equivalent-work CPU benchmark; Vulkan command recording uses checked doubles. Native Minecraft proof is separate.'
  print(json.dumps(result,indent=2))
  if args.report:args.report.parent.mkdir(parents=True,exist_ok=True);args.report.write_text(json.dumps(result,indent=2)+'\n')
if __name__=='__main__':main()
