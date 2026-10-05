/* SPDX-License-Identifier: GPL-3.0-only */
package mvhpondercompat;
import com.mojang.blaze3d.systems.RenderSystem;
import net.vulkanmod.compat.UniversalRendererGate;
import net.vulkanmod.vulkan.VRenderSystem;
import java.lang.invoke.*;

/** Translate only the pinned Ponder stencil capability; preserve the original selected GL path. */
public final class NativeStencilBridge {
    private static long enables,disables;
    private NativeStencilBridge(){}
    private static void check(int capability){
        RenderSystem.assertOnRenderThread();
        if(capability!=0x0B90)throw new IllegalArgumentException("Only the audited stencil capability is translated");
    }
    public static void glEnable(int capability){
        check(capability);
        if(UniversalRendererGate.vulkanRendererEnabled())VRenderSystem.enableStencilTest();
        else original(true,capability);
        enables++;
    }
    public static void glDisable(int capability){
        check(capability);
        if(UniversalRendererGate.vulkanRendererEnabled())VRenderSystem.disableStencilTest();
        else original(false,capability);
        disables++;
    }
    private static void original(boolean enable,int capability){
        // Lazy linkage keeps native sessions from initializing an OpenGL context. This is the
        // explicit, original GL11 fallback, not an unimplemented native operation or gate escape.
        try{(enable?Original.ENABLE:Original.DISABLE).invokeExact(capability);}
        catch(RuntimeException|Error e){throw e;}
        catch(Throwable e){throw new IllegalStateException("Original stencil call failed",e);}
    }
    private static final class Original {
        static final MethodHandle ENABLE=find("glEnable"),DISABLE=find("glDisable");
        private static MethodHandle find(String name){
            try{return MethodHandles.publicLookup().findStatic(Class.forName("org.lwjgl.opengl.GL11",true,NativeStencilBridge.class.getClassLoader()),name,MethodType.methodType(void.class,int.class));}
            catch(ReflectiveOperationException e){throw new ExceptionInInitializerError(e);}
        }
    }
    public static long[] callCounts(){RenderSystem.assertOnRenderThread();return new long[]{enables,disables};}
}
