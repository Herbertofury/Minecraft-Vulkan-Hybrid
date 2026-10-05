from pathlib import Path
import argparse, json, subprocess, tempfile
I=Path(__file__).resolve().parents[1]

def main():
    p=argparse.ArgumentParser();p.add_argument('--report',type=Path);a=p.parse_args()
    with tempfile.TemporaryDirectory(prefix='mvh-glsl-comments-') as tmp:
        classes=Path(tmp)/'classes'
        subprocess.run(['javac','--release','17','-d',str(classes),str(I/'overlay/forge/src/main/java/net/vulkanmod/vulkan/shader/parser/GlslComments.java'),str(I/'tests/GlslCommentsRegression.java')],check=True)
        subprocess.run(['java','-ea','-cp',str(classes),'GlslCommentsRegression'],check=True,timeout=30)
    if a.report:
        a.report.parent.mkdir(parents=True,exist_ok=True)
        a.report.write_text(json.dumps({'passed':True,'scope':'Actual comment lexer: trailing and multiline declaration comments, quoted/escaped imports, arithmetic and exact source positions; unterminated block rejection. Actual shader compiler and rendering checked separately.'},indent=2)+'\n')

if __name__=='__main__':main()
