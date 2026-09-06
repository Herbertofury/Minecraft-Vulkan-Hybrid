package com.axalotl.async.common.mixin.entity;

import com.axalotl.async.common.gpu.GpuPushBatch;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Defers only LivingEntity's crowding/push phase while a mixed sync/async world
 * entity batch is active. Movement, AI, attacks, effects, and every other tick
 * phase stay in their original locations. Each original pushEntities invocation
 * is replayed after all entity workers converge on the stable dimension thread.
 */
@Mixin(value = LivingEntity.class, priority = 1002)
public abstract class LivingEntityPushMixin {
    @Inject(method = "pushEntities", at = @At("HEAD"), cancellable = true)
    private void harimt$deferBatchPushes(CallbackInfo ci) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (GpuPushBatch.shouldDefer(self)) {
            GpuPushBatch.defer(self);
            ci.cancel();
        }
    }
}
