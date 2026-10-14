#!/usr/bin/env python3
"""Test source-string handling using actual LWJGL native memory, without an OpenGL context."""
import argparse, hashlib, json, shutil, subprocess, tempfile, urllib.request
from pathlib import Path

DEPENDENCIES = {
    'lwjgl-3.3.1.jar': 'cf83f90e32fb973ff5edfca4ef35f55ca51bb70a579b6a1f290744f552e8e484',
    'lwjgl-3.3.1-natives-linux.jar': '22ef2afa31a1740a337ec9c6806c6b8d97e931a63e2c43270cbaf14fb3f6fc4e'}
PROBE = r'''
import net.vulkanmod.gl.GlShaderSource;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryUtil;
import java.nio.*;
public class SourceProbe {
 static void check(boolean condition,String message){if(!condition)throw new AssertionError(message);}
 public static void main(String[] args){
  check(GlShaderSource.concatenate(new CharSequence[]{"#version 150\n","void ","main(){}"}).equals("#version 150\nvoid main(){}"),"multi-string source truncated");
  check(GlShaderSource.concatenate(new CharSequence[0]).isEmpty(),"empty source changed");
  ByteBuffer first=MemoryUtil.memUTF8("abcTAIL"),second=MemoryUtil.memUTF8("βxyz"),third=MemoryUtil.memUTF8("last\0discard");
  PointerBuffer pointers=MemoryUtil.memAllocPointer(4);
  IntBuffer lengths=MemoryUtil.memAllocInt(5);
  try{
   pointers.put(0,0L).put(1,MemoryUtil.memAddress(first)).put(2,MemoryUtil.memAddress(second)).put(3,MemoryUtil.memAddress(third));pointers.position(1).limit(4);
   lengths.put(2,3).put(3,2).put(4,-1);lengths.position(2).limit(5);
   check(GlShaderSource.concatenate(pointers,lengths).equals("abcβlast"),"explicit byte lengths/negative terminator lost");
   check(pointers.position()==1&&pointers.limit()==4&&lengths.position()==2&&lengths.limit()==5,"caller buffer positions changed");
   check(GlShaderSource.concatenate(pointers,new int[]{3,2,-1}).equals("abcβlast"),"array length path changed");
   check(GlShaderSource.concatenate(pointers,(IntBuffer)null).equals("abcTAILβxyzlast"),"null length list not terminated");
   check(GlShaderSource.concatenate(pointers,new int[]{0,0,0}).isEmpty(),"zero source lengths ignored");
   try{GlShaderSource.concatenate(pointers,new int[]{3});throw new AssertionError("short length array accepted");}catch(IllegalArgumentException expected){}
   pointers.put(1,0L);try{GlShaderSource.concatenate(pointers,(IntBuffer)null);throw new AssertionError("null source pointer accepted");}catch(IllegalArgumentException expected){}
  }finally{MemoryUtil.memFree(lengths);MemoryUtil.memFree(pointers);MemoryUtil.memFree(first);MemoryUtil.memFree(second);MemoryUtil.memFree(third);}
  System.out.println("{\"passed\":true,\"cases\":10,\"native_lwjgl_memory\":true,\"full_source_concatenation\":true,\"utf8_byte_lengths\":true,\"buffer_positions_preserved\":true}");
 }
}
'''

def main():
    p=argparse.ArgumentParser();p.add_argument('source',type=Path);p.add_argument('--dependencies',type=Path);p.add_argument('--report',type=Path);args=p.parse_args()
    source=args.source/'forge/src/main/java/net/vulkanmod/gl/GlShaderSource.java'
    for family in ['GL20M','ARBShaderObjectsM']:
        boundary=(args.source/f'forge/src/main/java/net/vulkanmod/mixin/compatibility/gl/{family}.java').read_text()
        assert boundary.count('GlShaderSource.concatenate')==3
        assert 'strings[0]' not in boundary
        assert 'params.put(params.position(), GL20.GL_TRUE);' not in boundary
    with tempfile.TemporaryDirectory(prefix='hari-shader-source-') as tmp:
        root=Path(tmp);jars=[]
        for name,digest in DEPENDENCIES.items():
            local=args.dependencies/name if args.dependencies else None
            data=local.read_bytes() if local and local.is_file() else urllib.request.urlopen('https://repo.maven.apache.org/maven2/org/lwjgl/lwjgl/3.3.1/'+name,timeout=60).read()
            assert hashlib.sha256(data).hexdigest()==digest, 'LWJGL fixture hash changed'
            path=root/name;path.write_bytes(data);jars.append(str(path))
        (root/'GlShaderSource.java').write_bytes(source.read_bytes());(root/'SourceProbe.java').write_text(PROBE)
        compiler=['javac'] if shutil.which('javac') else ['java','com.sun.tools.javac.Main']
        subprocess.run(compiler+['--release','17','-cp',jars[0],'-d',str(root/'classes'),str(root/'GlShaderSource.java'),str(root/'SourceProbe.java')],check=True)
        result=subprocess.run(['java','-cp',':'.join([str(root/'classes')]+jars),'SourceProbe'],check=True,capture_output=True,text=True,timeout=30)
    report=json.loads(result.stdout);report['source_sha256']=hashlib.sha256(source.read_bytes()).hexdigest();report['lwjgl_fixture_sha256']=DEPENDENCIES
    report['scope']='Actual source helper with real LWJGL 3.3.1 native memory. General external GL shader programs remain unadvertised until their pipeline is translated.'
    print(result.stdout.strip())
    if args.report:args.report.parent.mkdir(parents=True,exist_ok=True);args.report.write_text(json.dumps(report,indent=2)+'\n')

if __name__=='__main__':main()
