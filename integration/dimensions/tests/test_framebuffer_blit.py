"""Exercise unchanged production affine clipping and reject behavior mutations.

The real native copy, bindings, attachment preservation and UI pixels require
the separate marker-owned Minecraft run; this is not GPU evidence.
"""
from pathlib import Path
import argparse,hashlib,json,subprocess,tempfile
I=Path(__file__).resolve().parents[1]
def block(text,start):
 left=text.index(start);brace=text.index('{',left);level=1;end=brace+1
 while level:
  if text[end]=='{':level+=1
  elif text[end]=='}':level-=1
  end+=1
 return text[left:end]
def main():
 ap=argparse.ArgumentParser();ap.add_argument('--report',type=Path);a=ap.parse_args()
 source=I/'overlay/forge/src/main/java/net/vulkanmod/gl/GlFramebufferBlit.java';text=source.read_text()
 actual=block(text,'static int[] clippedAxis(')+'\n'+block(text,'private static int endpoint(')
 fixture='''import java.util.Arrays;public class BlitAxisRegression {
 static int checks;
 static void check(int[] actual,int...expected){if(!Arrays.equals(actual,expected))throw new AssertionError(Arrays.toString(actual)+" != "+Arrays.toString(expected));checks++;}
 static void empty(int...p){if(Production.clippedAxis(p[0],p[1],p[2],p[3],p[4],p[5],p[6])!=null)throw new AssertionError("Nonempty clipped region");checks++;}
 static void fractional(int...p){try{Production.clippedAxis(p[0],p[1],p[2],p[3],p[4],p[5],p[6]);throw new AssertionError("Fractional mapping silently rounded");}catch(UnsupportedOperationException expected){checks++;}}
 public static void main(String[] args){
 check(Production.clippedAxis(0,1920,0,1920,1920,0,1920),0,1920,0,1920);
 check(Production.clippedAxis(0,960,0,1920,960,0,1920),0,960,0,1920);
 check(Production.clippedAxis(960,0,0,1920,960,0,1920),960,0,0,1920);
 check(Production.clippedAxis(0,960,1920,0,960,0,1920),0,960,1920,0);
 check(Production.clippedAxis(960,0,1920,0,960,0,1920),960,0,1920,0);
 check(Production.clippedAxis(0,100,0,100,100,20,80),20,80,20,80);
 check(Production.clippedAxis(100,0,0,100,100,20,80),80,20,20,80);
 check(Production.clippedAxis(0,100,100,0,100,20,80),20,80,80,20);
 check(Production.clippedAxis(-20,120,0,140,100,0,140),0,100,20,120);
 check(Production.clippedAxis(120,-20,0,140,100,0,140),100,0,20,120);
 check(Production.clippedAxis(0,100,-20,80,100,0,100),20,100,0,80);
 check(Production.clippedAxis(100,0,-20,80,100,0,100),80,0,0,80);
 check(Production.clippedAxis(0,100,0,200,100,20,160),10,80,20,160);
 check(Production.clippedAxis(0,100,200,0,100,20,160),20,90,160,20);
 empty(0,100,0,100,100,100,200);empty(0,100,0,100,100,80,20);
 fractional(0,100,0,200,100,1,199);fractional(-1,100,0,200,100,0,200);
 if(checks!=18)throw new AssertionError("Missing controls");System.out.println("BLIT_AXIS_CONTROLS="+checks);
 }}'''
 variants=[('production',actual,True),('ignores_scissor',actual.replace('(low - (double)d0) / targetSpan','(0.0 - d0) / targetSpan'),False),('breaks_reverse',actual.replace('Math.min(a,b)','a'),False),('rounds_fractional',actual.replace('Math.abs(coordinate - rounded) > 0.000001','false'),False)]
 results=[]
 for name,code,expected in variants:
  with tempfile.TemporaryDirectory(prefix='mvh-blit-axis-') as folder:
   root=Path(folder);(root/'Production.java').write_text('public class Production{'+code+'}');(root/'BlitAxisRegression.java').write_text(fixture)
   subprocess.run(['javac','--release','17','-d',str(root),str(root/'Production.java'),str(root/'BlitAxisRegression.java')],check=True,capture_output=True)
   r=subprocess.run(['java','-ea','-cp',str(root),'BlitAxisRegression'],capture_output=True,text=True,timeout=20);passed=r.returncode==0
   assert passed==expected,(name,r.stdout,r.stderr)
   if not expected:assert 'AssertionError' in r.stderr
   results.append({'variant':name,'behavior_passed':passed,'expected':expected})
 report={'passed':True,'controls':18,'source_sha256':hashlib.sha256(source.read_bytes()).hexdigest(),'variants':results,'scope':'Unchanged actual production affine clipping and integral-endpoint validation; three behavior mutations rejected. No GPU, UI, FPS or full GL-contract claim.'}
 if a.report:a.report.parent.mkdir(parents=True,exist_ok=True);a.report.write_text(json.dumps(report,indent=2)+'\n')
 print('FRAMEBUFFER_BLIT_AXIS_PASS',18,'REJECTED_MUTATIONS',3)
if __name__=='__main__':main()
