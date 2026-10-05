"""Actual production selector, pinned first-party JAR, platform and changed-input controls."""
from pathlib import Path
import argparse,hashlib,json,os,subprocess,tempfile,urllib.request
I=Path(__file__).resolve().parents[1];R=I.parents[1]
PIN='6cc7e1bfad6a2e82983a41535accf919a1027ca7087f1e6fcc955df22f7d91e1'
URL='https://cdn.modrinth.com/data/p8RJPJIC/versions/ieNxE3h5/Ixeris-4.6.8%2B1.20.1-forge.jar'
p=argparse.ArgumentParser();p.add_argument('--ixeris',type=Path);p.add_argument('--report',type=Path);a=p.parse_args();cases=[]
with tempfile.TemporaryDirectory(prefix='mvh-ixeris-platform-') as tmp:
    root=Path(tmp);classes=root/'classes';sources=[]
    def source(name,text):
        f=root/name;f.parent.mkdir(parents=True,exist_ok=True);f.write_text(text,encoding='utf8');sources.append(str(f))
    for name in ['UniversalRendererGate.java','InactiveModernFixGlAudit.java','InactiveGrassComputeGlAudit.java','InactiveIxerisMacOsGlAudit.java']:
        f=root/name;f.write_bytes((I/'overlay/forge/src/main/java/net/vulkanmod/compat'/name).read_bytes());sources.append(str(f))
    source('FMLPaths.java','package net.minecraftforge.fml.loading;public enum FMLPaths{GAMEDIR;public java.nio.file.Path get(){return java.nio.file.Path.of(System.getProperty("user.dir"));}}')
    source('Platform.java','package org.lwjgl.system;public enum Platform{WINDOWS,MACOSX,LINUX;public static Platform get(){return valueOf(System.getProperty("fixture.platform","WINDOWS"));}}')
    source('Probe.java','import net.vulkanmod.compat.UniversalRendererGate;public class Probe{public static void main(String[]a){boolean expected=Boolean.parseBoolean(a[0]);boolean actual=UniversalRendererGate.vulkanRendererEnabled();if(actual!=expected)throw new AssertionError("gate="+actual+" expected="+expected);System.out.println(UniversalRendererGate.reason());}}')
    source('Policy.java','''package net.vulkanmod.compat;public class Policy{public static void main(String[]a){for(var state:InactiveIxerisMacOsGlAudit.State.values())for(String entry:InactiveIxerisMacOsGlAudit.CLASSES){if(InactiveIxerisMacOsGlAudit.allows(InactiveIxerisMacOsGlAudit.PIN,entry,state)!=(state==InactiveIxerisMacOsGlAudit.State.WINDOWS))throw new AssertionError("platform");if(InactiveIxerisMacOsGlAudit.allows("changed",entry,state)||InactiveIxerisMacOsGlAudit.allows(null,entry,state)||InactiveIxerisMacOsGlAudit.allows(InactiveIxerisMacOsGlAudit.PIN,"other/Renderer.class",state))throw new AssertionError("pin/class");}System.out.println("EXACT_PIN_CLASS_PLATFORM_CONTROLS_PASS");}}''')
    subprocess.run(['javac','--release','17','-d',str(classes),*sources],check=True,capture_output=True)
    contract=classes/'assets/vulkanmod/compat/harimt_supported_gl_methods.properties';contract.parent.mkdir(parents=True);contract.write_bytes((R/'source/forge/src/main/resources/assets/vulkanmod/compat'/contract.name).read_bytes())
    subprocess.run(['java','-ea','-cp',str(classes),'net.vulkanmod.compat.Policy'],check=True)
    data=a.ixeris.read_bytes() if a.ixeris else urllib.request.urlopen(URL,timeout=45).read();assert hashlib.sha256(data).hexdigest()==PIN
    game=root/'game';mods=game/'mods';mods.mkdir(parents=True);target=mods/'Ixeris-4.6.8+1.20.1-forge.jar';target.write_bytes(data)
    for platform,expected in [('WINDOWS',True),('MACOSX',False),('LINUX',False),('UNRECOGNIZED',False),('WINDOWS',True)]:
        r=subprocess.run(['java','-Dfixture.platform='+platform,'-cp',str(classes),'Probe',str(expected).lower()],cwd=game,capture_output=True,text=True,check=True,timeout=30)
        cases.append({'case':platform,'expected_native':expected,'reason':r.stdout.strip()})
    before=target.stat();changed=bytearray(data);central=changed.index(b'PK\x01\x02');changed[central+12]^=1;target.write_bytes(changed);os.utime(target,ns=(before.st_atime_ns,before.st_mtime_ns));assert target.stat().st_size==before.st_size and target.stat().st_mtime_ns==before.st_mtime_ns
    r=subprocess.run(['java','-cp',str(classes),'Probe','false'],cwd=game,capture_output=True,text=True,check=True,timeout=30)
    cases.append({'case':'modified-whole-jar-same-size-time','expected_native':False,'reason':r.stdout.strip()})
report={'passed':True,'pinned_jar_sha256':PIN,'scope':'Actual production gate scans the published first-party JAR; modeled immutable LWJGL platform. Changed/unknown/other-platform controls retain ordinary fallback, including cached Windows-to-Mac transitions. Upstream mod code is not executed. Native game/thread/input benefit remains separate.','cases':cases}
if a.report:a.report.parent.mkdir(parents=True,exist_ok=True);a.report.write_text(json.dumps(report,indent=2)+'\n',encoding='utf8')
print('PINNED_INACTIVE_IXERIS_PLATFORM_PRODUCTION_GATE_PASS',len(cases))
