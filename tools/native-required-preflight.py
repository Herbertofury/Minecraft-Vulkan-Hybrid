"""Inspect an owned profile with the exact installed Hari gate before starting Minecraft."""
from pathlib import Path
import argparse,hashlib,json,subprocess,tempfile,os,zipfile
ap=argparse.ArgumentParser();ap.add_argument('profile',type=Path);ap.add_argument('--hari-sha256',required=True);ap.add_argument('--report',required=True,type=Path);a=ap.parse_args()
assert not a.report.exists(),'Use a fresh report; preserve diagnostics'
p=a.profile.resolve();assert json.loads((p/'MVH-NOXVIOLA-IDENTITY.json').read_text())=={'task':'mvh-noxviola-20261004','source':'Noxviola','variant':'candidate','isolated':True}
hari=list((p/'mods').glob('harimt-*-all.jar'));assert len(hari)==1
assert hashlib.sha256(hari[0].read_bytes()).hexdigest()==a.hari_sha256
source='''import java.io.*;import java.nio.file.*;import java.lang.reflect.*;import java.util.*;import java.util.zip.*;
public class NativeRequiredProbe{
 public static void main(String[]args)throws Exception{
  Class<?> gate=Class.forName("net.vulkanmod.compat.UniversalRendererGate");gate.getDeclaredMethod("nativeRequired");
  Method decision=gate.getMethod("vulkanRendererEnabled");boolean accepted=false;
  try{accepted=(boolean)decision.invoke(null);}catch(InvocationTargetException failure){Throwable cause=failure.getCause();if(!(cause instanceof IllegalStateException)||!cause.getMessage().startsWith("Native Vulkan is required"))throw failure;System.out.println("BLOCKED="+cause.getMessage());}
  System.out.println("NATIVE_ACCEPTED="+accepted);
  Method contracts=gate.getDeclaredMethod("loadContracts");contracts.setAccessible(true);Map<?,?> contract=(Map<?,?>)contracts.invoke(null);if(contract.isEmpty())throw new AssertionError("Contract missing");
  Method refs=gate.getDeclaredMethod("openGlMethodRefs",InputStream.class);refs.setAccessible(true);
  Method collect=gate.getDeclaredMethod("collectUnsupported",List.class,Map.class,Set.class,String.class);collect.setAccessible(true);
  Method nested=gate.getDeclaredMethod("scanNestedJar",InputStream.class,String.class,Map.class,Set.class,int.class);nested.setAccessible(true);
  List<Path> jars;try(var files=Files.list(Path.of("mods"))){jars=files.filter(f->f.toString().endsWith(".jar")).sorted().toList();}
  Method ids=gate.getDeclaredMethod("readModIds",ZipFile.class);ids.setAccessible(true);Set<String> loaded=new HashSet<>();
  for(Path path:jars)try(ZipFile zip=new ZipFile(path.toFile())){loaded.addAll((Set<String>)ids.invoke(null,zip));}
  List<Object> audits=new ArrayList<>();
  for(String name:List.of("InactiveModernFixGlAudit","InactiveGrassComputeGlAudit","InactiveIxerisMacOsGlAudit")){
   Class<?> audit=Class.forName("net.vulkanmod.compat."+name);Method inspect=name.equals("InactiveGrassComputeGlAudit")?audit.getDeclaredMethod("inspect",List.class,Set.class):audit.getDeclaredMethod("inspect",List.class);inspect.setAccessible(true);audits.add(name.equals("InactiveGrassComputeGlAudit")?inspect.invoke(null,jars,loaded):inspect.invoke(null,jars));
  }
  Set<String> unsupported=new TreeSet<>();
  for(Path path:jars){if(path.getFileName().toString().startsWith("harimt-"))continue;try(ZipFile zip=new ZipFile(path.toFile())){
   for(var entry:Collections.list(zip.entries())){if(entry.isDirectory())continue;boolean inactive=false;
    for(Object audit:audits){Method excludes=audit.getClass().getDeclaredMethod("excludes",Path.class,String.class);excludes.setAccessible(true);inactive|=(boolean)excludes.invoke(audit,path,entry.getName());}
    if(inactive)continue;try(InputStream input=zip.getInputStream(entry)){
     String label=path.getFileName()+"!"+entry.getName();
     if(entry.getName().endsWith(".class"))collect.invoke(null,refs.invoke(null,input),contract,unsupported,label);
     else if(entry.getName().endsWith(".jar"))nested.invoke(null,input,label,contract,unsupported,0);
    }
   }
  }}
  for(String call:unsupported)System.out.println("UNSUPPORTED="+call);
 }
}'''
with tempfile.TemporaryDirectory(prefix='mvh-installed-native-preflight-') as tmp:
    t=Path(tmp);f=t/'NativeRequiredProbe.java';f.write_text(source);subprocess.run(['javac','--release','17','-d',str(t),str(f)],check=True,capture_output=True)
    run=subprocess.run(['java','-Dharimt.vulkan.mode=require','-Dharimt.vulkan.compatCache=false','-cp',str(t)+os.pathsep+str(hari[0]),'NativeRequiredProbe'],cwd=p,check=True,capture_output=True,text=True,timeout=120)
lines=run.stdout.splitlines();accepted='NATIVE_ACCEPTED=true' in lines;calls=[l.removeprefix('UNSUPPORTED=') for l in lines if l.startswith('UNSUPPORTED=')]
assert any(l.startswith('NATIVE_ACCEPTED=') for l in lines)
report={'native_accepted':accepted,'hari':hari[0].name,'hari_sha256':a.hari_sha256,'decision':[l for l in lines if not l.startswith('UNSUPPORTED=')],'active_jars':len(list((p/'mods').glob('*.jar'))),'unsupported_calls':calls,'unique_apis':sorted({c.split('@',1)[0] for c in calls}),'minecraft_launched':False,'opengl_fallback_launched':False,'scope':'Exact installed production gate and read-only byte inspection with pinned inactive-class audits. Third-party classes never loaded; no gameplay/runtime/FPS/visual claim from a passing static audit.'}
a.report.parent.mkdir(parents=True,exist_ok=True);a.report.write_text(json.dumps(report,indent=2)+'\n')
print('NATIVE_PREFLIGHT', 'ACCEPTED' if accepted else 'BLOCKED',len(calls),'untranslated references;',len(report['unique_apis']),'unique APIs; Minecraft not launched')
raise SystemExit(0 if accepted else 2)
