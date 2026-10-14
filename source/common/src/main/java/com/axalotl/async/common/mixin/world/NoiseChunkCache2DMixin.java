package com.axalotl.async.common.mixin.world;

import com.axalotl.async.common.config.AsyncConfig;
import net.minecraft.world.level.levelgen.DensityFunction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Cache-aware bulk fill for the 1.20.1 NoiseChunk Cache2D wrapper.
 *
 * Minecraft 26.3 replaced the legacy specialized density caches with a unified
 * cache model. This backports the safe part: bulk evaluation must pass through
 * the cache instead of bypassing it. No float-density conversion is performed.
 */
@Mixin(targets = "net.minecraft.world.level.levelgen.NoiseChunk$Cache2D")
public abstract class NoiseChunkCache2DMixin implements DensityFunction {
    @Inject(method = "fillArray", at = @At("HEAD"), cancellable = true)
    private void harimt$mc263CacheAwareFill(double[] values, DensityFunction.ContextProvider contextProvider, CallbackInfo ci) {
        if (!AsyncConfig.enableMc263DensityCacheFill.getValue()) {
            return;
        }
        contextProvider.fillAllDirectly(values, (DensityFunction)(Object)this);
        ci.cancel();
    }
}
