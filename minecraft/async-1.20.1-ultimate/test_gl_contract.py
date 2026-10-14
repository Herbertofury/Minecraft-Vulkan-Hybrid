#!/usr/bin/env python3
"""Run the real renderer selector with actual class-file method-reference fixtures."""
import argparse, importlib.util, json, shutil, subprocess, tempfile, zipfile
from pathlib import Path

def main():
    p=argparse.ArgumentParser();p.add_argument('source',type=Path);p.add_argument('--report',type=Path);args=p.parse_args()
    generated=args.source/'forge/src/main/resources/assets/vulkanmod/compat'
    audit=json.loads((generated/'harimt_gl_translation_audit.json').read_text())
    accepted={(m['owner'],m['signature']) for m in audit['accepted']}
    assert ('GL11C','glClear(I)V') in accepted
    for fake in [('GL43C','glDispatchCompute(III)V'),('GL11C','glGetError()I'),('GL20C','glCompileShader(I)V')]:
        assert fake not in accepted, 'placeholder advertised as a translation: '+str(fake)
    assert all(m['name'] not in {'glCopyImageSubData','glDispatchCompute','glDrawArraysIndirect'} for m in audit['accepted'])
    compiler=['javac'] if shutil.which('javac') else ['java','com.sun.tools.javac.Main']
    reports=[]
    with tempfile.TemporaryDirectory(prefix='hari-gl-contract-') as tmp:
        root=Path(tmp);classes=root/'classes';classes.mkdir();game=root/'game';game.mkdir();mods=game/'mods';mods.mkdir()
        gate=root/'UniversalRendererGate.java';gate.write_bytes((args.source/'forge/src/main/java/net/vulkanmod/compat/UniversalRendererGate.java').read_bytes())
        fml=root/'FMLPaths.java';fml.write_text('package net.minecraftforge.fml.loading; public enum FMLPaths {GAMEDIR;public java.nio.file.Path get(){return java.nio.file.Path.of(System.getProperty("fixture.gameDir"));}}')
        probe=root/'Probe.java';probe.write_text('import net.vulkanmod.compat.UniversalRendererGate;public class Probe{public static void main(String[]a){System.out.println("RESULT="+UniversalRendererGate.vulkanRendererEnabled());System.out.println("REASON="+UniversalRendererGate.reason());}}')
        subprocess.run(compiler+['--release','17','-d',str(classes),str(gate),str(fml),str(probe)],check=True)
        contract=classes/'assets/vulkanmod/compat/harimt_supported_gl_methods.properties';contract.parent.mkdir(parents=True);contract.write_bytes((generated/contract.name).read_bytes())
        cases=[
            ('translated clear','GL11C','void glClear(int x){}','glClear(16384)',True,'glClear(I)V'),
            ('untranslated compute','GL43C','void glDispatchCompute(int x,int y,int z){}','glDispatchCompute(1,1,1)',False,'glDispatchCompute(III)V'),
            ('same name wrong overload','GL11C','void glClear(long x){}','glClear(1L)',False,'glClear(J)V'),
            ('fake error query','GL11C','int glGetError(){return 0;}','glGetError()',False,'glGetError()I'),
            ('unknown owner','GL46C','void glClear(int x){}','glClear(16384)',False,'glClear(I)V')]
        for label,owner,definition,call,expected,signature in cases:
            fixture=root/'fixture';fixture.mkdir(exist_ok=True);gl=fixture/(owner+'.java');gl.write_text('package org.lwjgl.opengl;public class '+owner+'{public static '+definition+'}')
            caller=fixture/'ExternalMod.java';caller.write_text('public class ExternalMod{public static void render(){org.lwjgl.opengl.'+owner+'.'+call+';}}')
            out=fixture/'classes';subprocess.run(compiler+['--release','17','-d',str(out),str(gl),str(caller)],check=True)
            jar=mods/'external-fixture.jar'
            with zipfile.ZipFile(jar,'w') as z:z.write(out/'ExternalMod.class','ExternalMod.class');z.writestr('META-INF/mods.toml','[[mods]]\nmodId="external_fixture"\n')
            command=['java','-cp',str(classes),'-Dfixture.gameDir='+str(game),'-Dharimt.vulkan.compatCache=false','Probe']
            result=subprocess.run(command,check=True,capture_output=True,text=True)
            assert 'RESULT='+str(expected).lower() in result.stdout,(label,result.stdout)
            if not expected:assert signature in result.stdout,(label,'exact overload omitted',result.stdout)
            reports.append(dict(case=label,expected_vulkan=expected,passed=True,method=owner+'#'+signature))
            if label=='same name wrong overload':
                # A v9 decision cannot survive the contract/schema correction.
                cached=game/'config/harimt-vulkan-compat.properties'
                cached.parent.mkdir(exist_ok=True);cached.write_text('signature=old-v9\nenabled=true\nreason=old placeholder accepted\n')
                cached_result=subprocess.run([c.replace('compatCache=false','compatCache=true') for c in command],check=True,capture_output=True,text=True)
                assert 'RESULT=false' in cached_result.stdout,'old cache admitted unknown overload'
                reports.append(dict(case='old compatibility cache invalidated',passed=True))
            shutil.rmtree(fixture)
    report=dict(passed=True,cases=reports,accepted_overloads=audit['accepted_overloads'],rejected_overloads=audit['rejected_overloads'],
                scope='Actual production selector and real JVM method-reference classes; empty/compute/shader placeholders and wrong overloads rejected before Vulkan startup.')
    print(json.dumps(report))
    if args.report:args.report.parent.mkdir(parents=True,exist_ok=True);args.report.write_text(json.dumps(report,indent=2)+'\n')

if __name__=='__main__':main()
