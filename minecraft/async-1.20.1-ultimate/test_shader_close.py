#!/usr/bin/env python3
"""Execute the actual ShaderInstance mixin close body against checked native-resource doubles."""
import argparse,json,shutil,subprocess,tempfile
from pathlib import Path
p=argparse.ArgumentParser();p.add_argument('source',type=Path);p.add_argument('--baseline',type=Path);p.add_argument('--report',type=Path);args=p.parse_args()
def method(path):
 s=path.read_text();a=s.index('    private void onClose(');b=s.index('{',a);depth=1;i=b+1
 while depth:
  depth+=(s[i]=='{')-(s[i]=='}');i+=1
 return s[a:i]
test=r'''
import java.util.*;
public class ShaderCloseTest {
 static class CallbackInfo { boolean cancelled; void cancel(){cancelled=true;} }
 static class Pipeline { int calls; boolean fail; void cleanUp(){calls++;if(fail)throw new IllegalStateException("cleanup fault");} }
 static class Probe {
  Pipeline pipeline; boolean harimt$closed;
  final Map<String,com.mojang.blaze3d.shaders.Uniform> f_173333_=new HashMap<>();
  METHOD
  void close(){onClose(new CallbackInfo());}
 }
 static void check(boolean b,String m){if(!b)throw new AssertionError(m);}
 public static void main(String[] args) {
  int failed=0;
  for(int c=0;c<4;c++)try {
   Probe p=new Probe();p.pipeline=c==1?null:new Pipeline();
   com.mojang.blaze3d.shaders.Uniform uniform=new com.mojang.blaze3d.shaders.Uniform();p.f_173333_.put("real",uniform);
   if(c>=2)p.pipeline.fail=true;
   try{p.close();if(c>=2)throw new AssertionError("cleanup error swallowed");}catch(IllegalStateException expected){check(c>=2,"unexpected close failure");}
   check(uniform.calls==1,"native uniform leaked");
   if(c==0||c==3){p.close();check(uniform.calls==1&&p.pipeline.calls==1,"close was not idempotent");}
  }catch(AssertionError failure){failed++;System.out.println("FAIL case="+c+" "+failure.getMessage());}
  System.out.println("FAILED="+failed);if(failed!=0)System.exit(1);
 }
}
'''
def run(source):
 with tempfile.TemporaryDirectory(prefix='hari-shader-close-') as temp:
  root=Path(temp);(root/'ShaderCloseTest.java').write_text(test.replace('METHOD',method(source)))
  (root/'Uniform.java').write_text('package com.mojang.blaze3d.shaders; public class Uniform { public int calls; public void close(){if(++calls>1)throw new AssertionError("uniform double free");}}')
  compiler=['javac'] if shutil.which('javac') else ['java','com.sun.tools.javac.Main']
  subprocess.run(compiler+['--release','17','-d',str(root),str(root/'ShaderCloseTest.java'),str(root/'Uniform.java')],check=True)
  result=subprocess.run(['java','-cp',str(root),'ShaderCloseTest'],text=True,capture_output=True)
  return result.returncode,result.stdout
code,out=run(args.source);assert code==0,out
report={'cases':4,'passed':4,'scope':'Actual production onClose method body; GPU and uniform resources doubled; packaged reload proof is separate.'}
if args.baseline:
 code,out=run(args.baseline);assert code!=0,'original close unexpectedly passed all leak/idempotence checks';report['baseline']=out
if args.report:args.report.parent.mkdir(parents=True,exist_ok=True);args.report.write_text(json.dumps(report,indent=2)+'\n')
print('PASS: 4 actual shader close resource cases; original negative control fails' if args.baseline else 'PASS: 4 actual shader close cases')
