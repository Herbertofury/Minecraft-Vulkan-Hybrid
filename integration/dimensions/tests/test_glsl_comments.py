from pathlib import Path
import argparse, json, subprocess, tempfile
I=Path(__file__).resolve().parents[1]

def main():
    p=argparse.ArgumentParser();p.add_argument('--report',type=Path);a=p.parse_args()
    with tempfile.TemporaryDirectory(prefix='mvh-glsl-comments-') as tmp:
        classes=Path(tmp)/'classes'
        parser=I/'overlay/forge/src/main/java/net/vulkanmod/vulkan/shader/parser'
        subprocess.run(['javac','--release','17','-d',str(classes),str(parser/'GlslComments.java'),str(parser/'GlslStatements.java'),str(I/'tests/GlslCommentsRegression.java'),str(I/'tests/GlslStatementsRegression.java')],check=True)
        subprocess.run(['java','-ea','-cp',str(classes),'GlslCommentsRegression'],check=True,timeout=30)
        subprocess.run(['java','-ea','-cp',str(classes),'GlslStatementsRegression'],check=True,timeout=30)
    if a.report:
        a.report.parent.mkdir(parents=True,exist_ok=True)
        a.report.write_text(json.dumps({'passed':True,'statement_boundary_cases':10,'scope':'Actual comment lexer and top-level statement boundaries: adjacent declarations, functions/for-loops, macros/continuations, quoted imports, conventional line preservation and unterminated block rejection. Actual shader compiler and rendering checked separately.'},indent=2)+'\n')

if __name__=='__main__':main()
