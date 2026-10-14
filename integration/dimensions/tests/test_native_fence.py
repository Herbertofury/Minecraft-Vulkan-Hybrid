"""Execute complete production Synchronization class with real GPU transfers/fences.

Vulkan/device and command-buffer wrappers are narrow fixtures; injected error results
exercise driver-failure ownership without deliberately losing the user's device.
Ordinary native operations and the production VkResult decoder are unchanged.
"""
from pathlib import Path
import argparse, hashlib, json, os, subprocess, tempfile

ROOT = Path(__file__).resolve().parents[3]
REL = Path('forge/src/main/java/net/vulkanmod/vulkan/Synchronization.java')
PACKAGE = REL.parent


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--classpath', required=True)
    ap.add_argument('--report', required=True, type=Path)
    ap.add_argument('--production-jar', type=Path)
    a = ap.parse_args()
    assert not a.report.exists()
    repaired = (ROOT/'integration/dimensions/overlay'/REL).read_text()
    original = (ROOT/'source'/REL).read_text()
    variants = [('repaired', repaired, True), ('original', original, False)]
    for label, operation in [('failed_batch_wait_recycles', 'wait for submitted command buffers'),
                             ('single_wait_error_ignored', 'wait for submitted fence'),
                             ('query_error_masked', 'query submitted fence')]:
        unsafe = repaired.replace(f'throwIfFailure(result, "{operation}");', '/* unsafe result ignored */')
        assert unsafe != repaired
        variants.append((label, unsafe, False))
    results = []
    for label, source, expected in variants:
        with tempfile.TemporaryDirectory(prefix='mvh-native-fence-') as tmp:
            t = Path(tmp)
            # Calls keep exactly the production arguments; only explicit error cases intercept results.
            instrumented = source.replace('vkWaitForFences(', 'NativeFenceRegression.waitNative(').replace('vkGetFenceStatus(', 'NativeFenceRegression.statusNative(')
            fixtures = [
                (REL, instrumented),
                (PACKAGE/'Vulkan.java', 'package net.vulkanmod.vulkan; public class Vulkan { public static org.lwjgl.vulkan.VkDevice getVkDevice(){return NativeFenceRegression.device;} }'),
                (PACKAGE/'queue/CommandPool.java', '''package net.vulkanmod.vulkan.queue;
public class CommandPool {public static class CommandBuffer {
private final org.lwjgl.vulkan.VkCommandBuffer cmd;private final long fence;
public CommandBuffer(org.lwjgl.vulkan.VkCommandBuffer c,long f){cmd=c;fence=f;}
public long getFence(){return fence;}public void reset(){net.vulkanmod.vulkan.NativeFenceRegression.reset(cmd);}
}}'''),
                (PACKAGE/'util/VUtil.java', 'package net.vulkanmod.vulkan.util; public class VUtil {public static final long UINT64_MAX=-1L;}'),
                (PACKAGE/'util/VkResult.java', (ROOT/'source'/PACKAGE/'util/VkResult.java').read_text()),
            ]
            paths = []
            for relative, text in fixtures:
                p = t/relative; p.parent.mkdir(parents=True, exist_ok=True); p.write_text(text, encoding='utf8'); paths.append(str(p))
            paths.append(str(Path(__file__).with_name('NativeFenceRegression.java')))
            build = subprocess.run(['javac', '--release', '17', '-encoding', 'UTF-8', '-cp', a.classpath, '-d', str(t/'classes'), *paths], capture_output=True, text=True)
            assert build.returncode == 0, (label, build.stderr)
            run = subprocess.run(['java', '-ea', '-Dorg.lwjgl.system.stackSize=1024', '-cp', str(t/'classes')+os.pathsep+a.classpath,
                                  'net.vulkanmod.vulkan.NativeFenceRegression'], capture_output=True, text=True, timeout=60)
            assert (run.returncode == 0) == expected, (label, run.stdout, run.stderr)
            if not expected: assert 'AssertionError: native failure masked:' in run.stderr, (label, run.stderr)
            results.append({'variant': label, 'passed': run.returncode == 0, 'expected': expected, 'stdout': run.stdout.strip(), 'stderr': run.stderr.strip()})
    packaged = None
    if a.production_jar:
        assert a.production_jar.is_file()
        with tempfile.TemporaryDirectory(prefix='mvh-packaged-fence-') as tmp:
            t = Path(tmp); paths = []
            # Only the device getter is supplied. Synchronization and CommandPool come from the actual JAR.
            for relative, text in fixtures[1:2]:
                p = t/relative; p.parent.mkdir(parents=True, exist_ok=True); p.write_text(text, encoding='utf8'); paths.append(str(p))
            paths.append(str(Path(__file__).with_name('NativeFenceRegression.java')))
            paths.append(str(Path(__file__).with_name('PackagedFenceRegression.java')))
            cp = a.classpath+os.pathsep+str(a.production_jar)
            build = subprocess.run(['javac', '--release', '17', '-encoding', 'UTF-8', '-cp', cp, '-d', str(t/'classes'), *paths], capture_output=True, text=True)
            assert build.returncode == 0, build.stderr
            assert not (t/'classes/net/vulkanmod/vulkan/Synchronization.class').exists()
            assert not (t/'classes/net/vulkanmod/vulkan/queue/CommandPool.class').exists()
            run = subprocess.run(['java', '-ea', '-Dorg.lwjgl.system.stackSize=1024', '-cp', str(t/'classes')+os.pathsep+cp,
                                  'net.vulkanmod.vulkan.PackagedFenceRegression'], capture_output=True, text=True, timeout=60)
            assert run.returncode == 0, (run.stdout, run.stderr)
            packaged = {'passed': True, 'jar_sha256': hashlib.sha256(a.production_jar.read_bytes()).hexdigest(),
                        'source_or_native_call_instrumentation': False, 'stdout': run.stdout.strip(), 'stderr': run.stderr.strip()}
    report = {'passed': True, 'cases': results, 'packaged': packaged, 'production_source_sha256': hashlib.sha256(repaired.encode()).hexdigest(),
              'scope': 'Complete production Synchronization class; real discrete GPU buffer transfers, submissions, fences and reset operations. Surrounding device/queue type fixtures; injected documented native failure results. No Minecraft or performance acceptance; no GLsync adapter acceptance.'}
    a.report.parent.mkdir(parents=True, exist_ok=True); a.report.write_text(json.dumps(report, indent=2)+'\n')
    print('NATIVE_FENCE_PASS', len(results), 'controls')


if __name__ == '__main__':
    main()
