"""Exercise the real gate; private grass bytes are optional and never published."""
from pathlib import Path
import argparse, hashlib, io, json, os, subprocess, tempfile, zipfile
I=Path(__file__).resolve().parents[1];R=I.parents[1]
PIN='91006e8220e65572d7e9e1e54b6058526c4adf908faedc750c9f19f13623df8c'
API='net/irisshaders/iris/api/v0/IrisApi.class'

def main():
    p=argparse.ArgumentParser();p.add_argument('--grass',type=Path);p.add_argument('--report',type=Path);a=p.parse_args();reports=[]
    with tempfile.TemporaryDirectory(prefix='mvh-grass-gl-') as tmp:
        root=Path(tmp);classes=root/'classes';sources=[]
        def source(name,text):
            f=root/name;f.parent.mkdir(parents=True,exist_ok=True);f.write_text(text);sources.append(str(f))
        for name in ['UniversalRendererGate.java','InactiveModernFixGlAudit.java','InactiveGrassComputeGlAudit.java']:
            f=root/name;f.write_bytes((I/'overlay/forge/src/main/java/net/vulkanmod/compat'/name).read_bytes());sources.append(str(f))
        source('FMLPaths.java','package net.minecraftforge.fml.loading;public enum FMLPaths {GAMEDIR;public java.nio.file.Path get(){return java.nio.file.Path.of(System.getProperty("user.dir"));}}')
        source('Probe.java','import net.vulkanmod.compat.UniversalRendererGate;public class Probe{public static void main(String[]a){boolean expected=Boolean.parseBoolean(a[0]);boolean actual=UniversalRendererGate.vulkanRendererEnabled();System.out.println("REASON="+UniversalRendererGate.reason());if(actual!=expected)throw new AssertionError("gate="+actual+" expected="+expected);}}')
        source('GrassPolicy.java','package net.vulkanmod.compat;public class GrassPolicy{public static void main(String[]a){for(var state:InactiveGrassComputeGlAudit.State.values()){boolean expected=state==InactiveGrassComputeGlAudit.State.ABSENT;if(InactiveGrassComputeGlAudit.allows(InactiveGrassComputeGlAudit.PIN,InactiveGrassComputeGlAudit.CLASS,state)!=expected)throw new AssertionError("provider state");for(String digest:new String[]{null,"bad"})if(InactiveGrassComputeGlAudit.allows(digest,InactiveGrassComputeGlAudit.CLASS,state))throw new AssertionError("hash");if(InactiveGrassComputeGlAudit.allows(InactiveGrassComputeGlAudit.PIN,"other/GrassComputeAnimator.class",state))throw new AssertionError("class");}System.out.println("EXACT_GRASS_PIN_CLASS_ABSENT_PROVIDER_NEGATIVE_CONTROLS_PASS");}}')
        source('com/leonardoinc22/shortgrass/client/render/GrassComputeAnimator.java','package com.leonardoinc22.shortgrass.client.render;public class GrassComputeAnimator{public static void compute(){org.lwjgl.opengl.GL43C.glDispatchCompute(1,1,1);}}')
        source('org/lwjgl/opengl/GL43C.java','package org.lwjgl.opengl;public class GL43C{public static void glDispatchCompute(int x,int y,int z){throw new AssertionError("GL fixture must never execute");}}')
        subprocess.run(['javac','--release','17','-d',str(classes),*sources],check=True)
        contract=classes/'assets/vulkanmod/compat/harimt_supported_gl_methods.properties';contract.parent.mkdir(parents=True);contract.write_bytes((R/'source/forge/src/main/resources/assets/vulkanmod/compat'/contract.name).read_bytes())
        subprocess.run(['java','-ea','-cp',str(classes),'net.vulkanmod.compat.GrassPolicy'],check=True)
        game=root/'game';mods=game/'mods';mods.mkdir(parents=True)
        def check(title,expected,classpath=classes):
            r=subprocess.run(['java','-ea','-cp',str(classpath),'Probe',str(expected).lower()],cwd=game,capture_output=True,text=True,check=True,timeout=30)
            reports.append({'case':title,'expected_vulkan':expected,'output':r.stdout.strip()})
        target=mods/'grass.jar'
        with zipfile.ZipFile(target,'w') as z:z.write(classes/'com/leonardoinc22/shortgrass/client/render/GrassComputeAnimator.class','com/leonardoinc22/shortgrass/client/render/GrassComputeAnimator.class')
        check('unknown-build-with-compute-call',False)
        target.unlink()
        if a.grass:
            data=a.grass.read_bytes();assert hashlib.sha256(data).hexdigest()==PIN
            target.write_bytes(data);check('pinned-grass-no-provider',True)
            api_classes=root/'provider';f=api_classes/API;f.parent.mkdir(parents=True);f.write_bytes(b'presence-only-resource')
            check('classpath-provider-present',False,str(classes)+os.pathsep+str(api_classes))
            provider=mods/'shader-provider.jar'
            with zipfile.ZipFile(provider,'w') as z:z.writestr(API,b'presence-only-resource')
            check('direct-provider-present',False)
            provider.unlink();check('provider-removed-cache-invalidated',True)
            nested=io.BytesIO()
            with zipfile.ZipFile(nested,'w') as z:z.writestr(API,b'presence-only-resource')
            with zipfile.ZipFile(provider,'w') as z:z.writestr('META-INF/jarjar/provider.jar',nested.getvalue())
            check('nested-provider-present',False)
            provider.unlink();check('nested-provider-removed',True)
            before=target.stat();changed=bytearray(data);central=changed.index(b'PK\x01\x02');changed[central+12]^=1;target.write_bytes(changed);os.utime(target,ns=(before.st_atime_ns,before.st_mtime_ns))
            check('modified-grass-same-size-time',False)
            target.unlink()
            with zipfile.ZipFile(provider,'w') as z:z.writestr('META-INF/jarjar/grass.jar',data)
            check('nested-grass-no-top-level-exemption',False)
    report={'passed':True,'actual_private_grass_checked':bool(a.grass),'scope':'Production gate, exact class/hash/provider controls and unknown compute fixture. Optional pinned real JAR: absent/direct/nested/classpath shader API, cache changes, same-size/time modified input and nested-copy rejection. No renderer features, raw game bytecode or private assets published. Native visual/performance checks separate.','cases':reports}
    print(json.dumps(report))
    if a.report:a.report.parent.mkdir(parents=True,exist_ok=True);a.report.write_text(json.dumps(report,indent=2)+'\n')

if __name__=='__main__':main()
