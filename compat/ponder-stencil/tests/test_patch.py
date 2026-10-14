"""Behavior-level ASM repair controls without loading or redistributing upstream classes."""
from pathlib import Path
import argparse,json,os,re,subprocess,tempfile
H=Path(__file__).resolve().parents[1]
def normalized(text):
    lines=[]
    for line in text.splitlines():
        if re.match(r'\s+\d+:',line):
            line=line.replace('mvhpondercompat/NativeStencilBridge.','org/lwjgl/opengl/GL11.')
            line=re.sub(r'#\d+','#REF',line);lines.append(re.sub(r'\s+',' ',line).strip())
    return lines
def main():
    ap=argparse.ArgumentParser();ap.add_argument('--asm-classpath',required=True);ap.add_argument('--report',type=Path);a=ap.parse_args();results=[]
    with tempfile.TemporaryDirectory(prefix='mvh-ponder-patch-control-') as folder:
        root=Path(folder);patcher=root/'patcher'
        subprocess.run(['javac','--release','17','-cp',a.asm_classpath,'-d',str(patcher),str(H/'src/PatchPonderStencil.java')],check=True,capture_output=True)
        base='''package net.createmod.catnip.gui.element;import org.lwjgl.opengl.GL11;public interface StencilElement {
          default void prepareStencil(){GL11.glDisable(2960);GL11.glEnable(2960);geometry(7);}
          default void prepareElement(){GL11.glEnable(2960);geometry(11);}
          default void cleanUp(){GL11.glDisable(2960);geometry(23);}
          void geometry(int value);
        }'''
        variants=[('production_shape',base,True),('unknown_capability',base.replace('2960','3042'),False),('lost_disable',base.replace('GL11.glDisable(2960);geometry(23);','geometry(23);'),False),('unknown_call_owner',base.replace('GL11.gl','org.lwjgl.opengl.GL11C.gl'),False),('unknown_call_site',base.replace('prepareElement()','arbitraryMethod()'),False)]
        for label,text,expected in variants:
            case=root/label;case.mkdir();sources=[]
            for name,value in {'net/createmod/catnip/gui/element/StencilElement.java':text,'org/lwjgl/opengl/GL11.java':'package org.lwjgl.opengl;public final class GL11{public static void glEnable(int c){}public static void glDisable(int c){}}','org/lwjgl/opengl/GL11C.java':'package org.lwjgl.opengl;public final class GL11C{public static void glEnable(int c){}public static void glDisable(int c){}}'}.items():
                p=case/name;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(value);sources.append(str(p))
            classes=case/'classes';subprocess.run(['javac','--release','17','-d',str(classes),*sources],check=True,capture_output=True)
            original=classes/'net/createmod/catnip/gui/element/StencilElement.class';out=case/'StencilElement.class'
            trial=subprocess.run(['java','-cp',str(patcher)+os.pathsep+a.asm_classpath,'PatchPonderStencil',str(original),str(out)],capture_output=True,text=True,timeout=30)
            passed=trial.returncode==0;assert passed==expected,(label,trial.stdout,trial.stderr)
            if passed:
                before=subprocess.check_output(['javap','-c','-p',str(original)],text=True);after=subprocess.check_output(['javap','-c','-p',str(out)],text=True)
                assert normalized(before)==normalized(after),'Instructions other than four known owners changed'
                assert after.count('mvhpondercompat/NativeStencilBridge.')==4 and 'org/lwjgl/opengl/GL11.' not in after
                assert original.read_bytes()!=out.read_bytes()
            else:assert 'IllegalArgumentException' in trial.stderr and not out.exists()
            results.append({'variant':label,'repair_accepted':passed,'expected':expected})
    report={'passed':True,'variants':results,'scope':'Actual ASM patcher rejects capability, owner, site and count mutations. Successful shape retains every disassembled instruction except four call owners. Whole-input/nested/class hashes and private-original byte parity are checked separately by recipe.'}
    if a.report:a.report.parent.mkdir(parents=True,exist_ok=True);a.report.write_text(json.dumps(report,indent=2)+'\n')
    print('PONDER_STENCIL_PATCH_CONTRACT_PASS',len(variants))
if __name__=='__main__':main()
