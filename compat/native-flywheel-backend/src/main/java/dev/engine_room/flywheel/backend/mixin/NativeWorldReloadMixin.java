/* SPDX-License-Identifier: GPL-3.0-only */
package dev.engine_room.flywheel.backend.mixin;

import dev.engine_room.flywheel.impl.FlwImplXplat;
import mvhflywheelbackend.NativeEngine;
import net.vulkanmod.render.chunk.WorldRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Hari replaces vanilla allChanged(), so dispatch Flywheel's original event here. */
@Mixin(value=WorldRenderer.class, remap=false)
abstract class NativeWorldReloadMixin {
    @Inject(method="allChanged", at=@At("RETURN"), remap=false)
    private void mvh$originalReloadLifecycle(CallbackInfo ci) {
        var level=WorldRenderer.getLevel();
        if (level!=null) {
            FlwImplXplat.INSTANCE.dispatchReloadLevelRendererEvent(level);
            NativeEngine.RELOAD_EVENTS.incrementAndGet();
        }
    }
}
