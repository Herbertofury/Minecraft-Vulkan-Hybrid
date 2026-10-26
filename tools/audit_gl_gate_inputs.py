"""Read JAR bytes with Hari's production scanner; never load third-party classes."""
from pathlib import Path
import argparse,collections,hashlib,json,subprocess,tempfile
R=Path(__file__).resolve().parents[1]
p=argparse.ArgumentParser();p.add_argument('jar',type=Path);p.add_argument('--sha256',required=True);p.add_argument('--report',type=Path,required=True);a=p.parse_args()
assert not a.report.exists(),'Use a new diagnostic output path'
digest=hashlib.sha256(a.jar.read_bytes()).hexdigest();assert digest==a.sha256
probe='''import java.io.*;import java.nio.file.*;import java.lang.reflect.*;import java.util.*;import java.util.zip.*;
public class GateInventory {
 public static void main(String[] args)throws Exception {
  Class<?> gate=Class.forName("net.vulkanmod.compat.UniversalRendererGate");
  Method contracts=gate.getDeclaredMethod("loadContracts");contracts.setAccessible(true);
  Map<?,?> contract=(Map<?,?>)contracts.invoke(null);
  if(contract.isEmpty())throw new AssertionError("Translation contract missing");
  Method refs=gate.getDeclaredMethod("openGlMethodRefs",InputStream.class);refs.setAccessible(true);
  Method collect=gate.getDeclaredMethod("collectUnsupported",List.class,Map.class,Set.class,String.class);collect.setAccessible(true);
  Method nested=gate.getDeclaredMethod("scanNestedJar",InputStream.class,String.class,Map.class,Set.class,int.class);nested.setAccessible(true);
  Set<String> unsupported=new TreeSet<>();Path path=Path.of(args[0]);
  try(ZipFile zip=new ZipFile(path.toFile())) {
   for(var entry:Collections.list(zip.entries())) {
    if(entry.isDirectory())continue;
    try(InputStream input=zip.getInputStream(entry)) {
     String label=path.getFileName()+"!"+entry.getName();
     if(entry.getName().endsWith(".class"))collect.invoke(null,refs.invoke(null,input),contract,unsupported,label);
     else if(entry.getName().endsWith(".jar"))nested.invoke(null,input,label,contract,unsupported,0);
    }
   }
  }
  for(String entry:unsupported)System.out.println(entry);
 }
}'''
with tempfile.TemporaryDirectory(prefix='mvh-production-gate-inventory-') as tmp:
    root=Path(tmp);classes=root/'classes';sources=[]
    for name in ['UniversalRendererGate.java','InactiveModernFixGlAudit.java','InactiveGrassComputeGlAudit.java','InactiveIxerisMacOsGlAudit.java']:
        f=root/name;f.write_bytes((R/'integration/dimensions/overlay/forge/src/main/java/net/vulkanmod/compat'/name).read_bytes());sources.append(str(f))
    for name,source in [('FMLPaths.java','package net.minecraftforge.fml.loading;public enum FMLPaths{GAMEDIR;public java.nio.file.Path get(){return java.nio.file.Path.of(System.getProperty("user.dir"));}}'),('GateInventory.java',probe)]:
        f=root/name;f.write_text(source,encoding='utf8');sources.append(str(f))
    subprocess.run(['javac','--release','17','-d',str(classes),*sources],check=True,capture_output=True)
    relative=Path('assets/vulkanmod/compat/harimt_supported_gl_methods.properties');contract=R/'source/forge/src/main/resources'/relative
    target=classes/relative;target.parent.mkdir(parents=True);target.write_bytes(contract.read_bytes())
    result=subprocess.run(['java','-cp',str(classes),'GateInventory',str(a.jar.resolve())],check=True,capture_output=True,text=True,timeout=90)
    calls=result.stdout.splitlines();assert all('@'+a.jar.name+'!' in c or c.startswith('nested-jar-depth@') for c in calls)
    assert len(calls)==len(set(calls))
    api=sorted({c.split('@',1)[0] for c in calls});owner=collections.Counter(c.split('#',1)[0] for c in calls)
    report={'jar':a.jar.name,'sha256':digest,'translation_contract_sha256':hashlib.sha256(contract.read_bytes()).hexdigest(),'source_scanner_sha256':hashlib.sha256((R/'integration/dimensions/overlay/forge/src/main/java/net/vulkanmod/compat/UniversalRendererGate.java').read_bytes()).hexdigest(),'scope':'Actual production constant-pool and nested-JAR scanner against the accepted translation contract. Upstream class bytes are inspected, never loaded/executed or redistributed. Call-site reachability, native runtime, feature parity and FPS acceptance remain separate. No safety-gate bypass or active-call exclusion.','unsupported_call_site_count':len(calls),'unique_unsupported_api_count':len(api),'owner_counts':dict(sorted(owner.items())),'unique_unsupported_apis':api,'call_sites':calls}
    a.report.parent.mkdir(parents=True,exist_ok=True);a.report.write_text(json.dumps(report,indent=2)+'\n',encoding='utf8')
assert hashlib.sha256(a.jar.read_bytes()).hexdigest()==digest
print('PRODUCTION_GL_GATE_INVENTORY_COMPLETE',a.jar.name,len(calls),'call sites;',len(api),'APIs; upstream input unchanged')
