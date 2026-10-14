from pathlib import Path
import argparse,hashlib,json,os,shutil,subprocess,tempfile,zipfile
P=Path(__file__).resolve().parent
upstream=json.loads((P/'UPSTREAM.json').read_text())
def patch(source,output,asm_classpath,report=None):
 assert not output.exists() and source.resolve()!=output.resolve()
 assert hashlib.sha256(source.read_bytes()).hexdigest()==upstream['sha256']
 suffix='.exe' if os.name=='nt' else '';home=os.environ.get('JAVA_HOME')
 def java(name):
  f=Path(home)/'bin'/(name+suffix) if home else None
  return str(f) if f and f.is_file() else shutil.which(name) or name
 with zipfile.ZipFile(source) as z:
  assert z.testzip() is None and len(z.namelist())==len(set(z.namelist()))
  assert not any(n.upper().endswith(('.SF','.RSA','.DSA','.EC')) for n in z.namelist())
  metadata=json.loads(z.read('pack.mcmeta'));original_format=metadata['pack']['pack_format'];assert original_format==7;metadata['pack']['pack_format']=15;fixed_metadata=(json.dumps(metadata,indent=2)+'\n').encode()
  original=z.read(upstream['changed_entry'])
  proof={}
  with tempfile.TemporaryDirectory(prefix='mvh-physics-provider-') as t:
   t=Path(t);(t/'original.class').write_bytes(original)
   stub=t/'net/diebuddies/bridge/ModLoaderFunctions.java';stub.parent.mkdir(parents=True);stub.write_text('package net.diebuddies.bridge;public class ModLoaderFunctions{public static int mask;public static boolean isModLoaded(String n){return switch(n){case "sodium"->(mask&1)!=0;case "rubidium"->(mask&2)!=0;case "embeddium"->(mask&4)!=0;default->false;};}}')
   test=t/'ProviderTruthRegression.java';test.write_text('import net.diebuddies.physics.StarterClient;import net.diebuddies.bridge.ModLoaderFunctions;public class ProviderTruthRegression{public static void main(String[]a){for(int m=0;m<32;m++){ModLoaderFunctions.mask=m;StarterClient.iris=(m&24)!=0;StarterClient.test();if(StarterClient.sodium!=((m&7)!=0))throw new AssertionError("Wrong provider selection "+m);}System.out.println("32 actual provider truth controls PASS");}}')
   for mode in ['patched','original_negative']:
    root=t/mode;root.mkdir();control=root/'control';tool=root/'ProviderDetectionPatch.java';text=(P/'ProviderDetectionPatch.java').read_text()
    if mode=='original_negative':text=text.replace('m.instructions.set(n,new InsnNode(Opcodes.ICONST_0));','')
    tool.write_text(text);subprocess.run([java('javac'),'-cp',asm_classpath,'-d',str(root),str(tool)],check=True,capture_output=True)
    fixed=root/'patched.class';subprocess.run([java('java'),'-cp',str(root)+os.pathsep+asm_classpath,'ProviderDetectionPatch',str(t/'original.class'),str(fixed),str(control)],check=True,capture_output=True)
    subprocess.run([java('javac'),'-cp',str(control),'-d',str(control),str(stub),str(test)],check=True,capture_output=True)
    check=subprocess.run([java('java'),'-cp',str(control),'ProviderTruthRegression'],capture_output=True,text=True)
    if mode=='patched':assert check.returncode==0,check.stderr;proof['positive']=check.stdout.strip();patched=fixed.read_bytes()
    else:assert check.returncode!=0 and 'Wrong provider selection 8' in check.stderr;proof['negative_original_iris_implies_sodium']='rejected'
   with zipfile.ZipFile(output,'w') as dest:
    for entry in z.infolist():dest.writestr(entry,patched if entry.filename==upstream['changed_entry'] else fixed_metadata if entry.filename=='pack.mcmeta' else z.read(entry))
 with zipfile.ZipFile(source) as a,zipfile.ZipFile(output) as b:
  assert a.namelist()==b.namelist()
  assert sorted(n for n in a.namelist() if not n.endswith('/') and a.read(n)!=b.read(n))==sorted([upstream['changed_entry'],'pack.mcmeta'])
 proof.update({'source_sha256':upstream['sha256'],'output_sha256':hashlib.sha256(output.read_bytes()).hexdigest(),'changed_entries':[upstream['changed_entry'],'pack.mcmeta'],'all_other_code_assets_licenses_equal':True,'original_pack_format':original_format,'pack_format':15,'runtime_acceptance':False,'scope':'Actual patched decision bytecode, original decision negative control; runtime and performance separate'})
 if report:assert not report.exists();report.write_text(json.dumps(proof,indent=2)+'\n')
 return proof
if __name__=='__main__':
 p=argparse.ArgumentParser();p.add_argument('source',type=Path);p.add_argument('output',type=Path);p.add_argument('--asm-classpath',required=True);p.add_argument('--report',type=Path);a=p.parse_args();print(json.dumps(patch(a.source,a.output,a.asm_classpath,a.report),indent=2))
