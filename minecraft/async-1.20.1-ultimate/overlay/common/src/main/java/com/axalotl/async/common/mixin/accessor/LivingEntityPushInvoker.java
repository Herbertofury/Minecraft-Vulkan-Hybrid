package com.axalotl.async.common.mixin.accessor;

import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Invokes LivingEntity#pushEntities after the async entity-tick barrier.
 * This deliberately reuses the vanilla method so cramming rules, selectors,
 * Forge hooks, and doPush semantics are not reimplemented by HariMT.
 */
@Mixin(LivingEntity.class)
public interface LivingEntityPushInvoker {
    @Invoker("pushEntities")
    void harimt$invokePushEntities();
}
