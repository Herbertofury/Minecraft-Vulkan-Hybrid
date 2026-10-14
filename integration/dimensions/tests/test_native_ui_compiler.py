"""Original Essential UI GLSL through the production frontend and actual shaderc.
Inputs remain private/unmodified. No executable Essential classes are loaded.
"""
from pathlib import Path
import argparse, hashlib, json, os, subprocess, tempfile
from native_ui_sources import relocated_uniform_source
R=Path(__file__).resolve().parents[3]
S=R/'integration/dimensions/overlay/forge/src/main/java'
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def main():
    ap=argparse.ArgumentParser();ap.add_argument('--classpath',required=True);ap.add_argument('--inputs',type=Path,required=True);ap.add_argument('--output',type=Path,required=True);ap.add_argument('--production-jar',type=Path);a=ap.parse_args()
    assert not a.output.exists()
    manifest=json.loads((a.inputs/'INPUTS.json').read_text())
    assert len(manifest['shaders'])==5
    for row in manifest['shaders']:assert sha(Path(row['local']))==row['sha256']
    a.output.mkdir(parents=True)
    with tempfile.TemporaryDirectory(prefix='mvh-native-ui-compiler-') as t:
        q=Path(t);files=[Path(__file__).with_name('NativeUiCompilerRegression.java')]
        cp=a.classpath
        if a.production_jar:cp=str(a.production_jar)+os.pathsep+cp
        else:
            files += [S/'net/vulkanmod/vulkan/shader/NativeUiProgram.java',S/'net/vulkanmod/vulkan/shader/SPIRVUtils.java',relocated_uniform_source(q)]
        # Only the optional profiler is replaced; the compiler and frontend are exact.
        f=q/'net/vulkanmod/compat/observer/CompatProfiler.java';f.parent.mkdir(parents=True);f.write_text('package net.vulkanmod.compat.observer;public final class CompatProfiler{public static boolean ENABLED=false;public static int shaderCompileCount;public static long spirvCompileTimeNanos;}');files.append(f)
        build=subprocess.run(['javac','--release','17','-encoding','UTF-8','-cp',cp,'-d',str(q/'classes'),*map(str,files)],capture_output=True,text=True)
        (a.output/'compile.log').write_text(build.stdout+build.stderr);assert build.returncode==0,build.stderr
        run=subprocess.run(['java','-ea','-Dorg.lwjgl.system.stackSize=1024','-cp',str(q/'classes')+os.pathsep+cp,'NativeUiCompilerRegression',str(a.inputs),str(a.output/'spv')],capture_output=True,text=True,timeout=90)
        (a.output/'run.log').write_text(run.stdout+run.stderr);assert run.returncode==0,(run.stdout,run.stderr)
    report={'passed':True,'exact_original_inputs':manifest,'production_jar_sha256':sha(a.production_jar) if a.production_jar else None,'native_compiler':'Actual production shaderc with exact frontend/reflected layout, optional profiler fixture only','stdout':run.stdout,'scope':'Three original Essential/Elementa UI pairs compile to real Vulkan SPIR-V, sampler and uniform ABI, caller bytecode ownership, deterministic reload and six invalid-program controls. No Essential executable classes, Minecraft, GL context, pixels or FPS acceptance.'}
    (a.output/'REPORT.json').write_text(json.dumps(report,indent=2)+'\n');print(run.stdout)
if __name__=='__main__':main()
