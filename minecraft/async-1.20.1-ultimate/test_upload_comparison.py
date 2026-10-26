#!/usr/bin/env python3
"""Execute accepted and candidate upload managers against identical byte-exact workloads."""
import argparse,hashlib,importlib.util,json,shutil,subprocess,tempfile
from pathlib import Path
spec=importlib.util.spec_from_file_location('probes',Path(__file__).with_name('test_vulkan_performance.py'))
probes=importlib.util.module_from_spec(spec);spec.loader.exec_module(probes)
PROBE=r'''
import java.nio.ByteBuffer;
import net.vulkanmod.render.chunk.buffer.UploadManager;
import net.vulkanmod.vulkan.memory.Buffer;
import org.lwjgl.vulkan.VK10;
public class UploadComparison {
 public static void main(String[] args){
  UploadManager.createInstance();Buffer target=new Buffer(1,8192);
  for(int i=0;i<128;i++){
   byte[] data=new byte[16];java.util.Arrays.fill(data,(byte)i);
   UploadManager.INSTANCE.recordUpload(target,i*16,16,ByteBuffer.wrap(data));
  }
  UploadManager.INSTANCE.submitUploads();
  for(int i=0;i<128;i++)for(int j=0;j<16;j++)if(Buffer.bytes.get(1L)[i*16+j]!=(byte)i)throw new AssertionError("lost upload bytes");
  long copies=VK10.log.stream().filter(x->x.startsWith("copy:")).count();
  long barriers=VK10.log.stream().filter(x->x.startsWith("barrier:")).count();
  System.out.println("{\"regions\":128,\"copies\":"+copies+",\"barriers\":"+barriers+",\"checked_bytes\":2048}");
 }
}
'''
def evaluate(manager, source):
 with tempfile.TemporaryDirectory(prefix='hari-upload-comparison-') as tmp:
  root=Path(tmp); stubs=dict(probes.STUBS)
  stubs['org/lwjgl/vulkan/VK10.java']=stubs['org/lwjgl/vulkan/VK10.java'].replace('public class VK10 {','public class VK10 { public static final long VK_WHOLE_SIZE=-1;')
  stubs['it/unimi/dsi/fastutil/longs/LongOpenHashSet.java']='package it.unimi.dsi.fastutil.longs; public class LongOpenHashSet extends java.util.HashSet<Long> { public boolean add(long x){return super.add(x);} }'
  stubs['org/lwjgl/vulkan/VkBufferMemoryBarrier.java']='''package org.lwjgl.vulkan; public class VkBufferMemoryBarrier { public static Buffer calloc(int n,org.lwjgl.system.MemoryStack s){return new Buffer();} public VkBufferMemoryBarrier sType$Default(){return this;}public VkBufferMemoryBarrier buffer(long x){return this;}public VkBufferMemoryBarrier srcAccessMask(int x){return this;}public VkBufferMemoryBarrier dstAccessMask(int x){return this;}public VkBufferMemoryBarrier size(long x){return this;}public static class Buffer{public VkBufferMemoryBarrier get(int i){return new VkBufferMemoryBarrier();}} }'''
  for name,body in stubs.items():
   p=root/name;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(body)
  p=root/'net/vulkanmod/render/chunk/buffer/UploadManager.java';p.parent.mkdir(parents=True,exist_ok=True);p.write_bytes(manager.read_bytes())
  for name in ['UploadRanges','UploadCopies']:
   (p.parent/(name+'.java')).write_bytes((source/('forge/src/main/java/net/vulkanmod/render/chunk/buffer/'+name+'.java')).read_bytes())
  (root/'UploadComparison.java').write_text(PROBE)
  compiler=['javac'] if shutil.which('javac') else ['java','com.sun.tools.javac.Main']
  subprocess.run(compiler+['--release','17','-d',str(root/'classes')]+list(map(str,root.rglob('*.java'))),check=True)
  r=subprocess.run(['java','-ea','-cp',str(root/'classes'),'UploadComparison'],text=True,capture_output=True,check=True,timeout=20)
  return json.loads(r.stdout)
def main():
 p=argparse.ArgumentParser();p.add_argument('source',type=Path);p.add_argument('--baseline',type=Path,required=True);p.add_argument('--report',type=Path,required=True);args=p.parse_args()
 baseline=evaluate(args.baseline,args.source);candidate_path=args.source/'forge/src/main/java/net/vulkanmod/render/chunk/buffer/UploadManager.java';candidate=evaluate(candidate_path,args.source)
 assert baseline['copies']==128 and candidate['copies']==1 and candidate['barriers']==0
 assert baseline['checked_bytes']==candidate['checked_bytes']==2048
 result={'passed':True,'baseline':baseline,'candidate':candidate,'baseline_source_sha256':hashlib.sha256(args.baseline.read_bytes()).hexdigest(),'candidate_source_sha256':hashlib.sha256(candidate_path.read_bytes()).hexdigest(),'scope':'Actual accepted and candidate Java managers, identical disjoint destinations and data. Command recording is checked doubles; native validation is separate.'}
 args.report.parent.mkdir(parents=True,exist_ok=True);args.report.write_text(json.dumps(result,indent=2)+'\n');print(json.dumps(result,indent=2))
if __name__=='__main__':main()
