#!/usr/bin/env python3
"""Execute production GLSL conversion and native shaderc with an old-code control."""
import argparse, hashlib, json, os, shutil, subprocess, tempfile, zipfile
from pathlib import Path

STUBS = {
    'com/mojang/blaze3d/vertex/VertexFormat.java': 'package com.mojang.blaze3d.vertex; public class VertexFormat { public java.util.List<String> getElementAttributeNames(){return java.util.List.of();} }',
    'it/unimi/dsi/fastutil/objects/ObjectArrayList.java': 'package it.unimi.dsi.fastutil.objects; public class ObjectArrayList<T> extends java.util.ArrayList<T> {}',
    'net/vulkanmod/vulkan/shader/Pipeline.java': 'package net.vulkanmod.vulkan.shader; public class Pipeline { public static class Builder {public static int getStageFromString(String s){return 0;} } }',
    'net/vulkanmod/vulkan/shader/descriptor/UBO.java': 'package net.vulkanmod.vulkan.shader.descriptor; public class UBO {}',
    'net/vulkanmod/vulkan/shader/layout/AlignedStruct.java': 'package net.vulkanmod.vulkan.shader.layout; public class AlignedStruct { public static class Builder {public void addUniformInfo(String t,String n){} public net.vulkanmod.vulkan.shader.descriptor.UBO buildUBO(int b,int s){return new net.vulkanmod.vulkan.shader.descriptor.UBO();} } }',
    'net/vulkanmod/vulkan/shader/descriptor/ImageDescriptor.java': 'package net.vulkanmod.vulkan.shader.descriptor; public class ImageDescriptor { public final String qualifier,name; public final int imageIdx; private final int binding; public ImageDescriptor(int b,String t,String n,int i){binding=b;qualifier=t;name=n;imageIdx=i;} public int getBinding(){return binding;} }',
}
PROBE = r'''
import java.nio.file.*;
import java.util.*;
import net.vulkanmod.vulkan.shader.parser.GlslConverter;
import static org.lwjgl.util.shaderc.Shaderc.*;
public class SamplerProbe {
 static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
 static void compile(String source,int stage){
  long compiler=shaderc_compiler_initialize(), options=shaderc_compile_options_initialize(), result=0;
  check(compiler!=0 && options!=0,"native shaderc initialization failed");
  try {
   shaderc_compile_options_set_target_env(options,shaderc_target_env_vulkan,shaderc_env_version_vulkan_1_2);
   result=shaderc_compile_into_spv(compiler,source,stage,"shared-sampler-test","main",options);
   check(result!=0,"no compiler result");
   if(shaderc_result_get_compilation_status(result)!=shaderc_compilation_status_success)
    throw new IllegalStateException(shaderc_result_get_error_message(result));
   check(shaderc_result_get_bytes(result).remaining()>20,"empty SPIR-V");
  } finally {
   if(result!=0)shaderc_result_release(result);
   shaderc_compile_options_release(options);shaderc_compiler_release(compiler);
  }
 }
 static GlslConverter convert(String v,String f){var c=new GlslConverter();c.process(v,f);return c;}
 static long declarations(String source,String name){return source.lines().filter(s->s.contains("uniform sampler")&&s.endsWith(" "+name+";")).count();}
 public static void main(String[] args)throws Exception {
  String v="#version 150\nuniform sampler2D Sampler0;\nuniform sampler2D Sampler1;\nout vec2 uv;\nvoid main(){uv=vec2(0);gl_Position=texture(Sampler0,uv)+texture(Sampler1,uv);}\n";
  String f="#version 150\nuniform sampler2D Sampler1;\nuniform sampler2D Sampler0;\nin vec2 uv;\nout vec4 fragColor;\nvoid main(){fragColor=texture(Sampler0,uv)+texture(Sampler1,uv);}\n";
  var c=convert(v,f);
  if(args[0].equals("negative")){
   try {compile(c.getVshConverted(),shaderc_glsl_vertex_shader);throw new AssertionError("old duplicate sampler control unexpectedly compiled");}
   catch(IllegalStateException expected){check(expected.getMessage().contains("redefinition"),"wrong old-code failure: "+expected);}
   System.out.println("{\"old_redefinition_reproduced\":true}");return;
  }
  check(c.getSamplerList().size()==2,"shared samplers got multiple descriptor bindings");
  for(String name:List.of("Sampler0","Sampler1")){
   check(declarations(c.getVshConverted(),name)==1,"vertex declaration duplicated: "+name);
   check(declarations(c.getFshConverted(),name)==1,"fragment declaration duplicated: "+name);
  }
  check(c.getSamplerList().get(0).imageIdx==0&&c.getSamplerList().get(1).imageIdx==1,"texture slot changed");
  compile(c.getVshConverted(),shaderc_glsl_vertex_shader);compile(c.getFshConverted(),shaderc_glsl_fragment_shader);
  var distinct=convert(v.replace("Sampler1","VertexOnly"),f.replace("Sampler1","FragmentOnly"));
  check(distinct.getSamplerList().size()==3,"stage-only sampler lost");
  check(!distinct.getVshConverted().contains("FragmentOnly"),"fragment declaration leaked to vertex stage");
  check(!distinct.getFshConverted().contains("VertexOnly"),"vertex declaration leaked to fragment stage");
  check(distinct.getSamplerList().get(2).getBinding()==3,"unstable binding table");
  compile(distinct.getVshConverted(),shaderc_glsl_vertex_shader);compile(distinct.getFshConverted(),shaderc_glsl_fragment_shader);
  try{convert(v,f.replace("sampler2D Sampler0","samplerCube Sampler0"));throw new AssertionError("conflicting sampler types silently accepted");}
  catch(IllegalArgumentException expected){check(expected.getMessage().contains("Sampler0")&&expected.getMessage().contains("conflicting"),"missing type-conflict context");}
  if(args.length==3){
   var midnight=convert(Files.readString(Path.of(args[1])),Files.readString(Path.of(args[2])));
   check(midnight.getSamplerList().size()==2,"Midnight texture count changed");
   compile(midnight.getVshConverted(),shaderc_glsl_vertex_shader);compile(midnight.getFshConverted(),shaderc_glsl_fragment_shader);
  }
  System.out.println("{\"passed\":true,\"native_spirv_stages\":"+(args.length==3?6:4)+",\"shared_binding_preserved\":true,\"stage_only_samplers_preserved\":true,\"type_conflict_rejected\":true,\"actual_midnight_rift\":"+(args.length==3)+"}");
 }
}
'''

def expand_imports(source, archive):
    import re
    seen = set()
    def expand(text):
        def include(match):
            name = match.group(1)
            path = 'assets/minecraft/shaders/include/' + name
            if path in seen:
                return ''
            seen.add(path)
            return expand(archive.read(path).decode())
        return re.sub(r'^\s*#moj_import\s+<([^>]+)>\s*$', include, text, flags=re.M)
    return expand(source)

def main():
    p=argparse.ArgumentParser();p.add_argument('source',type=Path)
    p.add_argument('--baseline',type=Path,required=True);p.add_argument('--runtime-jar',type=Path,required=True)
    p.add_argument('--midnight-jar',type=Path);p.add_argument('--minecraft-client',type=Path)
    p.add_argument('--report',type=Path);args=p.parse_args()
    if bool(args.midnight_jar)!=bool(args.minecraft_client):p.error('provide both Midnight JAR and Minecraft client JAR')
    base='forge/src/main/java/net/vulkanmod/vulkan/shader/'
    hashes={}
    with tempfile.TemporaryDirectory(prefix='hari-shared-samplers-') as tmp:
        root=Path(tmp);libs=root/'libs';libs.mkdir();native=root/'native';native.mkdir()
        with zipfile.ZipFile(args.runtime_jar) as z:
            for name in ['META-INF/harimt-libs/lwjgl-3.3.1.jar','META-INF/harimt-libs/lwjgl-3.3.1-natives-linux.jar','META-INF/jarjar/lwjgl-shaderc-3.3.3.jar']:
                (libs/Path(name).name).write_bytes(z.read(name))
            (native/'libshaderc.so').write_bytes(z.read('assets/vulkanmod/natives/linux/x64/linux/x64/org/lwjgl/shaderc/libshaderc.so'))
        for name,body in STUBS.items():
            dest=root/'src'/name;dest.parent.mkdir(parents=True,exist_ok=True);dest.write_text(body)
        for name in ['parser/GlslConverter.java','parser/UniformParser.java','parser/InputOutputParser.java','parser/CodeParser.java','SamplerTextureSlot.java']:
            source=args.source/base/name;dest=root/'src/net/vulkanmod/vulkan/shader'/name
            dest.parent.mkdir(parents=True,exist_ok=True);dest.write_bytes(source.read_bytes())
            hashes[name]=hashlib.sha256(source.read_bytes()).hexdigest()
        (root/'src/SamplerProbe.java').write_text(PROBE)
        compiler=['javac'] if shutil.which('javac') else ['java','com.sun.tools.javac.Main']
        cp=os.pathsep.join(map(str,libs.glob('*.jar')))
        command=compiler+['--release','17','-cp',cp,'-d',str(root/'classes')]+list(map(str,(root/'src').rglob('*.java')))
        subprocess.run(command,check=True,capture_output=True,text=True)
        extra=[]
        if args.midnight_jar:
            with zipfile.ZipFile(args.midnight_jar) as midnight,zipfile.ZipFile(args.minecraft_client) as minecraft:
                for stage in ['vsh','fsh']:
                    dest=root/('midnight.'+stage)
                    dest.write_text(expand_imports(midnight.read('assets/midnight/shaders/core/rendertype_rift.'+stage).decode(),minecraft))
                    extra.append(str(dest))
        runtime=['java','-Dorg.lwjgl.librarypath='+str(native),'-cp',str(root/'classes')+os.pathsep+cp,'SamplerProbe']
        result=subprocess.run(runtime+['fixed']+extra,check=True,capture_output=True,text=True,timeout=60)
        report=json.loads(result.stdout)
        (root/'src/net/vulkanmod/vulkan/shader/parser/UniformParser.java').write_bytes(args.baseline.read_bytes())
        subprocess.run(command,check=True,capture_output=True,text=True)
        control=subprocess.run(runtime+['negative'],check=True,capture_output=True,text=True,timeout=60)
        report.update(json.loads(control.stdout));report['production_source_hashes']=hashes
        report['scope']='Actual production converter and native shaderc; descriptor/UBO dependencies are lightweight compile fixtures. Full packaged Minecraft validation is separate.'
    print(json.dumps(report))
    if args.report:args.report.parent.mkdir(parents=True,exist_ok=True);args.report.write_text(json.dumps(report,indent=2)+'\n')

if __name__=='__main__':main()
