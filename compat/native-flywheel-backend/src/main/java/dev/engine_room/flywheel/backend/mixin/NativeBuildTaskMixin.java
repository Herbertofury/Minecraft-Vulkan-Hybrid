/* SPDX-License-Identifier: GPL-3.0-only */
package dev.engine_room.flywheel.backend.mixin;

import dev.engine_room.flywheel.lib.visualization.VisualizationHelper;
import mvhflywheelbackend.NativeEngine;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.vulkanmod.render.chunk.build.task.BuildTask;
import net.vulkanmod.render.chunk.build.task.CompileResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The original Flywheel RebuildTask hook at Hari's actual block-entity compile point. */
@Mixin(value=BuildTask.class, remap=false)
abstract class NativeBuildTaskMixin {
    @Inject(method="handleBlockEntity", at=@At("HEAD"), cancellable=true, remap=false)
    private void mvh$registerOriginalVisual(CompileResult result, BlockEntity blockEntity, CallbackInfo ci) {
        NativeEngine.CHUNK_VISUAL_REGISTRATIONS.incrementAndGet();
        if (VisualizationHelper.tryAddBlockEntity(blockEntity)) ci.cancel();
    }
}
