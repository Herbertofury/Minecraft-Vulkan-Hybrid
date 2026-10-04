"""Run an original Linux-native focused test with the accepted Windows LWJGL bytes.
Only native library selection, classpath separator and UTF-8 test-process encoding change.
Production source and original regression programs remain byte-identical.
"""
import os,sys
from pathlib import Path
root=Path(__file__).resolve().parents[1]
if os.name!='nt':raise SystemExit('This adapter is for Windows native checks.')
name=sys.argv[1]
if name not in ['gl_shader_source','texture_upload_span']:raise SystemExit('Unsupported native regression')
p=root/'minecraft/async-1.20.1-ultimate'/('test_'+name+'.py')
s=p.read_text(encoding='utf-8')
s='import os\n'+s
s=s.replace('lwjgl-3.3.1-natives-linux.jar','lwjgl-3.3.1-natives-windows.jar').replace('22ef2afa31a1740a337ec9c6806c6b8d97e931a63e2c43270cbaf14fb3f6fc4e','093d13d62a6434bc656bf10a3b37e2530fd9af3b0bc20f8e9545be58659d1443')
s=s.replace("':'.join(","os.pathsep.join(")
os.environ['JAVA_TOOL_OPTIONS']='-Dfile.encoding=UTF-8'
sys.argv=[str(p),str(root/'source'),*sys.argv[2:]]
exec(compile(s,str(p),'exec'),{'__name__':'__main__','__file__':str(p)})
