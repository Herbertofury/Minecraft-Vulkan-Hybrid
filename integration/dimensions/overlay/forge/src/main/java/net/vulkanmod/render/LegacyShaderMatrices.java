package net.vulkanmod.render;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.renderer.ShaderInstance;
import org.joml.Matrix4f;

/** Reusable render-owner scratch; original mod uniforms receive the actual draw matrices. */
public final class LegacyShaderMatrices {
    private final Matrix4f projection = new Matrix4f();

    public void apply(ShaderInstance shader, Matrix4f view, Matrix4f originalProjection) {
        RenderSystem.assertOnRenderThread();
        shader.apply();
        if (shader.MODEL_VIEW_MATRIX != null) shader.MODEL_VIEW_MATRIX.set(view);
        if (shader.PROJECTION_MATRIX != null) {
            projection.set(originalProjection);
            projection.m02((originalProjection.m02() + originalProjection.m03()) * 0.5F);
            projection.m12((originalProjection.m12() + originalProjection.m13()) * 0.5F);
            projection.m22((originalProjection.m22() + originalProjection.m23()) * 0.5F);
            projection.m32((originalProjection.m32() + originalProjection.m33()) * 0.5F);
            shader.PROJECTION_MATRIX.set(projection);
        }
    }
}
