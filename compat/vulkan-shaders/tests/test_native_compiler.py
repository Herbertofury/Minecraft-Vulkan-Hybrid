"""Pinned full Iris reference compiler + real official shaderc; no mod/renderer installation."""
from pathlib import Path
import argparse,difflib,hashlib,json,os,subprocess,tempfile,urllib.request
H=Path(__file__).resolve().parents[1];REL=Path('src/main/java/net/irisshaders/iris/vulkan/shader/IrisSPIRVCompiler.java')
ORIGIN=json.loads((H/'reference-patches/ORIGIN.json').read_text());SHA=ORIGIN['original_sha256']
URL='https://raw.githubusercontent.com/trptrk/Iris-Vulkan/'+ORIGIN['commit']+'/'+REL.as_posix()
def fixture(root,compiler,cache):
    sources=[]
    texts={REL:compiler,'src/main/java/net/irisshaders/iris/vulkan/shader/NativeSpirvCache.java':cache,
     'src/main/java/net/irisshaders/iris/gl/shader/ShaderType.java':'package net.irisshaders.iris.gl.shader;public enum ShaderType{VERTEX,FRAGMENT,GEOMETRY,COMPUTE,TESSELATION_CONTROL,TESSELATION_EVAL}',
     'src/main/java/org/apache/logging/log4j/Logger.java':'package org.apache.logging.log4j;public final class Logger{public void info(String s,Object...v){}public void debug(String s,Object...v){}public void error(String s,Object...v){}public void warn(String s,Object...v){}}',
     'src/main/java/org/apache/logging/log4j/LogManager.java':'package org.apache.logging.log4j;public final class LogManager{public static Logger getLogger(Class<?> c){return new Logger();}}',
     'src/main/java/NativeCompilerProbe.java':(H/'tests/NativeCompilerProbe.java').read_text(),
     'src/main/java/net/irisshaders/iris/vulkan/shader/NativeCacheProbe.java':(H/'tests/NativeCacheProbe.java').read_text()}
    for name,body in texts.items():
        p=root/name;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(body,encoding='utf8');sources.append(str(p))
    return sources
def main():
    ap=argparse.ArgumentParser();ap.add_argument('--reference-source',type=Path);ap.add_argument('--download-reference',action='store_true');ap.add_argument('--lwjgl-classpath',required=True);ap.add_argument('--legacy-lwjgl-classpath');ap.add_argument('--report',type=Path);ap.add_argument('--write-patch',type=Path);a=ap.parse_args()
    assert bool(a.reference_source)!=a.download_reference
    original=a.reference_source.read_bytes() if a.reference_source else urllib.request.urlopen(URL,timeout=40).read()
    assert hashlib.sha256(original).hexdigest()==SHA,'Pinned compiler changed'
    with tempfile.TemporaryDirectory(prefix='mvh-iris-key-patch-') as tmp:
        root=Path(tmp);file=root/REL;file.parent.mkdir(parents=True);file.write_bytes(original)
        subprocess.run(['git','apply','--check',str((H/'reference-patches/iris-spirv-full-input-key.patch').resolve())],cwd=root,check=True,capture_output=True)
        subprocess.run(['git','apply',str((H/'reference-patches/iris-spirv-full-input-key.patch').resolve())],cwd=root,check=True,capture_output=True)
        keyed=file.read_text(encoding='utf8')
    assert hashlib.sha256(keyed.encode()).hexdigest()==ORIGIN['modified_text_sha256']
    repaired=keyed.replace('private static final Map<CompileKey, ByteBuffer> spirvCache = new ConcurrentHashMap<>();','private static final NativeSpirvCache<CompileKey> spirvCache = new NativeSpirvCache<>();')
    assert repaired!=keyed and repaired.count('spirvCache.put(cacheKey, spirv);')==2
    repaired=repaired.replace('spirvCache.put(cacheKey, spirv);','spirvCache.put(cacheKey, spirv, (long) cacheKey.source().length() + cacheKey.name().length());')
    cache=(H/'reference-patches/NativeSpirvCache.java').read_text();results=[]
    variants=[('repaired',repaired,cache,True),('previous_key_only_shared_buffer',keyed,cache,False),('source_hash_collision',repaired.replace('new CompileKey(name, source, shaderType, false)','new CompileKey(name, String.valueOf(source.hashCode()), shaderType, false)'),cache,False),('entry_bound_removed',repaired,cache.replace('MAX_ENTRIES=128','MAX_ENTRIES=256'),False),('byte_bound_doubled',repaired,cache.replace('MAX_PAYLOAD_BYTES=16L','MAX_PAYLOAD_BYTES=32L'),False)]
    for name,compiler,helper,expected in variants:
        with tempfile.TemporaryDirectory(prefix='mvh-iris-native-compiler-') as tmp:
            root=Path(tmp);sources=fixture(root,compiler,helper);classes=root/'classes'
            compiled=subprocess.run(['javac','--release','17','-cp',a.lwjgl_classpath,'-d',str(classes),*sources],capture_output=True,text=True)
            assert compiled.returncode==0,(name,compiled.stderr)
            cp=str(classes)+os.pathsep+a.lwjgl_classpath
            first=subprocess.run(['java','-ea','-cp',cp,'NativeCompilerProbe'],capture_output=True,text=True,timeout=60)
            second=subprocess.run(['java','-ea','-cp',cp,'net.irisshaders.iris.vulkan.shader.NativeCacheProbe'],capture_output=True,text=True,timeout=30)
            passed=first.returncode==0 and second.returncode==0
            assert passed==expected,(name,first.stdout,first.stderr,second.stdout,second.stderr)
            if not expected:assert 'AssertionError' in first.stderr+second.stderr,'Control must reject semantics, not fail native loading'
            results.append({'variant':name,'native_compiler_passed':first.returncode==0,'cache_bounds_passed':second.returncode==0,'expected_combined':expected})
    legacy=None
    if a.legacy_lwjgl_classpath:
        with tempfile.TemporaryDirectory(prefix='mvh-iris-legacy-api-') as tmp:
            root=Path(tmp);sources=fixture(root,repaired,cache)
            old=subprocess.run(['javac','--release','17','-cp',a.legacy_lwjgl_classpath,'-d',str(root/'classes'),*sources],capture_output=True,text=True)
            assert old.returncode!=0 and 'shaderc_compile_options_set_vulkan_rules_relaxed' in old.stderr
            legacy={'compiled':False,'missing_binding':'shaderc_compile_options_set_vulkan_rules_relaxed','scope':'Actual complete compiler against Hari shaderc 3.3.3 bindings. No option removed and no game/runtime dependency changed.'}
    patch=''.join(difflib.unified_diff(keyed.splitlines(True),repaired.splitlines(True),fromfile='a/'+REL.as_posix(),tofile='b/'+REL.as_posix()))
    if a.write_patch:assert not a.write_patch.exists();a.write_patch.write_bytes(patch.encode())
    report={'passed':True,'upstream_repository':ORIGIN['repository'],'upstream_commit':ORIGIN['commit'],'original_compiler_sha256':SHA,'key_only_compiler_sha256':ORIGIN['modified_text_sha256'],'repaired_compiler_sha256':hashlib.sha256(repaired.encode()).hexdigest(),'cache_source_sha256':hashlib.sha256(cache.encode()).hexdigest(),'native_sdk':'Official LWJGL/shaderc 3.3.6; Java 17; isolated CPU native compilation, no GPU draw or Minecraft integration','cases':results,'legacy_runtime_api':legacy,'scope':'Actual vertex/fragment/compute SPIR-V, invalid GLSL rejection, real Java source hash collision, raw/preprocessed/name keys, writable direct-buffer content/cursor isolation, reload clear, entry/LRU/payload bounds; previous key-only compiler and three mutations rejected. Logger and Iris enum are fixtures. Original preprocessing and native shaderc execute. No active shader pack, Forge compatibility, FPS/startup/hitch gain or full Iris/Flywheel acceptance.'}
    if a.report:a.report.parent.mkdir(parents=True,exist_ok=True);a.report.write_text(json.dumps(report,indent=2)+'\n')
    print('IRIS_REFERENCE_NATIVE_COMPILER_AND_CACHE_PASS',len(variants),'CONTROLS; LEGACY_API',legacy)
if __name__=='__main__':main()
