package net.vulkanmod.render.chunk.frustum;

import com.mojang.blaze3d.systems.RenderSystem;
import org.joml.Matrix4f;

/** Render-owner snapshot of the exact visibility inputs; no approximate matrix reuse. */
public final class FrustumVisibilityState {
    private final Matrix4f matrix = new Matrix4f();
    private boolean valid, smartCull, spectator;
    private long captures;

    public boolean matches(Matrix4f current, boolean smartCull, boolean spectator) {
        RenderSystem.assertOnRenderThread();
        return this.valid && this.smartCull==smartCull && this.spectator==spectator
                && this.matrix.equals(current);
    }

    public void capture(Matrix4f current, boolean smartCull, boolean spectator) {
        RenderSystem.assertOnRenderThread();
        this.valid = finite(current);
        if (this.valid) this.matrix.set(current);
        this.smartCull=smartCull;
        this.spectator=spectator;
        this.captures++;
    }

    public void invalidate() { RenderSystem.assertOnRenderThread(); this.valid=false; }
    public long captures() { RenderSystem.assertOnRenderThread(); return this.captures; }

    private static boolean finite(Matrix4f m) {
        return Float.isFinite(m.m00()) && Float.isFinite(m.m01()) && Float.isFinite(m.m02()) && Float.isFinite(m.m03())
                && Float.isFinite(m.m10()) && Float.isFinite(m.m11()) && Float.isFinite(m.m12()) && Float.isFinite(m.m13())
                && Float.isFinite(m.m20()) && Float.isFinite(m.m21()) && Float.isFinite(m.m22()) && Float.isFinite(m.m23())
                && Float.isFinite(m.m30()) && Float.isFinite(m.m31()) && Float.isFinite(m.m32()) && Float.isFinite(m.m33());
    }
}
