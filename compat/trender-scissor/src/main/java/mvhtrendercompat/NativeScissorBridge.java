/* SPDX-License-Identifier: GPL-3.0-only */
package mvhtrendercompat;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;

/** Exact pinned TRender scissor calls use Minecraft's selected renderer and state owner. */
public final class NativeScissorBridge {
    private static long enables,disables,boxes;
    private NativeScissorBridge() {}
    private static void check(int capability) {
        RenderSystem.assertOnRenderThread();
        if(capability!=0x0C11)throw new IllegalArgumentException("Only the audited scissor capability is translated");
    }
    public static void glEnable(int capability) {
        check(capability);GlStateManager._enableScissorTest();enables++;
    }
    public static void glDisable(int capability) {
        check(capability);GlStateManager._disableScissorTest();disables++;
    }
    public static void glScissor(int x,int y,int width,int height) {
        RenderSystem.assertOnRenderThread();
        GlStateManager._scissorBox(x,y,width,height);boxes++;
    }
    public static long[] callCounts() {
        RenderSystem.assertOnRenderThread();return new long[]{enables,disables,boxes};
    }
}
