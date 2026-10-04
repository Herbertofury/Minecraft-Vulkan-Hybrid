/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.world.entity.Entity
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.gen.Accessor
 */
package com.axalotl.async.common.mixin.accessor;

import com.google.common.collect.ImmutableList;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value={Entity.class})
public interface EntityAccessor {
    @Accessor(value="isInsidePortal")
    public boolean isInsidePortal();

    @Accessor(value="boardingCooldown")
    public void setBoardingCooldown(int var1);

    @Accessor(value="passengers")
    public ImmutableList<Entity> getPassengersField();

    @Accessor(value="passengers")
    public void setPassengersField(ImmutableList<Entity> passengers);
}

