"""Execute the exact production GL program adapter with actual native compiler calls."""
from pathlib import Path
import argparse,hashlib,json,os,subprocess,tempfile
from native_ui_sources import relocated_uniform_source
R=Path(__file__).resolve().parents[3];S=R/'integration/dimensions/overlay/forge/src/main/java'
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def main():
    ap=argparse.ArgumentParser();ap.add_argument('--classpath',required=True);ap.add_argument('--inputs',type=Path,required=True);ap.add_argument('--output',type=Path,required=True);ap.add_argument('--production-jar',type=Path);a=ap.parse_args();assert not a.output.exists();a.output.mkdir(parents=True)
    with tempfile.TemporaryDirectory(prefix='mvh-gl-native-program-') as t:
        q=Path(t);files=[Path(__file__).with_name('NativeGlProgramRegression.java')];cp=a.classpath
        if a.production_jar:cp=str(a.production_jar)+os.pathsep+cp
        else:files += [S/'net/vulkanmod/gl/GlProgram.java',S/'net/vulkanmod/gl/NativeGlErrors.java',S/'net/vulkanmod/vulkan/shader/NativeUiProgram.java',S/'net/vulkanmod/vulkan/shader/NativeUniformUse.java',S/'net/vulkanmod/vulkan/shader/SPIRVUtils.java',relocated_uniform_source(q)]
        f=q/'net/vulkanmod/compat/observer/CompatProfiler.java';f.parent.mkdir(parents=True);f.write_text('package net.vulkanmod.compat.observer;public final class CompatProfiler{public static boolean ENABLED=false;public static int shaderCompileCount;public static long spirvCompileTimeNanos;}');files.append(f)
        build=subprocess.run(['javac','--release','17','-encoding','UTF-8','-cp',cp,'-d',str(q/'classes'),*map(str,files)],capture_output=True,text=True);(a.output/'compile.log').write_text(build.stdout+build.stderr);assert build.returncode==0,build.stderr
        run=subprocess.run(['java','-ea','-Dorg.lwjgl.system.stackSize=1024','-cp',str(q/'classes')+os.pathsep+cp,'NativeGlProgramRegression',str(a.inputs)],capture_output=True,text=True,timeout=90);(a.output/'run.log').write_text(run.stdout+run.stderr);assert run.returncode==0,(run.stdout,run.stderr)
    report={'passed':True,'production_jar_sha256':sha(a.production_jar) if a.production_jar else None,'stdout':run.stdout,'scope':'Exact GL adapter with actual native compilation/linking and retained executable bytecode, invalid/empty rejection, original Essential rectangle/circle stages, program-owned locations, matrix transpose, shader delete/detach/relink ownership. Optional profiler fixture, constants from official GL API only. No OpenGL context or call, Minecraft, generic draw acceptance, fullpack or FPS gain.'};(a.output/'REPORT.json').write_text(json.dumps(report,indent=2)+'\n');print(run.stdout)
if __name__=='__main__':main()
