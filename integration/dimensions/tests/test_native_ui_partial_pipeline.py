"""Actual native returned pipeline is owned before an injected create result fails."""
from pathlib import Path
import argparse,hashlib,json,os,subprocess,tempfile
R=Path(__file__).resolve().parents[3]
source=R/'integration/dimensions/overlay/forge/src/main/java/net/vulkanmod/vulkan/shader/NativeUiPipeline.java'
ap=argparse.ArgumentParser();ap.add_argument('--classpath',required=True);ap.add_argument('--inputs',type=Path,required=True);ap.add_argument('--output',type=Path,required=True);ap.add_argument('--production-jar',type=Path,required=True);a=ap.parse_args();assert not a.output.exists();a.output.mkdir(parents=True)
sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
text=source.read_text();original=text
for call,hook in [('vkCreateGraphicsPipelines(device,0,info,null,out)','NativeUiPartialPipelineRegression.create(device,0,info,null,out)'),('vkDestroyPipeline(device,pipeline,null)','NativeUiPartialPipelineRegression.destroy(device,pipeline,null)')]:
    assert text.count(call)==1; text=text.replace(call,hook)
with tempfile.TemporaryDirectory(prefix='mvh-native-ui-partial-') as t:
    q=Path(t);f=q/'NativeUiPipeline.java';f.write_text(text)
    prof=q/'net/vulkanmod/compat/observer/CompatProfiler.java';prof.parent.mkdir(parents=True);prof.write_text('package net.vulkanmod.compat.observer;public final class CompatProfiler{public static boolean ENABLED=false;public static int shaderCompileCount;public static long spirvCompileTimeNanos;}')
    cp=str(a.production_jar)+os.pathsep+a.classpath
    files=[f,prof,Path(__file__).with_name('NativeUiPartialPipelineRegression.java'),Path(__file__).with_name('NativeUiGpuRegression.java')]
    build=subprocess.run(['javac','--release','17','-proc:none','-encoding','UTF-8','-cp',cp,'-d',str(q/'classes'),*map(str,files)],capture_output=True,text=True);(a.output/'compile.log').write_text(build.stdout+build.stderr);assert build.returncode==0,build.stderr
    run=subprocess.run(['java','-ea','-Dorg.lwjgl.system.stackSize=1024','-cp',str(q/'classes')+os.pathsep+cp,'net.vulkanmod.vulkan.shader.NativeUiPartialPipelineRegression',str(a.inputs),str(a.output/'unused-pixels')],capture_output=True,text=True,timeout=90);(a.output/'run.log').write_text(run.stdout+run.stderr);assert run.returncode==0,(run.stdout,run.stderr)
report={'passed':True,'pipeline_source_sha256':sha(source),'reference_production_jar_sha256':sha(a.production_jar),'stdout':run.stdout,'scope':'Actual discrete GPU creates a real UI pipeline using exact production compiler/original private shader inputs. Two named native calls are intercepted in a temporary otherwise-exact pipeline source copy: real create succeeds with a nonzero returned handle, but its result is injected as VK_ERROR_OUT_OF_HOST_MEMORY; real destroy counts exact-once ownership and original failure propagation. Device/resources clean up normally. No actual driver failure, exact packaged pipeline bytecode, draw pixels, Minecraft, provider/fullpack, FPS/startup gain claim.'}
(a.output/'REPORT.json').write_text(json.dumps(report,indent=2)+'\n');print(run.stdout)
