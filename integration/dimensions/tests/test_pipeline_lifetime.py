"""Extract exact production ownership methods, execute actual shaderc and hardware Vk modules.

The surrounding builder/renderer/API types are a narrow fixture. Shader allocation,
module creation/destruction, failure identity and the extracted production method
bodies are real. This is lifetime/component evidence, not native game rendering.
"""
from pathlib import Path
import argparse, hashlib, json, os, subprocess, tempfile
from test_shader_lifetime import ROOT, REL, instrument

BASE=Path('forge/src/main/java/net/vulkanmod/vulkan/shader')

def method(text, signature):
    start=text.index(signature);body=text.index('{',start);depth=1;end=body+1
    while depth:
        depth+=(text[end]=='{')-(text[end]=='}');end+=1
    return text[start:end]

def fixture(pipeline,graphics):
    signatures=['public GraphicsPipeline createGraphicsPipeline()','public void setSPIRVs(',
                'private void releaseSPIRVs()', 'public void compileShaders(String name, String vsh, String fsh)']
    methods='\n'.join(method(pipeline,s) for s in signatures if s in pipeline)
    builder='''package net.vulkanmod.vulkan.shader;
import java.util.*;import org.apache.commons.lang3.Validate;
import net.vulkanmod.vulkan.shader.SPIRVUtils.SPIRV;import net.vulkanmod.vulkan.shader.SPIRVUtils.ShaderKind;
import static net.vulkanmod.vulkan.shader.SPIRVUtils.compileShader;
public final class Pipeline {public static class Builder {
List<Object> UBOs=new ArrayList<>(),imageDescriptors=new ArrayList<>();Object manualUBO;SPIRV vertShaderSPIRV,fragShaderSPIRV;
'''+methods+'\n}}'
    module=method(graphics,'private void createShaderModules(')
    # Wrap the original Vulkan destruction operation with a counting delegate that performs that exact native call.
    module=module.replace('vkDestroyShaderModule(Vulkan.getVkDevice(), this.vertShaderModule, null);','PipelineLifetimeRegression.destroyModule(this.vertShaderModule);')
    renderer='''package net.vulkanmod.vulkan.shader;
import java.nio.*;
final class GraphicsPipeline implements AutoCloseable {
private long vertShaderModule,fragShaderModule;
GraphicsPipeline(Pipeline.Builder b){createShaderModules(b.vertShaderSPIRV,b.fragShaderSPIRV);}
static long createShaderModule(ByteBuffer code){return PipelineLifetimeRegression.createModule(code);}
'''+module+'''
public void close(){PipelineLifetimeRegression.destroyModule(vertShaderModule);PipelineLifetimeRegression.destroyModule(fragShaderModule);}
}'''
    return builder,renderer

def main():
    ap=argparse.ArgumentParser();ap.add_argument('--classpath',required=True);ap.add_argument('--report',required=True,type=Path);a=ap.parse_args();assert not a.report.exists()
    overlay=ROOT/'integration/dimensions/overlay';pipeline=(overlay/BASE/'Pipeline.java').read_text();graphics=(overlay/BASE/'GraphicsPipeline.java').read_text();spirv=(overlay/REL).read_text()
    original=(ROOT/'source'/BASE/'Pipeline.java').read_text();oldgraphics=(ROOT/'source'/BASE/'GraphicsPipeline.java').read_text()
    variants=[('repaired',pipeline,graphics,True),('original_pipeline_lifetime',original,oldgraphics,False),
              ('pipeline_finally_release_removed',pipeline.replace('releaseSPIRVs();','/* rejected missing pipeline release */'),graphics,False),
              ('partial_compile_release_removed',pipeline.replace('vertex.free();','/* rejected missing partial release */'),graphics,False),
              ('partial_module_release_removed',pipeline,graphics.replace('vkDestroyShaderModule(Vulkan.getVkDevice(), this.vertShaderModule, null);','/* rejected missing module release */'),False)]
    cases=[]
    for name,body,gpu,expected in variants:
        with tempfile.TemporaryDirectory(prefix='mvh-pipeline-lifetime-') as tmp:
            t=Path(tmp);builder,renderer=fixture(body,gpu);sources=[]
            for rel,text in [(BASE/'Pipeline.java',builder),(BASE/'GraphicsPipeline.java',renderer),(REL,instrument(spirv)),
                    (Path('forge/src/main/java/net/vulkanmod/compat/observer/CompatProfiler.java'),'package net.vulkanmod.compat.observer;public final class CompatProfiler {public static boolean ENABLED=false;public static int shaderCompileCount;public static long spirvCompileTimeNanos;}')]:
                p=t/rel;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(text,encoding='utf8');sources.append(str(p))
            classes=t/'classes';sources.append(str(Path(__file__).with_name('PipelineLifetimeRegression.java')))
            build=subprocess.run(['javac','--release','17','-encoding','UTF-8','-cp',a.classpath,'-d',str(classes),*sources],capture_output=True,text=True)
            assert build.returncode==0,(name,build.stderr)
            run=subprocess.run(['java','-ea','-Dorg.lwjgl.system.stackSize=1024','-cp',str(classes)+os.pathsep+a.classpath,'net.vulkanmod.vulkan.shader.PipelineLifetimeRegression'],capture_output=True,text=True,timeout=60)
            assert (run.returncode==0)==expected,(name,run.stdout,run.stderr)
            if not expected:assert 'AssertionError: unreleased ' in run.stderr,(name,run.stderr)
            cases.append({'variant':name,'passed':run.returncode==0,'expected':expected,'stdout':run.stdout.strip(),'stderr':run.stderr.strip()})
    report={'passed':True,'cases':cases,'production_pipeline_sha256':hashlib.sha256(pipeline.encode()).hexdigest(),'production_graphics_sha256':hashlib.sha256(graphics.encode()).hexdigest(),
            'scope':'Exact extracted production stage/pipeline ownership methods, real shaderc and discrete hardware Vulkan shader modules. Surrounding builder/renderer validation fixture; native allocation/release counters. No Minecraft, draw/pixels, fullpack or FPS acceptance.'}
    a.report.parent.mkdir(parents=True,exist_ok=True);a.report.write_text(json.dumps(report,indent=2)+'\n');print('HARDWARE_PIPELINE_LIFETIME_PASS',len(cases),'controls')

if __name__=='__main__':main()
