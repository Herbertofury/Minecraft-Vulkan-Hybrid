#!/usr/bin/env python3
"""Execute the production renderer gate against graphics settings and Forge game paths."""
import argparse,json,shutil,subprocess,tempfile
from pathlib import Path
p=argparse.ArgumentParser();p.add_argument('source',type=Path);p.add_argument('contract',type=Path);p.add_argument('--report',type=Path);args=p.parse_args()
with tempfile.TemporaryDirectory(prefix='hari-graphics-gate-') as temp:
 root=Path(temp);classes=root/'classes';classes.mkdir()
 (root/'UniversalRendererGate.java').write_bytes(args.source.read_bytes())
 (root/'FMLPaths.java').write_text('''package net.minecraftforge.fml.loading;
 public enum FMLPaths { GAMEDIR;
 public java.nio.file.Path get() { return java.nio.file.Path.of(System.getProperty("fixture.gameDir")); }}''')
 (root/'Probe.java').write_text('''import net.vulkanmod.compat.UniversalRendererGate;
 public class Probe { public static void main(String[] args) {
 System.out.println("RESULT="+UniversalRendererGate.vulkanRendererEnabled()); }}''')
 compiler=['javac'] if shutil.which('javac') else ['java','com.sun.tools.javac.Main']
 subprocess.run(compiler+['--release','17','-d',str(classes),str(root/'UniversalRendererGate.java'),str(root/'FMLPaths.java'),str(root/'Probe.java')],check=True)
 resource=classes/'assets/vulkanmod/compat/harimt_supported_gl_methods.properties';resource.parent.mkdir(parents=True);resource.write_bytes(args.contract.read_bytes())
 game=root/'game';game.mkdir();other=root/'launcher';other.mkdir()
 cases=[('missing options',None,None,True),('Fancy preserved','graphicsMode:1\n',None,True),('Fabulous preserved','graphicsMode:2\n',None,False),('Forge path beats launcher settings','graphicsMode:1\n','graphicsMode:2\n',True),('Fabulous in actual Forge path','graphicsMode:2\n','graphicsMode:1\n',False)]
 for label,option,launcher,expected in cases:
  for folder,content in [(game,option),(other,launcher)]:
   path=folder/'options.txt'
   if content is None:path.unlink(missing_ok=True)
   else:path.write_text(content)
  result=subprocess.run(['java','-cp',str(classes),'-Dfixture.gameDir='+str(game),'-Duser.dir='+str(other),'-Dharimt.vulkan.compatCache=true','Probe'],capture_output=True,text=True,check=True)
  assert 'RESULT='+str(expected).lower() in result.stdout,(label,result.stdout,result.stderr)
  if option is not None:assert (game/'options.txt').read_text()==option,'renderer changed the saved graphics setting'
 print('PASS: 5 actual renderer-gate graphics/path cases')
if args.report:
 args.report.parent.mkdir(parents=True,exist_ok=True);args.report.write_text(json.dumps({'cases':5,'passed':5,'scope':'Actual production gate Java; Forge path API doubled. Packaged Fabulous runtime is a separate gate.'},indent=2)+'\n')
