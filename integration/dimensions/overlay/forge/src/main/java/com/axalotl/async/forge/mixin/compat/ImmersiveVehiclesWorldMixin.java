package com.axalotl.async.forge.mixin.compat;
import net.minecraft.world.entity.Entity;
import net.minecraft.server.level.ServerLevel;
import com.axalotl.async.common.ChunkOwnerExecutor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Redirect;

/** MTS's shared follower map can reference an entity in the previous dimension. */
@Pseudo @Mixin(targets="mcinterface1201.WrapperWorld",remap=false)
public abstract class ImmersiveVehiclesWorldMixin {
 @Redirect(method="onIVWorldTick",at=@At(value="INVOKE",target="Lmcinterface1201/BuilderEntityExisting;m_146870_()V"),require=1,allow=1,remap=false)
 private void harimt$removeOldGunOnItsOwner(@Coerce Object builder){harimt$discardOnOwner((Entity)builder);}
 @Redirect(method="onIVWorldTick",at=@At(value="INVOKE",target="Lmcinterface1201/BuilderEntityRenderForwarder;m_146870_()V"),require=1,allow=1,remap=false)
 private void harimt$removeOldFollowerOnItsOwner(@Coerce Object builder){harimt$discardOnOwner((Entity)builder);}
 private static void harimt$discardOnOwner(Entity entity){
  if(entity.level() instanceof ServerLevel level && !((ChunkOwnerExecutor)(Object)level.getChunkSource()).harimt$ownerIsCurrentThread()){
   // Do not block a dimension worker on another dimension queued behind it.
   // The server handles this task after the dimension barrier restores owners.
   level.getServer().execute(()->{if(!entity.isRemoved())entity.discard();});
  }else entity.discard();
 }
}
