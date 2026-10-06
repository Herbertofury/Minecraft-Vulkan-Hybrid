package dev.engine_room.flywheel.backend.mixin;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mojang.blaze3d.platform.GlStateManager;

import dev.engine_room.flywheel.backend.engine.uniform.LevelUniforms;

@Mixin(value = GlStateManager.class, remap = false)
abstract class GlStateManagerMixin {
	@Inject(method = "setupLevelDiffuseLighting", at = @At("HEAD"))
	private static void flywheel$onSetupLevelDiffuseLighting(Vector3f vector3f, Vector3f vector3f2, Matrix4f matrix4f, CallbackInfo ci) {
		// Capture the light directions before they're transformed into screen space
		// Basically all usages of assigning light direction go through here so I think this is safe
		LevelUniforms.LIGHT0_DIRECTION.set(vector3f);
		LevelUniforms.LIGHT1_DIRECTION.set(vector3f2);
	}
}
