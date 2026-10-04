/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.google.common.collect.ImmutableList
 *  com.google.common.collect.Lists
 *  com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod
 *  com.llamalad7.mixinextras.injector.wrapoperation.Operation
 *  net.minecraft.core.BlockPos
 *  net.minecraft.nbt.CompoundTag
 *  net.minecraft.server.level.ServerLevel
 *  net.minecraft.world.entity.Entity
 *  net.minecraft.world.entity.Entity$RemovalReason
 *  net.minecraft.world.entity.player.Player
 *  net.minecraft.world.level.Level
 *  net.minecraft.world.level.block.Blocks
 *  net.minecraft.world.level.block.state.BlockState
 *  net.minecraft.world.level.chunk.ChunkAccess
 *  net.minecraft.world.level.chunk.ChunkStatus
 *  net.minecraft.world.level.chunk.LevelChunk
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.Shadow
 *  org.spongepowered.asm.mixin.Unique
 *  org.spongepowered.asm.mixin.injection.At
 *  org.spongepowered.asm.mixin.injection.Inject
 *  org.spongepowered.asm.mixin.injection.callback.CallbackInfo
 *  org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable
 */
package com.axalotl.async.common.mixin.entity;

import com.axalotl.async.common.mixin.accessor.EntityAccessor;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value={Entity.class})
public abstract class EntityMixin {
    @Unique
    private final Object async$lock = new Object();

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    @WrapMethod(method={"setRemoved"})
    private void setRemoved(Entity.RemovalReason reason, Operation<Void> original) {
        Entity self = (Entity) (Object) this;
        if (com.axalotl.async.common.C2meThreadBoundary.mustHandoff() && self.level() instanceof ServerLevel world) {
            com.axalotl.async.common.C2meThreadBoundary.run(world, () -> setRemoved(reason, original));
            return;
        }
        Object object = this.async$lock;
        synchronized (object) {
            original.call(new Object[]{reason});
        }
    }

    @WrapMethod(method={"getFeetBlockState"})
    private BlockState wrapFeetBlockState(Operation<BlockState> original) {
        BlockState blockState = (BlockState)original.call(new Object[0]);
        if (blockState == null) {
            Entity self = (Entity)(Object)this;
            Level level = self.level();
            if (level instanceof ServerLevel) {
                ServerLevel serverLevel = (ServerLevel)level;
                BlockPos pos = self.blockPosition();
                LevelChunk chunk = serverLevel.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
                if (chunk != null) {
                    return chunk.getBlockState(pos);
                }
                ChunkAccess access = serverLevel.getChunkSource().getChunk(pos.getX() >> 4, pos.getZ() >> 4, ChunkStatus.FULL, true);
                if (access instanceof LevelChunk) {
                    LevelChunk levelChunk = (LevelChunk)access;
                    return levelChunk.getBlockState(pos);
                }
            }
            return Blocks.AIR.defaultBlockState();
        }
        return blockState;
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    @WrapMethod(method={"addPassenger"})
    private void addPassenger(Entity passenger, Operation<Void> original) {
        Entity self = (Entity)(Object)this;
        if (passenger.getVehicle() != self) {
            throw new IllegalStateException("Use x.startRiding(y), not y.addPassenger(x)");
        }
        Object object = this.async$lock;
        synchronized (object) {
            EntityAccessor accessor = (EntityAccessor)(Object)this;
            ArrayList list = Lists.newArrayList(accessor.getPassengersField());
            if (!self.level().isClientSide() && passenger instanceof Player && !list.isEmpty() && !(list.get(0) instanceof Player)) {
                list.add(0, passenger);
            } else {
                list.add(passenger);
            }
            accessor.setPassengersField(ImmutableList.copyOf((Collection)list));
        }
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    @WrapMethod(method={"removePassenger"})
    private void removePassenger(Entity passenger, Operation<Void> original) {
        Entity self = (Entity)(Object)this;
        if (passenger.getVehicle() == self) {
            throw new IllegalStateException("Use x.stopRiding(y), not y.removePassenger(x)");
        }
        Object object = this.async$lock;
        synchronized (object) {
            EntityAccessor accessor = (EntityAccessor)(Object)this;
            ArrayList<Entity> list = new ArrayList<Entity>((Collection<Entity>)accessor.getPassengersField());
            list.remove(passenger);
            accessor.setPassengersField(ImmutableList.copyOf(list));
            ((EntityAccessor)passenger).setBoardingCooldown(60);
        }
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    @WrapMethod(method={"ejectPassengers"})
    private void ejectPassengers(Operation<Void> original) {
        ImmutableList<Entity> snapshot;
        Object object = this.async$lock;
        synchronized (object) {
            snapshot = ((EntityAccessor)(Object)this).getPassengersField();
        }
        for (int i = snapshot.size() - 1; i >= 0; --i) {
            ((Entity)snapshot.get(i)).stopRiding();
        }
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    @Inject(method={"getPassengers"}, at={@At(value="HEAD")}, cancellable=true)
    private void async$getPassengers(CallbackInfoReturnable<List<Entity>> cir) {
        Object object = this.async$lock;
        synchronized (object) {
            cir.setReturnValue(((EntityAccessor)(Object)this).getPassengersField());
        }
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    @Inject(method={"getFirstPassenger"}, at={@At(value="HEAD")}, cancellable=true)
    private void async$getFirstPassenger(CallbackInfoReturnable<Entity> cir) {
        Object object = this.async$lock;
        synchronized (object) {
            ImmutableList<Entity> snapshot = ((EntityAccessor)(Object)this).getPassengersField();
            cir.setReturnValue(snapshot.isEmpty() ? null : snapshot.get(0));
        }
    }

    @Inject(method={"load"}, at={@At(value="RETURN")})
    private void async$syncAfterLoad(CompoundTag tag, CallbackInfo ci) {
    }
}

