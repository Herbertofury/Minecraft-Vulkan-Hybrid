#!/usr/bin/env python3
"""Execute the actual channel-clear mixin with a deterministic reload race control."""
import argparse, hashlib, json, shutil, subprocess, tempfile
from pathlib import Path

STUBS = {
'org/spongepowered/asm/mixin/Mixin.java': 'package org.spongepowered.asm.mixin; public @interface Mixin {Class<?>[] value();}',
'org/spongepowered/asm/mixin/Shadow.java': 'package org.spongepowered.asm.mixin; public @interface Shadow {}',
'org/spongepowered/asm/mixin/Final.java': 'package org.spongepowered.asm.mixin; public @interface Final {}',
'com/llamalad7/mixinextras/injector/wrapmethod/WrapMethod.java': 'package com.llamalad7.mixinextras.injector.wrapmethod; public @interface WrapMethod {String[] method();}',
'com/llamalad7/mixinextras/injector/wrapoperation/Operation.java': 'package com.llamalad7.mixinextras.injector.wrapoperation; public interface Operation<T>{T call(Object... args);}',
'net/minecraft/client/sounds/ChannelAccess.java': 'package net.minecraft.client.sounds; public class ChannelAccess {}',
'net/minecraft/client/sounds/SoundEngineExecutor.java': '''package net.minecraft.client.sounds;
import java.util.concurrent.*;
public class SoundEngineExecutor implements Executor, AutoCloseable {
 private volatile Thread owner;
 private final ExecutorService pool=Executors.newSingleThreadExecutor(r->{Thread t=new Thread(r,"sound-owner");owner=t;return t;});
 public void execute(Runnable job){pool.execute(job);}
 public boolean isSameThread(){return Thread.currentThread()==owner;}
 public void close() throws Exception {pool.shutdown();if(!pool.awaitTermination(5,TimeUnit.SECONDS))throw new AssertionError("sound executor leaked");}
}'''
}
PROBE = r'''
import com.axalotl.async.forge.mixin.client.ChannelAccessMixin;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.client.sounds.SoundEngineExecutor;
import java.lang.reflect.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
public class AudioProbe {
 static final Method clear;static final Field executor;
 static {try {clear=ChannelAccessMixin.class.getDeclaredMethod("harimt$clearOnSoundThread",Operation.class);clear.setAccessible(true);executor=ChannelAccessMixin.class.getDeclaredField("executor");executor.setAccessible(true);}catch(Exception e){throw new AssertionError(e);}}
 static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
 static void await(CountDownLatch latch){try{check(latch.await(5,TimeUnit.SECONDS),"race test timeout");}catch(InterruptedException e){throw new AssertionError(e);}}
 static void call(ChannelAccessMixin mixin,Operation<Void> op){try{clear.invoke(mixin,op);}catch(InvocationTargetException e){AudioProbe.<RuntimeException>rethrow(e.getCause());}catch(ReflectiveOperationException e){throw new AssertionError(e);}}
 static <T extends Throwable> void rethrow(Throwable failure)throws T{throw (T)failure;}
 static ChannelAccessMixin instance(java.util.concurrent.Executor sound)throws Exception{ChannelAccessMixin mixin=new ChannelAccessMixin(){};executor.set(mixin,sound);return mixin;}
 public static void main(String[] args)throws Exception {
  // Vanilla queues stop, releases the channel on another thread, then destroys the context.
  // Hold the queued stop after its live-source check to make that exact race deterministic.
  AtomicBoolean live=new AtomicBoolean(true);AtomicInteger invalid=new AtomicInteger();
  try(SoundEngineExecutor sound=new SoundEngineExecutor()){
   CountDownLatch checked=new CountDownLatch(1),continueStop=new CountDownLatch(1),finished=new CountDownLatch(1);
   sound.execute(()->{boolean initialized=live.get();checked.countDown();await(continueStop);if(initialized&&!live.get())invalid.incrementAndGet();finished.countDown();});
   await(checked);live.set(false);continueStop.countDown();await(finished);
  }
  check(invalid.get()==1,"old invalid-source race was not reproduced");
  AtomicInteger stops=new AtomicInteger(),releases=new AtomicInteger();
  try(SoundEngineExecutor sound=new SoundEngineExecutor()){
   ChannelAccessMixin mixin=instance(sound);
   for(int i=0;i<1100;i++){
    live.set(true);CountDownLatch checked=new CountDownLatch(1),continueStop=new CountDownLatch(1);
    sound.execute(()->{boolean initialized=live.get();checked.countDown();await(continueStop);check(initialized&&live.get(),"source destroyed before queued stop");stops.incrementAndGet();});
    await(checked);
    AtomicReference<Throwable> failure=new AtomicReference<>();CountDownLatch queued=new CountDownLatch(1);
    // Observe the production mixin's submission rather than use a timing assumption.
    executor.set(mixin,(java.util.concurrent.Executor)(job->{sound.execute(job);queued.countDown();}));
    Thread reload=new Thread(()->{try{call(mixin,a->{check(sound.isSameThread(),"clear escaped sound owner");check(live.getAndSet(false),"double source release");releases.incrementAndGet();return null;});}catch(Throwable e){failure.set(e);}},"resource-reload");
    reload.start();await(queued);check(live.get(),"clear raced queued source operation");continueStop.countDown();reload.join(5000);check(!reload.isAlive(),"cleanup stalled");check(failure.get()==null,"cleanup failed: "+failure.get());check(!live.get(),"context cleanup could start before channel release");
   }
   executor.set(mixin,sound);CompletableFuture<Void> inline=new CompletableFuture<>();
   sound.execute(()->{try{call(mixin,a->{check(sound.isSameThread(),"inline clear escaped owner");return null;});inline.complete(null);}catch(Throwable e){inline.completeExceptionally(e);}});
   inline.get(5,TimeUnit.SECONDS);
   RuntimeException original=new RuntimeException("original audio cleanup failure");
   try{call(mixin,a->{throw original;});throw new AssertionError("failure swallowed");}catch(RuntimeException actual){check(actual==original,"failure identity changed");}
  }
  check(stops.get()==1100&&releases.get()==1100,"lost or duplicate audio operations");
  RejectedExecutionException rejection=new RejectedExecutionException("closed executor");
  try{call(instance(job->{throw rejection;}),a->null);throw new AssertionError("submission failure swallowed");}catch(RejectedExecutionException actual){check(actual==rejection,"submission failure identity changed");}
  System.out.println("{\"passed\":true,\"old_invalid_source_race_reproduced\":true,\"ordered_reload_cycles\":1100,\"live_stops\":1100,\"source_releases\":1100,\"same_thread_clear\":true,\"original_failure_identity\":true,\"submission_failure_identity\":true}");
 }
}
'''

def main():
    p=argparse.ArgumentParser();p.add_argument('source',type=Path);p.add_argument('--report',type=Path);args=p.parse_args()
    config=json.loads((args.source/'forge/src/main/resources/harimt.forge.mixins.json').read_text())
    assert 'client.ChannelAccessMixin' in config['client']
    source=args.source/'forge/src/main/java/com/axalotl/async/forge/mixin/client/ChannelAccessMixin.java'
    with tempfile.TemporaryDirectory(prefix='hari-audio-reload-') as tmp:
        root=Path(tmp)
        for name,body in STUBS.items():
            dest=root/name;dest.parent.mkdir(parents=True,exist_ok=True);dest.write_text(body)
        dest=root/'com/axalotl/async/forge/mixin/client/ChannelAccessMixin.java';dest.parent.mkdir(parents=True);dest.write_bytes(source.read_bytes())
        (root/'AudioProbe.java').write_text(PROBE)
        compiler=['javac'] if shutil.which('javac') else ['java','com.sun.tools.javac.Main']
        subprocess.run(compiler+['--release','17','-d',str(root/'classes')]+list(map(str,root.rglob('*.java'))),check=True)
        result=subprocess.run(['java','-cp',str(root/'classes'),'AudioProbe'],check=True,capture_output=True,text=True,timeout=45)
    report=json.loads(result.stdout);report['source_sha256']=hashlib.sha256(source.read_bytes()).hexdigest()
    report['scope']='Actual production mixin; real ordered executor and deterministic invalid-source negative control. Packaged OpenAL reload validation is separate.'
    print(result.stdout.strip())
    if args.report:args.report.parent.mkdir(parents=True,exist_ok=True);args.report.write_text(json.dumps(report,indent=2)+'\n')

if __name__=='__main__':main()
