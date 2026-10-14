"""Run production shaderc code with real natives and audit result lifetime at native boundaries.

Only the optional profiler is a fixture. The native compiler, includes, GLSL and SPIR-V
execute unmodified. Instrumentation counts original allocation/release calls; it adds
no release or compiler behavior. This is component proof, not a Minecraft/FPS claim.
"""
from pathlib import Path
import argparse, hashlib, json, os, subprocess, tempfile

ROOT=Path(__file__).resolve().parents[3]
REL=Path('forge/src/main/java/net/vulkanmod/vulkan/shader/SPIRVUtils.java')

def instrument(text):
    # Wrap the actual calls, retaining every argument, return value and native operation.
    text=text.replace('shaderc_compile_into_spv(compiler, source, shaderKind.kind, filename, "main", options)',
                      'trackResult(shaderc_compile_into_spv(compiler, source, shaderKind.kind, filename, "main", options))')
    text=text.replace('shaderc_result_release(result);','trackRelease(result);').replace('shaderc_result_release(handle);','trackRelease(handle);')
    marker='public class SPIRVUtils {'
    assert text.count(marker)==1
    return text.replace(marker, marker+'''
    private static final java.util.Set<Long> testLive = new java.util.HashSet<>();
    private static int testAcquired, testReleased;
    private static long trackResult(long result) {
        if (result != 0) { if (!testLive.add(result)) throw new AssertionError("duplicate native result"); testAcquired++; }
        return result;
    }
    private static void trackRelease(long result) {
        if (!testLive.remove(result)) throw new AssertionError("native result released twice");
        shaderc_result_release(result); testReleased++;
    }
    public static int liveTestResults() { return testLive.size(); }
    public static int acquiredTestResults() { return testAcquired; }
    public static int releasedTestResults() { return testReleased; }
''')

def main():
    ap=argparse.ArgumentParser();ap.add_argument('--classpath',required=True);ap.add_argument('--report',type=Path,required=True)
    a=ap.parse_args();assert not a.report.exists(),'Preserve previous reports'
    for item in a.classpath.split(os.pathsep): assert Path(item).is_file(),item
    original=(ROOT/'source'/REL).read_text(encoding='utf8')
    repaired=(ROOT/'integration/dimensions/overlay'/REL).read_text(encoding='utf8')
    variants=[('repaired',repaired,True),('archived_original',original,False),
              ('successful_release_removed',repaired.replace('if (handle != 0) shaderc_result_release(handle);','if (handle != 0) { /* rejected lifetime mutation */ }'),False),
              ('failed_release_removed',repaired.replace('if (!transferred) shaderc_result_release(result);','if (!transferred) { /* rejected lifetime mutation */ }'),False)]
    results=[]
    for name,source,expected in variants:
        with tempfile.TemporaryDirectory(prefix='mvh-shader-lifetime-') as tmp:
            t=Path(tmp);src=t/'net/vulkanmod/vulkan/shader/SPIRVUtils.java';src.parent.mkdir(parents=True);src.write_text(instrument(source),encoding='utf8')
            fixture=t/'net/vulkanmod/compat/observer/CompatProfiler.java';fixture.parent.mkdir(parents=True)
            fixture.write_text('package net.vulkanmod.compat.observer;public final class CompatProfiler {public static boolean ENABLED=false;public static int shaderCompileCount;public static long spirvCompileTimeNanos;}')
            classes=t/'classes'
            build=subprocess.run(['javac','--release','17','-encoding','UTF-8','-cp',a.classpath,'-d',str(classes),str(src),str(fixture),str(Path(__file__).with_name('ShaderLifetimeRegression.java'))],capture_output=True,text=True)
            assert build.returncode==0,(name,build.stderr)
            run=subprocess.run(['java','-ea','-cp',str(classes)+os.pathsep+a.classpath,'ShaderLifetimeRegression'],capture_output=True,text=True,timeout=90)
            assert (run.returncode==0)==expected,(name,run.stdout,run.stderr)
            if not expected:assert 'AssertionError: unreleased native compiler results:' in run.stderr,(name,run.stderr)
            results.append({'variant':name,'passed':run.returncode==0,'expected':expected,'stdout':run.stdout.strip(),'stderr':run.stderr.strip()})
    report={'passed':True,'original_sha256':hashlib.sha256(original.encode()).hexdigest(),'overlay_sha256':hashlib.sha256(repaired.encode()).hexdigest(),
            'classpath':[{'file':Path(p).name,'sha256':hashlib.sha256(Path(p).read_bytes()).hexdigest()} for p in a.classpath.split(os.pathsep)],'cases':results,
            'native_compiler_results_per_variant':160,'scope':'Actual production shaderc native compile/free and error handling, unchanged GLSL/bytecode; optional profiler fixture, native-boundary counters only. Original and two missing-release mutations reject. No Minecraft, GPU draw, pack/FPS/visual acceptance.'}
    a.report.parent.mkdir(parents=True,exist_ok=True);a.report.write_text(json.dumps(report,indent=2)+'\n')
    print('SHADER_NATIVE_LIFETIME_PASS',len(results),'controls')

if __name__=='__main__':main()
