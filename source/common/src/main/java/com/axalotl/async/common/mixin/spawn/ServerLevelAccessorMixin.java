/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.world.entity.Entity
 *  net.minecraft.world.level.ServerLevelAccessor
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.injection.At
 *  org.spongepowered.asm.mixin.injection.Inject
 *  org.spongepowered.asm.mixin.injection.callback.CallbackInfo
 */
package com.axalotl.async.common.mixin.spawn;

import com.axalotl.async.common.ParallelProcessor;
import com.axalotl.async.common.config.AsyncConfig;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ServerLevelAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

@Mixin(value={ServerLevelAccessor.class})
public interface ServerLevelAccessorMixin {
    /**
     * Replaces the vanilla interface default method so Forge's Mixin AP can
     * generate a production refmap. The disabled path is vanilla 1.20.1's exact
     * self-and-passenger add loop; async-spawn mode retains HMT's add lock.
     */
    @Overwrite
    default void addFreshEntityWithPassengers(Entity entity) {
        ServerLevelAccessor self = (ServerLevelAccessor) this;
        if (com.axalotl.async.common.C2meThreadBoundary.mustHandoff()) {
            com.axalotl.async.common.C2meThreadBoundary.run(self.getLevel(), () -> self.addFreshEntityWithPassengers(entity));
            return;
        }
        if (AsyncConfig.disabled.getValue().booleanValue() || !AsyncConfig.enableAsyncSpawn.getValue().booleanValue()) {
            entity.getSelfAndPassengers().forEach(e -> ((ServerLevelAccessor)this).addFreshEntity(e));
            return;
        }
        Object object = ParallelProcessor.getEntityOperationLock(((ServerLevelAccessor)this).getLevel());
        synchronized (object) {
            entity.getSelfAndPassengers().forEach(e -> ((ServerLevelAccessor)this).addFreshEntity(e));
        }
    }
}

