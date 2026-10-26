#!/usr/bin/env python3
"""Actual original Rubidium cache bytecode/fastutil and production logo lifetime code."""
import argparse, hashlib, json, subprocess, tempfile, urllib.request, zipfile
from pathlib import Path

STUBS = {
'net/minecraft/core/SectionPos.java': '''package net.minecraft.core; public class SectionPos { public static long m_123209_(int x,int y,int z){return ((long)x<<42)^((long)y<<21)^z;} public static int m_123223_(int y){return y*16;} public static SectionPos m_123173_(int x,int y,int z){return new SectionPos();}}''',
'net/minecraft/world/level/Level.java': '''package net.minecraft.world.level;import net.minecraft.world.level.chunk.*;public class Level {public LevelChunk m_6325_(int x,int z){return new LevelChunk();} public boolean m_151562_(int y){return false;} public int m_151566_(int y){return 0;}}''',
'net/minecraft/world/level/chunk/LevelChunk.java': '''package net.minecraft.world.level.chunk;public class LevelChunk{public LevelChunkSection[] m_7103_(){return new LevelChunkSection[]{new LevelChunkSection()};}}''',
'net/minecraft/world/level/chunk/LevelChunkSection.java': 'package net.minecraft.world.level.chunk;public class LevelChunkSection{}',
'me/jellysquid/mods/sodium/client/world/cloned/ClonedChunkSection.java': '''package me.jellysquid.mods.sodium.client.world.cloned;import net.minecraft.world.level.Level;import net.minecraft.world.level.chunk.*;import net.minecraft.core.SectionPos;public class ClonedChunkSection {private long used;public ClonedChunkSection(Level w,LevelChunk c,LevelChunkSection s,SectionPos p){}public long getLastUsedTimestamp(){return used;}public void setLastUsedTimestamp(long t){used=t;}}''',
}

CACHE_PROBE = r'''
import com.axalotl.async.forge.client.RubidiumCacheLock;
import org.objectweb.asm.*;import org.objectweb.asm.tree.*;
import java.nio.file.*;import java.lang.reflect.*;import java.util.*;
import java.util.concurrent.*;import java.util.concurrent.atomic.*;
import net.minecraft.world.level.Level;
public class CacheProbe {
 static final String TARGET="me.jellysquid.mods.sodium.client.world.cloned.ClonedChunkSectionCache";
 static class Loader extends ClassLoader{byte[] bytes;Loader(byte[] b){super(CacheProbe.class.getClassLoader());bytes=b;}
  protected Class<?> loadClass(String name,boolean resolve)throws ClassNotFoundException{if(!name.equals(TARGET))return super.loadClass(name,resolve);synchronized(getClassLoadingLock(name)){Class<?> c=findLoadedClass(name);if(c==null)c=defineClass(name,bytes,0,bytes.length);if(resolve)resolveClass(c);return c;}}}
 static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
 public static void main(String[] args)throws Exception{
  byte[] original=Files.readAllBytes(Path.of(args[0]));ClassReader reader=new ClassReader(original);ClassNode node=new ClassNode();reader.accept(node,0);
  MethodNode cleanup=node.methods.stream().filter(m->m.name.equals("cleanup")&&m.desc.equals("()V")).findFirst().orElseThrow();
  int instructions=cleanup.instructions.size();check((cleanup.access&Opcodes.ACC_SYNCHRONIZED)==0,"original negative control already locked");
  boolean positive=args[1].equals("positive");if(positive)RubidiumCacheLock.apply(node);
  check(cleanup.instructions.size()==instructions,"original cleanup body altered");
  ClassWriter writer=new ClassWriter(reader,0);node.accept(writer);Class<?> type=new Loader(writer.toByteArray()).loadClass(TARGET);
  Object cache=type.getConstructor(Level.class).newInstance(new Level());Method clean=type.getMethod("cleanup"),acquire=type.getMethod("acquire",int.class,int.class,int.class),invalidate=type.getMethod("invalidate",int.class,int.class,int.class);
  AtomicBoolean entered=new AtomicBoolean();AtomicReference<Throwable> failure=new AtomicReference<>();
  Thread thread=new Thread(()->{try{clean.invoke(cache);entered.set(true);}catch(Throwable e){failure.set(e);}});
  synchronized(cache){thread.start();long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(2);while(!entered.get()&&thread.getState()!=Thread.State.BLOCKED&&System.nanoTime()<end)Thread.sleep(1);
   check(positive?thread.getState()==Thread.State.BLOCKED&&!entered.get():entered.get(),"cleanup does not obey expected mutation monitor");}
  thread.join(2000);check(!thread.isAlive()&&failure.get()==null,"cleanup did not complete after lock release");
  if(!positive){System.out.println("OLD_CLEANUP_IGNORED_MUTATION_MONITOR");return;}
  AtomicInteger completed=new AtomicInteger();List<Thread> workers=new ArrayList<>();
  for(int lane=0;lane<8;lane++){final int index=lane;Thread worker=new Thread(()->{try{for(int i=0;i<5000;i++){Object section=acquire.invoke(cache,(i+index)%47,0,i%43);check(section!=null,"null section after concurrent cache cleanup");if(i%3==0)invalidate.invoke(cache,(i+index)%47,0,i%43);if(i%7==0)clean.invoke(cache);completed.incrementAndGet();}}catch(Throwable e){failure.compareAndSet(null,e);}});workers.add(worker);worker.start();}
  for(Thread worker:workers){worker.join(15000);check(!worker.isAlive(),"cache workers stalled");}
  check(failure.get()==null,"cache mutation failure: "+failure.get());check(completed.get()==40000,"lost original cache calls");
  System.out.println("ORIGINAL_RUBIDIUM_CACHE_40000_CALLS_PASSED");
 }
}
'''

LOGO_STUBS = {
'net/minecraft/resources/ResourceLocation.java': 'package net.minecraft.resources;public record ResourceLocation(String path){}',
'net/minecraft/server/packs/PackType.java': 'package net.minecraft.server.packs;public enum PackType{CLIENT_RESOURCES}',
'net/minecraft/server/packs/resources/ResourceManager.java': '''package net.minecraft.server.packs.resources;import java.io.*;import java.util.*;import net.minecraft.resources.ResourceLocation;public class ResourceManager{public boolean custom=true,fail;public Optional<Resource> getResource(ResourceLocation id){return custom?Optional.of(new Resource(fail)):Optional.empty();}public static class Resource{boolean fail;Resource(boolean f){fail=f;}public InputStream open()throws IOException{if(fail)throw new IOException("original resource failure");return new ByteArrayInputStream(new byte[]{3,7,11});}}}''',
'net/minecraft/server/packs/VanillaPackResources.java': '''package net.minecraft.server.packs;import java.io.*;import java.util.function.Supplier;import net.minecraft.resources.ResourceLocation;public class VanillaPackResources{public Supplier<InputStream> getResource(PackType t,ResourceLocation id){return ()->new ByteArrayInputStream(new byte[]{1,2,4});}}''',
'com/mojang/blaze3d/platform/NativeImage.java': '''package com.mojang.blaze3d.platform;import java.io.*;public record NativeImage(byte[] bytes){public static NativeImage read(InputStream input)throws IOException{return new NativeImage(input.readAllBytes());}}''',
'net/minecraft/client/resources/metadata/texture/TextureMetadataSection.java': 'package net.minecraft.client.resources.metadata.texture;public record TextureMetadataSection(boolean blur,boolean clamp){}',
'net/minecraft/client/renderer/texture/SimpleTexture.java': '''package net.minecraft.client.renderer.texture;import java.io.*;import java.util.*;import net.minecraft.resources.*;import net.minecraft.server.packs.resources.*;import net.minecraft.client.resources.metadata.texture.*;import com.mojang.blaze3d.platform.NativeImage;public class SimpleTexture{public static final Set<Integer> live=new HashSet<>();public static int next=1,releases;private int id;public byte[] pixels;public SimpleTexture(ResourceLocation location){}public void load(ResourceManager resources)throws IOException{TextureImage image=getTextureImage(resources);if(image.failure!=null)throw image.failure;if(!image.meta.blur()||!image.meta.clamp())throw new AssertionError("original logo filtering changed");pixels=image.image.bytes();id=next++;live.add(id);}protected TextureImage getTextureImage(ResourceManager manager){throw new AssertionError();}public int getId(){return id;}public void releaseId(){if(live.remove(id))releases++;}public void close(){}public static class TextureImage{public NativeImage image;public TextureMetadataSection meta;public IOException failure;public TextureImage(TextureMetadataSection m,NativeImage i){meta=m;image=i;}public TextureImage(IOException e){failure=e;}}}''',
'net/minecraft/client/Minecraft.java': '''package net.minecraft.client;import net.minecraft.server.packs.*;import net.minecraft.server.packs.resources.*;public class Minecraft{public static Minecraft instance;public Object overlay;public ResourceManager resources=new ResourceManager();public Minecraft(){instance=this;}public static Minecraft getInstance(){return instance;}public ResourceManager getResourceManager(){return resources;}public VanillaPackResources getVanillaPackResources(){return new VanillaPackResources();}public Object getOverlay(){return overlay;}}''',
'net/minecraftforge/client/loading/ForgeLoadingOverlay.java': 'package net.minecraftforge.client.loading;public class ForgeLoadingOverlay{}',
'net/minecraftforge/fml/earlydisplay/DisplayWindow.java': '''package net.minecraftforge.fml.earlydisplay;import net.minecraft.client.renderer.texture.SimpleTexture;public class DisplayWindow{public int logo;public void addMojangTexture(int id){logo=id;}public void draw(){if(!SimpleTexture.live.contains(logo))throw new IllegalStateException("GL_INVALID_OPERATION glBindTexture(non-gen name)");}}''',
'org/spongepowered/asm/mixin/injection/callback/CallbackInfo.java': 'package org.spongepowered.asm.mixin.injection.callback;public class CallbackInfo{}',
}
for name in ['Final','Shadow','Unique']:
    LOGO_STUBS[f'org/spongepowered/asm/mixin/{name}.java']=f'package org.spongepowered.asm.mixin;public @interface {name} {{}}'
LOGO_STUBS['org/spongepowered/asm/mixin/Mixin.java']='package org.spongepowered.asm.mixin;public @interface Mixin{Class<?>[] value();boolean remap();}'
LOGO_STUBS['org/spongepowered/asm/mixin/injection/At.java']='package org.spongepowered.asm.mixin.injection;public @interface At{String value();String target() default "";}'
for name in ['Inject','Redirect']:
    LOGO_STUBS[f'org/spongepowered/asm/mixin/injection/{name}.java']=f'package org.spongepowered.asm.mixin.injection;public @interface {name}{{String[] method();At at();boolean remap();}}'

LOGO_PROBE = r'''
import com.axalotl.async.forge.client.OwnedLoadingLogo;import com.axalotl.async.forge.mixin.client.opengl.ForgeLoadingLogoMixin;
import net.minecraft.client.Minecraft;import net.minecraft.client.renderer.texture.SimpleTexture;import net.minecraftforge.fml.earlydisplay.DisplayWindow;
import java.lang.reflect.*;import java.util.*;
public class LogoProbe extends ForgeLoadingLogoMixin{
 static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
 public static void main(String[] args)throws Exception{
  Minecraft mc=new Minecraft();OwnedLoadingLogo shared=new OwnedLoadingLogo(mc);DisplayWindow old=new DisplayWindow();old.addMojangTexture(shared.getId());shared.releaseId();
  try{old.draw();throw new AssertionError("old captured released logo did not fail");}catch(IllegalStateException expected){check(expected.getMessage().contains("non-gen name"),"wrong negative control");}
  LogoProbe overlay=new LogoProbe();mc.overlay=overlay;Field minecraft=ForgeLoadingLogoMixin.class.getDeclaredField("minecraft");minecraft.setAccessible(true);minecraft.set(overlay,mc);
  Method own=ForgeLoadingLogoMixin.class.getDeclaredMethod("harimt$ownLogo",DisplayWindow.class,int.class);own.setAccessible(true);DisplayWindow window=new DisplayWindow();own.invoke(overlay,window,shared.getId());
  Method release=ForgeLoadingLogoMixin.class.getDeclaredMethod("harimt$releaseLogo",org.spongepowered.asm.mixin.injection.callback.CallbackInfo.class);release.setAccessible(true);
  for(int i=0;i<50;i++){OwnedLoadingLogo reloaded=new OwnedLoadingLogo(mc);reloaded.releaseId();release.invoke(overlay,new org.spongepowered.asm.mixin.injection.callback.CallbackInfo());window.draw();}
  int count=SimpleTexture.releases;mc.overlay=null;release.invoke(overlay,new org.spongepowered.asm.mixin.injection.callback.CallbackInfo());release.invoke(overlay,new org.spongepowered.asm.mixin.injection.callback.CallbackInfo());check(SimpleTexture.releases==count+1,"logo release lost or duplicated");check(!SimpleTexture.live.contains(window.logo),"overlay logo leaked after final draw");
  mc.resources.custom=false;OwnedLoadingLogo vanilla=new OwnedLoadingLogo(mc);check(Arrays.equals(vanilla.pixels,new byte[]{1,2,4}),"vanilla logo changed");vanilla.releaseId();
  mc.resources.custom=true;OwnedLoadingLogo custom=new OwnedLoadingLogo(mc);check(Arrays.equals(custom.pixels,new byte[]{3,7,11}),"OptiFine/resource-pack logo changed");custom.releaseId();
  mc.resources.fail=true;try{new OwnedLoadingLogo(mc);throw new AssertionError("resource failure hidden");}catch(java.io.UncheckedIOException expected){check(expected.getCause().getMessage().equals("original resource failure"),"original resource failure changed");}
  check(SimpleTexture.live.isEmpty(),"owned texture leaked");System.out.println("ORIGINAL_LOGO_PIXELS_RELOAD_LIFETIME_AND_FINAL_RELEASE_PASSED");
 }
}
'''

def run(files, main, classpath='', arguments=()):
    with tempfile.TemporaryDirectory(prefix='hari-client-lifetime-') as temp:
        root=Path(temp)
        for name, body in files.items():
            path=root/name;path.parent.mkdir(parents=True,exist_ok=True)
            path.write_bytes(body) if isinstance(body,bytes) else path.write_text(body)
        classes=root/'classes';cp=str(classes)+(':'+classpath if classpath else '')
        subprocess.run(['java','--module','jdk.compiler/com.sun.tools.javac.Main','--release','17','-cp',cp,'-d',str(classes)]+[str(p) for p in root.rglob('*.java')],check=True)
        expanded=[str(root/x) if x=='original-cache.class' else x for x in arguments]
        return subprocess.check_output(['java','-ea','-cp',cp,main,*expanded],text=True,stderr=subprocess.PIPE,timeout=60)

def download(url,path,digest=None):
    if not path.is_file():
        with urllib.request.urlopen(url,timeout=90) as response:path.write_bytes(response.read())
    if digest:assert hashlib.sha256(path.read_bytes()).hexdigest()==digest,path
    return path

def main():
    parser=argparse.ArgumentParser();parser.add_argument('source',type=Path);parser.add_argument('--report',type=Path,required=True);parser.add_argument('--cache',type=Path,required=True);args=parser.parse_args();args.cache.mkdir(parents=True,exist_ok=True)
    rubidium=download('https://cdn.modrinth.com/data/4ZqxOvjD/versions/VKligEsm/rubidium-mc1.20.1-0.7.1.jar',args.cache/'rubidium-mc1.20.1-0.7.1.jar','f281204c19d7ef1eb8ca6181146ac828e87fcfdde7053facaabf71d9ded35c85')
    asm=download('https://repo.maven.apache.org/maven2/org/ow2/asm/asm/9.5/asm-9.5.jar',args.cache/'asm.jar');tree=download('https://repo.maven.apache.org/maven2/org/ow2/asm/asm-tree/9.5/asm-tree-9.5.jar',args.cache/'asm-tree.jar');fastutil=download('https://libraries.minecraft.net/it/unimi/dsi/fastutil/8.5.9/fastutil-8.5.9.jar',args.cache/'fastutil.jar')
    with zipfile.ZipFile(rubidium) as jar:original=jar.read('me/jellysquid/mods/sodium/client/world/cloned/ClonedChunkSectionCache.class')
    java=args.source/'forge/src/main/java';cache_files=dict(STUBS);cache_files.update({'CacheProbe.java':CACHE_PROBE,'original-cache.class':original,'com/axalotl/async/forge/client/RubidiumCacheLock.java':(java/'com/axalotl/async/forge/client/RubidiumCacheLock.java').read_text()})
    cp=':'.join(map(str,[asm.resolve(),tree.resolve(),fastutil.resolve()]))
    assert 'OLD_CLEANUP_IGNORED_MUTATION_MONITOR' in run(cache_files,'CacheProbe',cp,('original-cache.class','negative'))
    assert 'ORIGINAL_RUBIDIUM_CACHE_40000_CALLS_PASSED' in run(cache_files,'CacheProbe',cp,('original-cache.class','positive'))
    logo_files=dict(LOGO_STUBS);logo_files['LogoProbe.java']=LOGO_PROBE
    for name in ['com/axalotl/async/forge/client/OwnedLoadingLogo.java','com/axalotl/async/forge/mixin/client/opengl/ForgeLoadingLogoMixin.java']:logo_files[name]=(java/name).read_text()
    assert 'ORIGINAL_LOGO_PIXELS_RELOAD_LIFETIME_AND_FINAL_RELEASE_PASSED' in run(logo_files,'LogoProbe')
    report={'passed':True,'original_rubidium_sha256':hashlib.sha256(rubidium.read_bytes()).hexdigest(),'original_cleanup_bytecode_retained':True,'old_cleanup_monitor_negative_control':True,'concurrent_original_cache_calls':40000,'captured_deleted_logo_negative_control':True,'overlay_survives_texture_manager_reloads':50,'original_custom_and_vanilla_logo_pixels':True,'original_filtering_and_resource_errors':True,'final_texture_release_exact_once':True,'scope':'Original Rubidium cleanup/acquire/invalidate bytecode and real fastutil with Minecraft section doubles; production logo/mixin Java with resource/GL-lifetime doubles. Native packaged client acceptance remains required.'}
    args.report.parent.mkdir(parents=True,exist_ok=True);args.report.write_text(json.dumps(report,indent=2)+'\n');print(json.dumps(report,indent=2))

if __name__=='__main__':main()
