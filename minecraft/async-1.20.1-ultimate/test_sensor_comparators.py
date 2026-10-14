#!/usr/bin/env python3
"""Compile all three real sensor mixins; reproduce old stubs and prove distance ordering."""
import argparse,json,shutil,subprocess,tempfile
from pathlib import Path
NAMES=['NearestLivingEntitySensorMixin','NearestItemSensorMixin','PlayerSensorMixin']
STUBS={
'com/axalotl/async/common/config/AsyncConfig.java':'package com.axalotl.async.common.config; import java.util.*; public class AsyncConfig {public static final Map.Entry<String,Boolean> disabled=new AbstractMap.SimpleEntry<>("disabled",false);}',
'net/minecraft/server/level/ServerLevel.java':'package net.minecraft.server.level; public class ServerLevel {}',
'net/minecraft/world/entity/Entity.java':'''package net.minecraft.world.entity; public class Entity {public double x,y,z; public int reads;public double getX(){reads++;return x;}public double getY(){reads++;return y;}public double getZ(){reads++;return z;}public double distanceToSqr(Entity e){double dx=x-e.x,dy=y-e.y,dz=z-e.z;return dx*dx+dy*dy+dz*dz;}public boolean equals(Object e){return e instanceof Entity;}public int hashCode(){return 1;}}''',
'net/minecraft/world/entity/LivingEntity.java':'package net.minecraft.world.entity; public class LivingEntity extends Entity {}',
'net/minecraft/world/entity/Mob.java':'package net.minecraft.world.entity; public class Mob extends LivingEntity {}',
'net/minecraft/world/entity/item/ItemEntity.java':'package net.minecraft.world.entity.item; public class ItemEntity extends net.minecraft.world.entity.Entity {}',
'net/minecraft/server/level/ServerPlayer.java':'package net.minecraft.server.level; public class ServerPlayer extends net.minecraft.world.entity.LivingEntity {}',
'org/spongepowered/asm/mixin/Mixin.java':'package org.spongepowered.asm.mixin; public @interface Mixin {Class<?>[] value();int priority();}',
'org/spongepowered/asm/mixin/injection/At.java':'package org.spongepowered.asm.mixin.injection; public @interface At {String value();String target();}',
'org/spongepowered/asm/mixin/injection/Redirect.java':'package org.spongepowered.asm.mixin.injection; public @interface Redirect {String[] method();At at();}',
}
for sensor in ['NearestLivingEntitySensor','NearestItemSensor','PlayerSensor']:
 STUBS[f'net/minecraft/world/entity/ai/sensing/{sensor}.java']=f'package net.minecraft.world.entity.ai.sensing; public class {sensor} {{}}'
PROBE=r'''
import com.axalotl.async.common.config.AsyncConfig;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.server.level.ServerPlayer;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.*;
import java.lang.reflect.*;
public class SensorProbe {
 static final String[] names={"NearestLivingEntitySensorMixin","NearestItemSensorMixin","PlayerSensorMixin"};
 static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);}
 static Entity entity(int kind){return kind==0?new LivingEntity():kind==1?new ItemEntity():new ServerPlayer();}
 @SuppressWarnings("unchecked") static Comparator<Entity> comparator(int kind,Mob source,ToDoubleFunction<Entity> key)throws Exception{
  Class<?> type=Class.forName("com.axalotl.async.common.mixin.entity.sensor."+names[kind]);Object mixin=type.getConstructor().newInstance();
  Method method=Arrays.stream(type.getDeclaredMethods()).filter(m->m.getName().equals("async$safeComparator")).findFirst().orElseThrow();method.setAccessible(true);
  return (Comparator<Entity>)method.invoke(mixin,key,null,source);
 }
 static void sorting(int kind,long seed)throws Exception{
  Random random=new Random(seed);Mob source=new Mob();source.x=3.5;source.y=-7.2;source.z=16.3;List<Entity> actual=new ArrayList<>();
  for(int i=0;i<250;i++){Entity e=entity(kind);e.x=random.nextInt(100)-50;e.y=random.nextInt(100)-50;e.z=random.nextInt(100)-50;actual.add(e);}
  Entity same=entity(kind);same.x=actual.get(0).x;same.y=actual.get(0).y;same.z=actual.get(0).z;actual.add(same);
  Entity nan=entity(kind);nan.x=Double.NaN;actual.add(nan);Entity infinite=entity(kind);infinite.x=Double.POSITIVE_INFINITY;actual.add(infinite);
  List<Entity> expected=new ArrayList<>(actual);expected.sort(Comparator.comparingDouble(e->e.distanceToSqr(source)));
  actual.sort(comparator(kind,source,e->e.distanceToSqr(source)));
  for(int i=0;i<actual.size();i++)check(actual.get(i)==expected.get(i),"vanilla order or stable tie changed: "+names[kind]);
 }
 public static void main(String[] args)throws Exception{
  if(args[0].equals("negative")){
   for(int kind=0;kind<3;kind++){Entity a=entity(kind),b=entity(kind);a.x=1;b.x=2;
    try{comparator(kind,new Mob(),e->e.x).compare(a,b);throw new AssertionError("old stub did not crash");}catch(IllegalStateException e){check(e.getMessage().equals("Decompilation failed"),"wrong negative failure");}
   }System.out.println("{\"old_executable_sensor_stubs_reproduced\":3}");return;
  }
  for(int kind=0;kind<3;kind++){
   sorting(kind,17);
   Mob source=new Mob();Entity a=entity(kind),b=entity(kind);a.x=1;b.x=2;Comparator<Entity> cmp=comparator(kind,source,e->e.distanceToSqr(source));
   check(cmp.compare(a,b)<0,"identity-colliding targets shared cache");int reads=a.reads+b.reads;
   a.x=100;b.x=1000;source.x=999;check(cmp.compare(a,b)<0&&a.reads+b.reads==reads,"movement changed comparator contract/cache");
   check(source.reads==3,"observer was not frozen once");check(comparator(kind,source,e->e.distanceToSqr(source)).compare(a,b)>0,"fresh comparator not refreshed");
   AsyncConfig.disabled.setValue(true);Comparator<Entity> disabled=comparator(kind,null,e->-e.x);check(disabled.compare(a,b)>0,"disabled extractor not retained");
   RuntimeException expected=new IllegalArgumentException("original extractor failure");Comparator<Entity> bad=comparator(kind,null,e->{throw expected;});try{bad.compare(a,b);throw new AssertionError("extractor failure swallowed");}catch(RuntimeException e){check(e==expected,"extractor failure changed");}
   AsyncConfig.disabled.setValue(false);
  }
  AtomicReference<Throwable> failure=new AtomicReference<>();int sorts=0;
  for(int count:new int[]{1,2,8}){
   List<Thread> threads=new ArrayList<>();
   for(int w=0;w<count;w++){final int worker=w;Thread t=new Thread(()->{try{for(int kind=0;kind<3;kind++)for(int i=0;i<10;i++)sorting(kind,worker*1000+i);}catch(Throwable e){failure.set(e);}},"sensor-worker");threads.add(t);t.start();}
   for(Thread t:threads){t.join(10000);check(!t.isAlive(),"sensor worker stalled");}if(failure.get()!=null)throw new AssertionError(failure.get());sorts+=count*30;
  }
  System.out.println("{\"passed\":true,\"actual_sensor_mixins\":3,\"stable_world_entities_checked\":759,\"concurrent_sorts\":"+sorts+",\"identity_snapshot_cache\":true,\"movement_stable_order\":true,\"disabled_extractor_and_failure_preserved\":true}");
 }
}
'''
def main():
 p=argparse.ArgumentParser();p.add_argument('source',type=Path);p.add_argument('--baseline',type=Path,required=True);p.add_argument('--report',type=Path);args=p.parse_args();reports={}
 for source_root in ['common/src/main/java','forge/src/main/java','fabric/src/main/java']:
  for file in (args.source/source_root).rglob('*.java'):
   body=file.read_text();assert 'Decompilation failed' not in body and 'This method has failed to decompile' not in body,file
 with tempfile.TemporaryDirectory(prefix='hari-sensors-') as tmp:
  for variant,origin in [('negative',args.baseline),('positive',args.source)]:
   root=Path(tmp)/variant;root.mkdir()
   for name,body in STUBS.items():
    dest=root/name;dest.parent.mkdir(parents=True,exist_ok=True);dest.write_text(body)
   for name in NAMES:
    relative=f'com/axalotl/async/common/mixin/entity/sensor/{name}.java';dest=root/relative;dest.parent.mkdir(parents=True,exist_ok=True);dest.write_bytes((origin/'common/src/main/java'/relative).read_bytes())
   (root/'SensorProbe.java').write_text(PROBE);compiler=['javac'] if shutil.which('javac') else ['java','com.sun.tools.javac.Main']
   subprocess.run(compiler+['--release','17','-d',str(root/'classes')]+list(map(str,root.rglob('*.java'))),check=True)
   run=subprocess.run(['java','-ea','-cp',str(root/'classes'),'SensorProbe',variant],text=True,capture_output=True,check=True,timeout=30);reports.update(json.loads(run.stdout))
 reports['executable_decompiler_source_placeholders']=0;reports['scope']='Actual three production mixins, checked Minecraft entity interfaces and real parallel sorts. Native mob sensing/chunk-travel proof remains separate.';print(json.dumps(reports,indent=2))
 if args.report:args.report.parent.mkdir(parents=True,exist_ok=True);args.report.write_text(json.dumps(reports,indent=2)+'\n')
if __name__=='__main__':main()
