"""Compile the exact record supplied by the source patch; reject original hash-only identity."""
from pathlib import Path
import argparse,json,subprocess,tempfile
root=Path(__file__).resolve().parents[1];parser=argparse.ArgumentParser();parser.add_argument('--report',type=Path);args=parser.parse_args()
record=(root/'reference-patches/compile-key.java.txt').read_text()
probe='''import java.util.*;
public class CompileKeyProbe {
RECORD
enum ShaderType{VERTEX,FRAGMENT,COMPUTE}
static Object key(String name,String source,ShaderType stage,boolean preprocessed){return new CompileKey(name,source,stage,preprocessed);}
static void check(boolean result){if(!result)throw new AssertionError("Shader cache identity collision");}
public static void main(String[]args){
 String a="#version 450\\nvoid main(){}//Aa",b="#version 450\\nvoid main(){}//BB";
 check(!a.equals(b)&&a.hashCode()==b.hashCode());
 Object raw=key("gbuffers",a,ShaderType.FRAGMENT,false);
 check(!raw.equals(key("gbuffers",b,ShaderType.FRAGMENT,false)));
 check(!raw.equals(key("shadow",a,ShaderType.FRAGMENT,false)));
 check(!raw.equals(key("gbuffers",a,ShaderType.VERTEX,false)));
 check(!raw.equals(key("gbuffers",a,ShaderType.FRAGMENT,true)));
 check(raw.equals(key(new String("gbuffers"),new String(a),ShaderType.FRAGMENT,false)));
 Map<Object,Integer> cache=new HashMap<>();cache.put(raw,7);cache.put(key("gbuffers",b,ShaderType.FRAGMENT,false),9);check(cache.size()==2&&cache.get(raw)==7);
 System.out.println("FULL_SHADER_SOURCE_STAGE_NAME_FORM_IDENTITY_PASS");
}
}
'''.replace('RECORD',record)
variants=[('production',probe,True),('original_hash_only_key',probe.replace('return new CompileKey(name,source,stage,preprocessed);','return source.hashCode() ^ stage.hashCode();'),False),('input_form_lost',probe.replace('new CompileKey(name,source,stage,preprocessed)','new CompileKey(name,source,stage,false)'),False)]
results=[]
for label,code,expected in variants:
    with tempfile.TemporaryDirectory(prefix='mvh-iris-key-') as directory:
        path=Path(directory);source=path/'CompileKeyProbe.java';source.write_text(code)
        subprocess.run(['javac','--release','17','-encoding','UTF-8','-d',str(path),str(source)],check=True,capture_output=True,text=True)
        result=subprocess.run(['java','-ea','-cp',str(path),'CompileKeyProbe'],capture_output=True,text=True)
        assert (result.returncode==0)==expected,(label,result.stdout,result.stderr)
        results.append({'variant':label,'semantic_regression_passed':result.returncode==0,'expected_pass':expected})
report={'status':'PASS','scope':'Actual source-patch key record only; no shader compilation, installed port, GPU draw, FPS or Forge compatibility inference','results':results}
if args.report:args.report.parent.mkdir(parents=True,exist_ok=True);args.report.write_text(json.dumps(report,indent=2)+'\n')
print('IRIS_SOURCE_CACHE_IDENTITY_NEGATIVE_CONTROLS_PASS',len(results))
