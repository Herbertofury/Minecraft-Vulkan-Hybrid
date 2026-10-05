"""Actual MTS redirect helper, original wrong-owner negative control, and a real thread/barrier."""
from pathlib import Path
import argparse,shutil,subprocess,tempfile,json
I=Path(__file__).resolve().parents[1]
stubs={
'org/spongepowered/asm/mixin/Mixin.java':'package org.spongepowered.asm.mixin;public @interface Mixin {String[] targets();boolean remap();}',
'org/spongepowered/asm/mixin/Pseudo.java':'package org.spongepowered.asm.mixin;public @interface Pseudo {}',
'org/spongepowered/asm/mixin/injection/At.java':'package org.spongepowered.asm.mixin.injection;public @interface At {String value();String target();}',
'org/spongepowered/asm/mixin/injection/Coerce.java':'package org.spongepowered.asm.mixin.injection;public @interface Coerce {}',
'org/spongepowered/asm/mixin/injection/Redirect.java':'package org.spongepowered.asm.mixin.injection;public @interface Redirect {String method();At at();int require();int allow();boolean remap();}',
'net/minecraft/world/level/Level.java':'package net.minecraft.world.level;public class Level {}',
'net/minecraft/server/level/ServerLevel.java':'''package net.minecraft.server.level;import net.minecraft.world.level.Level;import java.util.concurrent.*;public class ServerLevel extends Level {public volatile Thread owner=Thread.currentThread();public final ConcurrentLinkedQueue<Runnable> afterBarrier=new ConcurrentLinkedQueue<>();public final Chunks chunks=new Chunks(this);public Chunks getChunkSource(){return chunks;}public Executor getServer(){return afterBarrier::add;}public static class Chunks implements com.axalotl.async.common.ChunkOwnerExecutor {final ServerLevel level;Chunks(ServerLevel l){level=l;}public boolean harimt$ownerIsCurrentThread(){return Thread.currentThread()==level.owner;}public Executor harimt$ownerExecutor(){throw new AssertionError("must not join another dimension queue");}}}''',
'net/minecraft/world/entity/Entity.java':'''package net.minecraft.world.entity;import net.minecraft.server.level.ServerLevel;import net.minecraft.world.level.Level;public class Entity {final ServerLevel world;public volatile boolean removed;public int calls;public Entity(ServerLevel l){world=l;}public Level level(){return world;}public boolean isRemoved(){return removed;}public void discard(){if(Thread.currentThread()!=world.owner)throw new java.util.ConcurrentModificationException("Async entity unload");calls++;removed=true;}}''',
}
probe='''import net.minecraft.server.level.ServerLevel;import net.minecraft.world.entity.Entity;import java.util.concurrent.atomic.*;public class MtsRemovalProbe {public static void main(String[] a)throws Exception {var method=com.axalotl.async.forge.mixin.compat.ImmersiveVehiclesWorldMixin.class.getDeclaredMethod("harimt$discardOnOwner",Entity.class);method.setAccessible(true);ServerLevel previous=new ServerLevel();Entity follower=new Entity(previous);AtomicReference<Throwable> failure=new AtomicReference<>();AtomicBoolean rejected=new AtomicBoolean();Thread nextDimension=new Thread(()->{try{try{follower.discard();}catch(java.util.ConcurrentModificationException expected){rejected.set(true);}method.invoke(null,follower);}catch(Throwable e){failure.set(e);}},"dimthread_server_nether");nextDimension.start();nextDimension.join(1000);if(nextDimension.isAlive()||failure.get()!=null||!rejected.get()||follower.removed||previous.afterBarrier.size()!=1)throw new AssertionError("wrong-world mutation or cross-dimension join: "+failure.get());previous.afterBarrier.remove().run();if(!follower.removed||follower.calls!=1)throw new AssertionError("removal lost/duplicated");Entity local=new Entity(previous);method.invoke(null,local);if(local.calls!=1||!previous.afterBarrier.isEmpty())throw new AssertionError("owner removal queued");System.out.println("MTS_ACTUAL_HELPER_WRONG_WORLD_NEGATIVE_CONTROL_DEFERRED_BARRIER_PASS");}}'''
def main():
 p=argparse.ArgumentParser();p.add_argument('--report',type=Path);a=p.parse_args()
 with tempfile.TemporaryDirectory(prefix='mvh-mts-owner-') as tmp:
  root=Path(tmp)
  for name,body in stubs.items():f=root/name;f.parent.mkdir(parents=True,exist_ok=True);f.write_text(body)
  for rel in ['common/src/main/java/com/axalotl/async/common/ChunkOwnerExecutor.java','forge/src/main/java/com/axalotl/async/forge/mixin/compat/ImmersiveVehiclesWorldMixin.java']:
   f=root/Path(rel).parts[-1];f.write_bytes((I/'overlay'/rel).read_bytes())
  (root/'MtsRemovalProbe.java').write_text(probe)
  subprocess.run(['javac','--release','17','-d',str(root/'classes')]+list(map(str,root.rglob('*.java'))),check=True)
  r=subprocess.run(['java','-ea','-cp',str(root/'classes'),'MtsRemovalProbe'],check=True,capture_output=True,text=True,timeout=10)
  print(r.stdout.strip())
 if a.report:a.report.parent.mkdir(parents=True,exist_ok=True);a.report.write_text(json.dumps({'passed':True,'scope':'Production redirect helper with modeled Minecraft owners and real dimension thread; original C2ME-style wrong-owner removal reproduced, barrier deferral and single execution verified. Native travel is a separate proof.'},indent=2)+'\n')
if __name__=='__main__':main()
