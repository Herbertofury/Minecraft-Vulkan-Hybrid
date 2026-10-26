/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod
 *  com.llamalad7.mixinextras.injector.wrapoperation.Operation
 *  net.minecraft.world.entity.EntityType
 *  net.minecraft.world.entity.EquipmentSlot
 *  net.minecraft.world.entity.Mob
 *  net.minecraft.world.entity.item.ItemEntity
 *  net.minecraft.world.item.ItemStack
 *  org.jetbrains.annotations.Nullable
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.Unique
 */
package com.axalotl.async.common.mixin.entity;

import com.axalotl.async.common.config.AsyncConfig;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.Unique;

@Mixin(value={Mob.class})
public class MobMixin {
    @Unique
    private static final Object async$lock = new Object();

    /**
     * Minecraft 26.3 stopped resetting persistent mobs' noActionTime solely because they are persistent.
     * Capture the value before vanilla 1.20.1 checkDespawn() so the tail hook can undo only that legacy
     * reset when no player is inside the category's vanilla no-despawn radius. This deliberately leaves
     * vanilla goal logic, despawn rules, targeting, navigation, and near-player reset behavior untouched.
     */
    @Unique
    private int harimt$mc263NoActionTimeBeforeDespawn;

    @Inject(method = "checkDespawn", at = @At("HEAD"))
    private void harimt$captureMc263PersistentIdleState(CallbackInfo ci) {
        if (AsyncConfig.enableMc263PersistentMobIdle.getValue()) {
            this.harimt$mc263NoActionTimeBeforeDespawn = ((Mob)(Object)this).getNoActionTime();
        }
    }

    @Inject(method = "checkDespawn", at = @At("RETURN"))
    private void harimt$restoreMc263PersistentIdleState(CallbackInfo ci) {
        if (!AsyncConfig.enableMc263PersistentMobIdle.getValue()) {
            return;
        }

        Mob mob = (Mob)(Object)this;
        if (!(mob.isPersistenceRequired() || mob.requiresCustomPersistence())) {
            return;
        }

        Entity nearestPlayer = mob.level().getNearestPlayer(mob, -1.0D);
        int noDespawnDistance = mob.getType().getCategory().getNoDespawnDistance();
        double noDespawnDistanceSqr = (double)noDespawnDistance * (double)noDespawnDistance;
        if (nearestPlayer == null || nearestPlayer.distanceToSqr(mob) >= noDespawnDistanceSqr) {
            mob.setNoActionTime(Math.max(mob.getNoActionTime(), this.harimt$mc263NoActionTimeBeforeDespawn));
        }
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    @WrapMethod(method={"equipItemIfPossible"})
    private ItemStack tryEquip(ItemStack stack, Operation<ItemStack> original) {
        Object object = async$lock;
        synchronized (object) {
            return (ItemStack)original.call(new Object[]{stack});
        }
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    @WrapMethod(method={"pickUpItem"})
    private void pickUpItem(ItemEntity entity, Operation<Void> original) {
        Object object = async$lock;
        synchronized (object) {
            original.call(new Object[]{entity});
        }
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    @WrapMethod(method={"setItemSlotAndDropWhenKilled"})
    private void equipLootStack(EquipmentSlot slot, ItemStack stack, Operation<Void> original) {
        Object object = async$lock;
        synchronized (object) {
            original.call(new Object[]{slot, stack});
        }
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    @WrapMethod(method={"convertTo(Lnet/minecraft/world/entity/EntityType;Z)Lnet/minecraft/world/entity/Mob;"})
    @Nullable
    private <T extends Mob> T convertTo(EntityType<T> entityType, boolean mysteryBool, Operation<T> original) {
        Object object = async$lock;
        synchronized (object) {
            if (((Mob)(Object)this).isRemoved()) {
                return null;
            }
            return (T)((Mob)original.call(new Object[]{entityType, mysteryBool}));
        }
    }
}

