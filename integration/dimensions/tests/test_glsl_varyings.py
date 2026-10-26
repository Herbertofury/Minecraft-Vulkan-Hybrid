"""Compile the actual varying parser and check both shader stages and format mapping."""
from pathlib import Path
import argparse,json,subprocess,tempfile
I=Path(__file__).resolve().parents[1]

def main():
    p=argparse.ArgumentParser();p.add_argument('--report',type=Path);a=p.parse_args()
    with tempfile.TemporaryDirectory(prefix='mvh-glsl-varyings-') as tmp:
        root=Path(tmp);sources=[]
        def write(name,text):
            f=root/name;f.parent.mkdir(parents=True,exist_ok=True);f.write_text(text);sources.append(str(f))
        write('com/mojang/blaze3d/vertex/VertexFormat.java','package com.mojang.blaze3d.vertex;public class VertexFormat{public java.util.List<String> getElementAttributeNames(){return java.util.List.of("Position","Color","UV0","UV2","Normal");}}')
        write('it/unimi/dsi/fastutil/objects/ObjectArrayList.java','package it.unimi.dsi.fastutil.objects;public class ObjectArrayList<T> extends java.util.ArrayList<T>{}')
        write('net/vulkanmod/vulkan/shader/parser/GlslConverter.java','package net.vulkanmod.vulkan.shader.parser;public class GlslConverter{enum ShaderStage{Vertex,Fragment}}')
        write('net/vulkanmod/vulkan/shader/parser/UniformParser.java','package net.vulkanmod.vulkan.shader.parser;public class UniformParser{public static String removeSemicolon(String s){return s.endsWith(";")?s.substring(0,s.length()-1):s;}}')
        actual=root/'net/vulkanmod/vulkan/shader/parser/InputOutputParser.java';actual.write_bytes((I/'overlay/forge/src/main/java/net/vulkanmod/vulkan/shader/parser/InputOutputParser.java').read_bytes());sources.append(str(actual))
        write('net/vulkanmod/vulkan/shader/parser/VaryingProbe.java','''package net.vulkanmod.vulkan.shader.parser;
public class VaryingProbe {
 static void feed(InputOutputParser p,String text){for(String token:text.split(" "))p.parseToken(token);}
 static void check(boolean condition,String message){if(!condition)throw new AssertionError(message);}
 public static void main(String[]args){
  InputOutputParser p=new InputOutputParser(new GlslConverter());p.setFormat(new com.mojang.blaze3d.vertex.VertexFormat());p.setShaderStage(GlslConverter.ShaderStage.Vertex);
  feed(p,"in ivec2 UV2;");feed(p,"in vec3 Position;");
  feed(p,"out float vertexDistance;");feed(p,"flat out vec2 noiseCoord;");feed(p,"out vec4 vertexColor;");feed(p,"noperspective centroid out vec2 sampleCoord;");
  String vertex=p.createInOutCode();
  check(vertex.contains("layout(location = 3) in ivec2 UV2;"),"integer vertex attribute format location");
  check(vertex.contains("layout(location = 0) in vec3 Position;"),"position format location");
  check(vertex.contains("layout(location = 1) flat out vec2 noiseCoord;"),"flat output lost");
  check(vertex.contains("layout(location = 2) out vec4 vertexColor;"),"qualifier leaked to next declaration");
  check(vertex.contains("layout(location = 3) noperspective centroid out vec2 sampleCoord;"),"multiple qualifiers lost");
  p.setShaderStage(GlslConverter.ShaderStage.Fragment);feed(p,"flat in vec2 noiseCoord;");feed(p,"out vec4 fragColor;");String fragment=p.createInOutCode();
  check(fragment.contains("layout(location = 1) flat in vec2 noiseCoord;"),"fragment location or flat interpolation differs");
  check(fragment.contains("layout(location = 3) noperspective centroid in vec2 sampleCoord;"),"fragment qualifier differs");
  check(fragment.contains("layout(location = 0) out vec4 fragColor;"),"color output lost");
  System.out.println("ACTUAL_GLSL_VARYING_LOCATIONS_INTERPOLATION_FORMAT_PASS");
 }
}''')
        subprocess.run(['javac','--release','17','-d',str(root/'classes'),*sources],check=True)
        subprocess.run(['java','-ea','-cp',str(root/'classes'),'net.vulkanmod.vulkan.shader.parser.VaryingProbe'],check=True,timeout=30)
    if a.report:
        a.report.parent.mkdir(parents=True,exist_ok=True);a.report.write_text(json.dumps({'passed':True,'scope':'Actual production varying parser with type stubs; original flat/noperspective/centroid qualifiers, vertex-format integer attribute locations, fragment linkage and declaration state. Native shader compilation/rendering separate.'},indent=2)+'\n')

if __name__=='__main__':main()
