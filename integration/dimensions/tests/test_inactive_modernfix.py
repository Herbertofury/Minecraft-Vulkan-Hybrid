"""Run the production gate with pinned real JAR bytes and effective-state negative controls."""
from pathlib import Path
import argparse,hashlib,json,os,shutil,subprocess,tempfile,urllib.request,zipfile
I=Path(__file__).resolve().parents[1];R=I.parents[1]
def main():
 p=argparse.ArgumentParser();p.add_argument('--modernfix83',type=Path);p.add_argument('--modernfix85',type=Path);p.add_argument('--report',type=Path);a=p.parse_args();pins=json.loads((I/'MODERNFIX-GL-AUDIT.json').read_text())['pins'];reports=[]
 with tempfile.TemporaryDirectory(prefix='mvh-modernfix-gl-') as tmp:
  root=Path(tmp);classes=root/'classes';sources=[]
  def source(name,text):
   f=root/name;f.parent.mkdir(parents=True,exist_ok=True);f.write_text(text,encoding='utf8');sources.append(str(f))
  for name in ['UniversalRendererGate.java','InactiveModernFixGlAudit.java','InactiveGrassComputeGlAudit.java','InactiveIxerisMacOsGlAudit.java']:
   f=root/name;f.write_bytes((I/'overlay/forge/src/main/java/net/vulkanmod/compat'/name).read_bytes());sources.append(str(f))
  source('FMLPaths.java','package net.minecraftforge.fml.loading;public enum FMLPaths {GAMEDIR;public java.nio.file.Path get(){return java.nio.file.Path.of(System.getProperty("user.dir"));}}')
  source('ModernFixMixinPlugin.java','package org.embeddedt.modernfix.core;public class ModernFixMixinPlugin {public static ModernFixMixinPlugin instance=Boolean.getBoolean("fixture.unknown")?null:new ModernFixMixinPlugin();public Object config=new Object();public boolean isOptionEnabled(String mixin){if(!mixin.equals("feature.registry_event_progress.GameDataMixin"))throw new AssertionError(mixin);return Boolean.getBoolean("fixture.enabled");}}')
  source('Probe.java','import net.vulkanmod.compat.UniversalRendererGate;public class Probe {public static void main(String[] a){boolean expected=Boolean.parseBoolean(a[0]);boolean actual=UniversalRendererGate.vulkanRendererEnabled();System.out.println("REASON="+UniversalRendererGate.reason());if(actual!=expected)throw new AssertionError("gate="+actual+" expected="+expected);}}')
  source('Policy.java','package net.vulkanmod.compat;public class Policy{public static void main(String[]a){String digest="954ff22f601be8de3978a0d7636a138dc93801c6ac55cfd71ef1cd2895de8e5f";for(var state:InactiveModernFixGlAudit.State.values()){if(InactiveModernFixGlAudit.allows(digest,InactiveModernFixGlAudit.CLASS,state)!=(state==InactiveModernFixGlAudit.State.DISABLED))throw new AssertionError("effective state");if(InactiveModernFixGlAudit.allows(digest,"other/AsyncLoadingScreen.class",state))throw new AssertionError("wrong class");if(InactiveModernFixGlAudit.allows(null,InactiveModernFixGlAudit.CLASS,state))throw new AssertionError("missing hash");if(InactiveModernFixGlAudit.allows("bad",InactiveModernFixGlAudit.CLASS,state))throw new AssertionError("changed hash");}System.out.println("EXACT_PIN_CLASS_STATE_NEGATIVE_CONTROLS_PASS");}}')
  subprocess.run(['javac','--release','17','-d',str(classes),*sources],check=True)
  contract=classes/'assets/vulkanmod/compat/harimt_supported_gl_methods.properties';contract.parent.mkdir(parents=True);contract.write_bytes((R/'source/forge/src/main/resources/assets/vulkanmod/compat'/contract.name).read_bytes())
  subprocess.run(['java','-ea','-cp',str(classes),'net.vulkanmod.compat.Policy'],check=True)
  for record in pins:
   jar= a.modernfix83 if '5.27.83' in record['version'] else a.modernfix85
   if jar is None:
    jar=root/record['file'];jar.write_bytes(urllib.request.urlopen(record['url'],timeout=45).read())
   data=jar.read_bytes();assert len(data)==record['bytes'];assert hashlib.sha256(data).hexdigest()==record['sha256'];assert hashlib.sha512(data).hexdigest()==record['sha512']
   game=root/record['version'];mods=game/'mods';mods.mkdir(parents=True);target=mods/record['file'];target.write_bytes(data)
   # Config flips reuse the same real JAR and written compatibility cache, without timestamp edits.
   cases=[('disabled',[],True),('enabled',['-Dfixture.enabled=true'],False),('unknown',['-Dfixture.unknown=true'],False),('disabled-again',[],True)]
   for title,flags,expected in cases:
    r=subprocess.run(['java',*flags,'-cp',str(classes),'Probe',str(expected).lower()],cwd=game,check=True,capture_output=True,text=True,timeout=30);reports.append({'version':record['version'],'case':title,'expected_vulkan':expected,'output':r.stdout.strip()})
   # Same version and calls, changed bytes: the digest must block the exemption.
   before=target.stat();changed=bytearray(data);central=changed.index(b'PK\x01\x02');changed[central+12]^=1;target.write_bytes(changed);os.utime(target,ns=(before.st_atime_ns,before.st_mtime_ns));assert target.stat().st_size==before.st_size and target.stat().st_mtime_ns==before.st_mtime_ns
   r=subprocess.run(['java','-cp',str(classes),'Probe','false'],cwd=game,check=True,capture_output=True,text=True,timeout=30);reports.append({'version':record['version'],'case':'modified-input-same-size-time','expected_vulkan':False,'output':r.stdout.strip()})
 report={'passed':True,'scope':'Actual production gate and pinned JAR scans; modeled effective ModernFix state, repeated cache reuse, active/unknown/tampered negative controls. Native gameplay is separate.','cases':reports};print(json.dumps(report))
 if a.report:a.report.parent.mkdir(parents=True,exist_ok=True);a.report.write_text(json.dumps(report,indent=2)+'\n',encoding='utf8')
if __name__=='__main__':main()
