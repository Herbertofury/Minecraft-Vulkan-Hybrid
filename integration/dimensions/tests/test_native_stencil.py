"""Compile production stencil encoder/helper, inspect real native Vulkan structs and mutation controls.

No upstream Minecraft class executes. The encoder and format predicate are extracted unchanged.
Actual GPU behavior is checked by the separate owned-client NativeStencilSmoke diagnostic.
"""
from pathlib import Path
import argparse,json,os,subprocess,tempfile
I=Path(__file__).resolve().parents[1];D=I.parents[1]
def block(text,start):
    left=text.index(start);brace=text.index('{',left);level=1;end=brace+1
    while level:
        if text[end]=='{':level+=1
        elif text[end]=='}':level-=1
        end+=1
    return text[left:end]
def main():
    ap=argparse.ArgumentParser();ap.add_argument('--lwjgl-classpath',required=True);ap.add_argument('--report',type=Path);a=ap.parse_args()
    base=D/'source/forge/src/main/java/net/vulkanmod'
    encoder=block((base/'vulkan/shader/PipelineState.java').read_text(encoding='utf8'),'public static abstract class StencilState')
    predicate=block((base/'vulkan/texture/VulkanImage.java').read_text(encoding='utf8'),'public static boolean hasStencilComponent')
    source=I/'overlay/forge/src/main/java/net/vulkanmod/vulkan/shader/NativeStencilPipelineState.java'
    actual=source.read_text(encoding='utf8')
    variants=[('production',actual,True),('disabled_stencil',actual.replace('present && PipelineState.StencilState.stencilTest(encoded)','false'),False),('wrong_depth_fail',actual.replace('face.depthFailOp(PipelineState.StencilState.decodeDepthFailOp(encoded));','face.depthFailOp(PipelineState.StencilState.decodePassOp(encoded));'),False),('back_face_missing',actual.replace('configure(target.back(), encoded);',''),False),('missing_dynamic_format',actual.replace('VulkanImage.hasStencilComponent(depthFormat) ? depthFormat : VK_FORMAT_UNDEFINED','VK_FORMAT_UNDEFINED'),False)]
    results=[]
    for label,code,expected in variants:
        with tempfile.TemporaryDirectory(prefix='mvh-native-stencil-') as folder:
            root=Path(folder);files=[]
            fixture={'net/vulkanmod/vulkan/shader/PipelineState.java':'package net.vulkanmod.vulkan.shader;import static org.lwjgl.vulkan.VK10.*;public final class PipelineState{'+encoder+'}',
                     'net/vulkanmod/vulkan/texture/VulkanImage.java':'package net.vulkanmod.vulkan.texture;import static org.lwjgl.vulkan.VK10.*;public final class VulkanImage{'+predicate+'}',
                     'net/vulkanmod/vulkan/shader/NativeStencilPipelineState.java':code,
                     'NativeStencilRegression.java':(I/'tests/NativeStencilRegression.java').read_text()}
            for name,text in fixture.items():
                file=root/name;file.parent.mkdir(parents=True,exist_ok=True);file.write_text(text);files.append(str(file))
            classes=root/'classes'
            subprocess.run(['javac','--release','17','-cp',a.lwjgl_classpath,'-d',str(classes),*files],check=True,capture_output=True)
            trial=subprocess.run(['java','-ea','-cp',str(classes)+os.pathsep+a.lwjgl_classpath,'NativeStencilRegression'],capture_output=True,text=True,timeout=40)
            passed=trial.returncode==0
            assert passed==expected,(label,trial.stdout,trial.stderr)
            if not expected:assert 'AssertionError' in trial.stderr,'Negative control must reject behavior, not fail to load'
            results.append({'variant':label,'behavior_passed':passed,'expected':expected})
    pipeline=(I/'overlay/forge/src/main/java/net/vulkanmod/vulkan/shader/GraphicsPipeline.java').read_text()
    assert 'NativeStencilPipelineState.apply(depthStencil, state.stencilState_i, state.renderPass.getFramebuffer().getDepthFormat());' in pipeline
    assert 'renderingInfo.stencilAttachmentFormat(NativeStencilPipelineState.attachmentFormat(state.renderPass.getFramebuffer().getDepthFormat()));' in pipeline
    assert 'depthStencil.stencilTestEnable(false);' not in pipeline
    report={'passed':True,'native_struct_combinations':24576,'variants':results,'scope':'Actual production GL encoder and Vulkan pipeline helper; actual LWJGL structs, both faces, absent/present stencil, all operation/compare combinations, invalid-enum rejection and four behavior mutation controls. GPU and feature scenes remain separate.'}
    if a.report:a.report.parent.mkdir(parents=True,exist_ok=True);a.report.write_text(json.dumps(report,indent=2)+'\n')
    print('NATIVE_STENCIL_REGRESSION_PASS',24576,'MUTATION_CONTROLS',4)
if __name__=='__main__':main()
