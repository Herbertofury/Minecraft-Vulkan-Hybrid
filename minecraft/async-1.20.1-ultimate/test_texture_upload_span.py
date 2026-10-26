#!/usr/bin/env python3
"""Actual staging implementation and libc memcpy: byte equivalence and hot-path A/B.

Only Vulkan buffer allocation/retirement is doubled. memcpy and source cursors are real.
Final Minecraft FPS, GPU readback and validation are independent acceptance gates.
"""
import argparse
import hashlib
import json
import os
import subprocess
import tempfile
import urllib.request
from pathlib import Path

STUBS = {
 'net/vulkanmod/vulkan/memory/Buffer.java': '''package net.vulkanmod.vulkan.memory;
 import org.lwjgl.PointerBuffer; import org.lwjgl.system.MemoryUtil;
 public class Buffer { protected int usedBytes,offset,bufferSize; protected PointerBuffer data;
 public java.nio.ByteBuffer bytes; protected Buffer(int usage,Object type){}
 protected void createBuffer(int n){bufferSize=n;bytes=MemoryUtil.memAlloc(n);data=PointerBuffer.allocateDirect(1);data.put(0,MemoryUtil.memAddress(bytes));}
 public void reset(){usedBytes=0;} public long getOffset(){return offset;}
 }''',
 'net/vulkanmod/vulkan/memory/MemoryTypes.java': 'package net.vulkanmod.vulkan.memory; public class MemoryTypes { public static final Object HOST_MEM=new Object(); }',
 'net/vulkanmod/vulkan/memory/MemoryManager.java': '''package net.vulkanmod.vulkan.memory; public class MemoryManager {
 public static final MemoryManager INSTANCE=new MemoryManager(); public static MemoryManager getInstance(){return INSTANCE;}
 public void addToFreeable(Buffer b){} }''',
 'net/vulkanmod/render/chunk/util/Util.java': 'package net.vulkanmod.render.chunk.util; public class Util { public static int align(int n,int a){return (n+a-1)/a*a;} }',
 'net/vulkanmod/vulkan/util/VUtil.java': 'package net.vulkanmod.vulkan.util; public class VUtil {}',
 'net/vulkanmod/compat/observer/CompatProfiler.java': 'package net.vulkanmod.compat.observer; public class CompatProfiler { public static boolean ENABLED=false; public static long bufferUploadBytes; }',
 'net/vulkanmod/compat/RuntimeOptions.java': 'package net.vulkanmod.compat; public class RuntimeOptions {public static boolean diagnosticsEnabled(){return false;} }',
 'net/vulkanmod/Initializer.java': 'package net.vulkanmod; public class Initializer {public static final Logger LOGGER=new Logger();public static class Logger {public void info(String s,Object x){} } }',
 'org/lwjgl/vulkan/VK10.java': 'package org.lwjgl.vulkan; public class VK10 { public static final int VK_BUFFER_USAGE_TRANSFER_SRC_BIT=1; }',
}
PROBE = r'''
import net.vulkanmod.vulkan.memory.*;
import net.vulkanmod.vulkan.texture.TextureUploadSpan;
import org.lwjgl.system.MemoryUtil;
import java.nio.*;
import java.util.*;
public class TextureSpanProbe {
 static volatile long sink;
 static void check(boolean b,String s){if(!b)throw new AssertionError(s);}
 static void invalid(Runnable r){try{r.run();throw new AssertionError("invalid span accepted");}catch(IllegalArgumentException|ArithmeticException expected){}}
 static long copy(StagingBuffer dst,ByteBuffer src,int off,int size,int repeats){
  long begin=System.nanoTime();
  for(int i=0;i<repeats;i++){dst.reset();dst.align(4);dst.copyBufferRange(size,src,off);sink+=dst.bytes.get((i*17)%size);}
  return System.nanoTime()-begin;
 }
 public static void main(String[] args){
  Random random=new Random(941077);
  int cases=0;
  for(int format:new int[]{1,2,3,4,8})for(int width:new int[]{1,3,16,31,64})
  for(int height:new int[]{1,2,17,32})for(int padding:new int[]{0,5})for(int skipRows:new int[]{0,2})for(int skipPixels:new int[]{0,3}){
   int stride=width+padding,position=13;
   int capacity=((skipRows+height+3)*stride+skipPixels)*format+position;
   ByteBuffer src=MemoryUtil.memAlloc(capacity);
   for(int i=0;i<capacity;i++)src.put(i,(byte)random.nextInt());
   src.position(position);
   int declared=padding==0&&skipRows==0&&skipPixels==0?0:stride;
   int rowLength=TextureUploadSpan.rowLength(width,declared);
   int off=TextureUploadSpan.sourceOffset(rowLength,skipRows,skipPixels,format);
   int size=TextureUploadSpan.byteSize(width,height,rowLength,format);
   StagingBuffer dst=new StagingBuffer(1);
   dst.align(format);dst.copyBufferRange(size,src,off);
   check(src.position()==position&&src.limit()==capacity,"source cursor changed");
   for(int y=0;y<height;y++)for(int x=0;x<width*format;x++){
    int sourceIndex=position+((skipRows+y)*stride+skipPixels)*format+x;
    check(dst.bytes.get(y*stride*format+x)==src.get(sourceIndex),"changed pixel "+cases+":"+y+":"+x);
   }
   check(dst.usedForTest()==size,"copied unneeded prefix/suffix");
   MemoryUtil.memFree(src);MemoryUtil.memFree(dst.bytes);cases++;
  }
  invalid(()->TextureUploadSpan.rowLength(64,63));
  invalid(()->TextureUploadSpan.sourceOffset(16,-1,0,4));
  invalid(()->TextureUploadSpan.byteSize(16,0,16,4));
  invalid(()->TextureUploadSpan.byteSize(100000,100000,100000,4));
  invalid(()->TextureUploadSpan.sourceOffset(Integer.MAX_VALUE,Integer.MAX_VALUE,Integer.MAX_VALUE,8));
  ByteBuffer tiny=MemoryUtil.memAlloc(64);tiny.position(8);
  StagingBuffer s=new StagingBuffer(64);
  invalid(()->s.copyBufferRange(57,tiny,0));invalid(()->s.copyBufferRange(56,tiny,1));invalid(()->s.copyBufferRange(1,tiny,-1));
  MemoryUtil.memFree(tiny);MemoryUtil.memFree(s.bytes);
  // Fixed 64x64 animation frame from a 64x2048 sheet: identical destination pixels.
  int oldBytes=64*2048*4,newBytes=64*64*4,off=64*1024*4,repeats=10000;
  ByteBuffer sheet=MemoryUtil.memAlloc(oldBytes);
  for(int i=0;i<oldBytes;i++)sheet.put(i,(byte)(i*37));
  StagingBuffer dst=new StagingBuffer(oldBytes);
  for(int i=0;i<4;i++){copy(dst,sheet,0,oldBytes,1000);copy(dst,sheet,off,newBytes,1000);}
  long[] oldTimes=new long[9],newTimes=new long[9];
  for(int i=0;i<9;i++){
   if(i%2==0){oldTimes[i]=copy(dst,sheet,0,oldBytes,repeats);newTimes[i]=copy(dst,sheet,off,newBytes,repeats);}
   else{newTimes[i]=copy(dst,sheet,off,newBytes,repeats);oldTimes[i]=copy(dst,sheet,0,oldBytes,repeats);}
  }
  Arrays.sort(oldTimes);Arrays.sort(newTimes);
  System.out.println("{\"passed\":true,\"exact_pixel_cases\":"+cases+",\"uploads_per_sample\":"+repeats+
   ",\"baseline_bytes_per_upload\":"+oldBytes+",\"candidate_bytes_per_upload\":"+newBytes+
   ",\"baseline_median_ns\":"+oldTimes[4]+",\"candidate_median_ns\":"+newTimes[4]+
   ",\"baseline_samples_ns\":"+Arrays.toString(oldTimes)+",\"candidate_samples_ns\":"+Arrays.toString(newTimes)+"}");
  MemoryUtil.memFree(sheet);MemoryUtil.memFree(dst.bytes);
 }
}
'''

def main():
    p = argparse.ArgumentParser()
    p.add_argument('source', type=Path)
    p.add_argument('--report', type=Path, required=True)
    p.add_argument('--cache', type=Path, required=True)
    a = p.parse_args()
    a.cache.mkdir(parents=True, exist_ok=True)
    inputs = []
    for name, sha in [('lwjgl-3.3.1.jar','cf83f90e32fb973ff5edfca4ef35f55ca51bb70a579b6a1f290744f552e8e484'),
                      ('lwjgl-3.3.1-natives-linux.jar','22ef2afa31a1740a337ec9c6806c6b8d97e931a63e2c43270cbaf14fb3f6fc4e')]:
        path = a.cache / name
        url = 'https://repo.maven.apache.org/maven2/org/lwjgl/lwjgl/3.3.1/' + name
        if not path.is_file(): path.write_bytes(urllib.request.urlopen(url, timeout=120).read())
        assert hashlib.sha256(path.read_bytes()).hexdigest() == sha, name
        inputs.append({'url':url,'sha256':sha,'bytes':path.stat().st_size})
    with tempfile.TemporaryDirectory(prefix='hari-texture-span-') as temp:
        root = Path(temp)
        for name, text in STUBS.items():
            dst = root / name; dst.parent.mkdir(parents=True,exist_ok=True); dst.write_text(text)
        for name in ['memory/StagingBuffer','texture/TextureUploadSpan']:
            source = a.source / ('forge/src/main/java/net/vulkanmod/vulkan/' + name + '.java')
            dst = root / ('net/vulkanmod/vulkan/' + name + '.java')
            dst.parent.mkdir(parents=True,exist_ok=True);dst.write_bytes(source.read_bytes())
        buffer = root / 'net/vulkanmod/vulkan/memory/Buffer.java'
        buffer.write_text(buffer.read_text().replace('public void reset()', 'public int usedForTest(){return usedBytes;} public void reset()'))
        (root / 'TextureSpanProbe.java').write_text(PROBE)
        jars = os.pathsep.join(str((a.cache/name).resolve()) for name in ['lwjgl-3.3.1.jar','lwjgl-3.3.1-natives-linux.jar'])
        subprocess.run(['java','--module','jdk.compiler/com.sun.tools.javac.Main','--release','17','-cp',jars,'-d',str(root/'classes')]+[str(x) for x in root.rglob('*.java')],check=True)
        run=subprocess.run(['java','-Xms128m','-Xmx128m','-ea','-cp',str(root/'classes')+os.pathsep+jars,'TextureSpanProbe'],text=True,capture_output=True,check=True,timeout=120)
        result=json.loads(run.stdout)
        result.update(scope='Actual production StagingBuffer + TextureUploadSpan and real native libc memcpy; buffer allocation doubled; component gain is not Minecraft FPS',inputs=inputs,upload_calls_unchanged=True,image_barriers_unchanged=True)
        # Required transfer/write ordering must still occur after every copy.
        image=(a.source/'forge/src/main/java/net/vulkanmod/vulkan/texture/VulkanImage.java').read_text()
        assert image.count('ImageUtil.imageTransferMemoryBarrier(stack, commandBuffer.getHandle(), this, mipLevel);')==1
        assert 'stagingBuffer.copyBufferRange(imageSize, buffer, sourceOffset);' in image
        a.report.parent.mkdir(parents=True,exist_ok=True)
        a.report.write_text(json.dumps(result,indent=2)+'\n');print(json.dumps(result))

if __name__ == '__main__': main()
