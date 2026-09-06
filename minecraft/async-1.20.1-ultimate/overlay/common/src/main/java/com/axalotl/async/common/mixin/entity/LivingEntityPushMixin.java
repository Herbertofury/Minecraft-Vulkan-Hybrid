package com.axalotl.async.common.mixin.entity;

import com.axalotl.async.common.gpu.GpuPushBatch;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Defers only the entity-push/crowding phase of async LivingEntity ticks.
 * Movement, AI, attacks, effects, and every other part of the entity tick still
 * run in their original location. The exact vanilla pushEntities method is
 * replayed on the server thread after the async worker barrier.
 */
@Mixin(value = LivingEntity.class, priority = 1002)
public abstract class LivingEntityPushMixin {
    @Inject(method = "pushEntities", at = @At("HEAD"), cancellable = true)
    private void harimt$deferAsyncPushes(CallbackInfo ci) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (GpuPushBatch.shouldDefer(self)) {
            GpuPushBatch.defer(self);
            ci.cancel();
        }
    }
}
