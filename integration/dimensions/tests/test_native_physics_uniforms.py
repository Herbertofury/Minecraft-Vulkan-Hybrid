"""Production native reflection/array/matrix semantics with actual shaderc, no GL context."""
from pathlib import Path
import argparse,hashlib,json,os,subprocess,tempfile
from native_ui_sources import relocated_uniform_source
R=Path(__file__).resolve().parents[3];S=R/'integration/dimensions/overlay/forge/src/main/java'
ap=argparse.ArgumentParser();ap.add_argument('--classpath',required=True);ap.add_argument('--output',required=True,type=Path);ap.add_argument('--production-jar',type=Path);a=ap.parse_args();assert not a.output.exists();a.output.mkdir(parents=True)
with tempfile.TemporaryDirectory(prefix='mvh-native-physics-uniform-') as tmp:
    q=Path(tmp);files=[Path(__file__).with_name('NativePhysicsUniformRegression.java')];cp=a.classpath
    if a.production_jar:cp=str(a.production_jar)+os.pathsep+cp
    else:files += [S/name for name in ['net/vulkanmod/gl/GlProgram.java','net/vulkanmod/gl/NativeGlErrors.java','net/vulkanmod/vulkan/shader/NativeUiProgram.java','net/vulkanmod/vulkan/shader/NativeUniformUse.java','net/vulkanmod/vulkan/shader/SPIRVUtils.java']]+[relocated_uniform_source(q)]
    profiler=q/'net/vulkanmod/compat/observer/CompatProfiler.java';profiler.parent.mkdir(parents=True);profiler.write_text('package net.vulkanmod.compat.observer;public final class CompatProfiler{public static boolean ENABLED=false;public static int shaderCompileCount;public static long spirvCompileTimeNanos;}');files.append(profiler)
    build=subprocess.run(['javac','--release','17','-encoding','UTF-8','-cp',cp,'-d',str(q/'classes'),*map(str,files)],capture_output=True,text=True);(a.output/'compile.log').write_text(build.stdout+build.stderr);assert build.returncode==0,build.stderr
    run=subprocess.run(['java','-ea','-Dorg.lwjgl.system.stackSize=1024','-cp',str(q/'classes')+os.pathsep+cp,'NativePhysicsUniformRegression'],capture_output=True,text=True,timeout=90);(a.output/'run.log').write_text(run.stdout+run.stderr);assert run.returncode==0,(run.stdout,run.stderr)
report={'passed':True,'production_sha256':hashlib.sha256(a.production_jar.read_bytes()).hexdigest() if a.production_jar else None,'stdout':run.stdout,'scope':'Exact native shader compiler/reflected ABI, active-variable exclusion, typed scalar/vector/matrix arrays, arithmetic array locations, cursor preservation, wrong type/count and relink ownership. No Minecraft, GL context, GPU pixels, fullpack or performance claim.'}
(a.output/'REPORT.json').write_text(json.dumps(report,indent=2)+'\n');print(run.stdout)
