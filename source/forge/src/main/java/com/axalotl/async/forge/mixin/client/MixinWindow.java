package com.axalotl.async.forge.mixin.client;

import com.axalotl.async.forge.client.hari263.HariRenderState;
import com.mojang.blaze3d.platform.Window;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Window.class)
public class MixinWindow {
    @Inject(method="<init>",at=@At("RETURN"))
    private void harimt$detectGpu(CallbackInfo ci){if (!net.vulkanmod.compat.UniversalRendererGate.vulkanRendererEnabled()) {
            HariRenderState.detectCapabilities();
        }}
}
