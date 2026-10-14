"""Core/ARB production shader adapter interoperability with real shaderc calls."""
from pathlib import Path
import argparse,hashlib,json,os,subprocess,tempfile
from native_ui_sources import relocated_uniform_source
R=Path(__file__).resolve().parents[3];S=R/'integration/dimensions/overlay/forge/src/main/java'
sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
ap=argparse.ArgumentParser();ap.add_argument('--classpath',required=True);ap.add_argument('--inputs',type=Path,required=True);ap.add_argument('--output',type=Path,required=True);ap.add_argument('--production-jar',type=Path);ap.add_argument('--reference-jar',type=Path);a=ap.parse_args();assert not a.output.exists();a.output.mkdir(parents=True)
manifest=json.loads((a.inputs/'INPUTS.json').read_text())
for row in manifest['shaders']:assert sha(Path(row['local']))==row['sha256']
with tempfile.TemporaryDirectory(prefix='mvh-shader-interop-') as t:
    q=Path(t);files=[Path(__file__).with_name('NativeShaderObjectInteropRegression.java')];cp=a.classpath
    if a.production_jar:cp=str(a.production_jar)+os.pathsep+cp
    else:
        assert a.reference_jar,'Reference jar only resolves unused VRenderSystem and draw-validation types in source mode'
        cp=str(a.reference_jar)+os.pathsep+cp
        files += [S/p for p in ['net/vulkanmod/gl/GlProgram.java','net/vulkanmod/gl/GlShaderStrings.java','net/vulkanmod/vulkan/shader/NativeUiProgram.java','net/vulkanmod/vulkan/shader/SPIRVUtils.java','net/vulkanmod/mixin/compatibility/gl/GL20M.java','net/vulkanmod/mixin/compatibility/gl/ARBShaderObjectsM.java']]
        files += [R/'source/forge/src/main/java/net/vulkanmod/gl/GlShaderSource.java',relocated_uniform_source(q)]
    f=q/'net/vulkanmod/compat/observer/CompatProfiler.java';f.parent.mkdir(parents=True);f.write_text('package net.vulkanmod.compat.observer;public final class CompatProfiler{public static boolean ENABLED=false;public static int shaderCompileCount;public static long spirvCompileTimeNanos;}');files.append(f)
    build=subprocess.run(['javac','--release','17','-proc:none','-encoding','UTF-8','-cp',cp,'-d',str(q/'classes'),*map(str,files)],capture_output=True,text=True);(a.output/'compile.log').write_text(build.stdout+build.stderr);assert build.returncode==0,build.stderr
    run=subprocess.run(['java','-ea','-Dorg.lwjgl.system.stackSize=1024','-cp',str(q/'classes')+os.pathsep+cp,'NativeShaderObjectInteropRegression',str(a.inputs)],capture_output=True,text=True,timeout=90);(a.output/'run.log').write_text(run.stdout+run.stderr);assert run.returncode==0,(run.stdout,run.stderr)
report={'passed':True,'production_jar_sha256':sha(a.production_jar) if a.production_jar else None,'stdout':run.stdout,'scope':'Exact adapter/mixin method bodies with real native stage/pair compiler and original unmodified rectangle/circle inputs; mixed core/ARB object calls, shared names, exact logs/source/ByteBuffer names and extents/cursors, attached shader/current program deletion and exactly-once retirement. Constants/annotations from official libraries, optional profiler fixture only. Standalone calls to mixin implementation methods; no Minecraft injection, OpenGL context or calls, GPU rendering, full provider, fullpack or FPS/startup acceptance.'}
(a.output/'REPORT.json').write_text(json.dumps(report,indent=2)+'\n');print(run.stdout)
