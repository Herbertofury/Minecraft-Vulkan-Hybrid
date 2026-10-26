package net.vulkanmod.gl;

import net.vulkanmod.compat.UniversalRendererGate;
import net.vulkanmod.vulkan.Renderer;
import net.vulkanmod.vulkan.VRenderSystem;
import org.lwjgl.opengl.GL11C;
import java.nio.IntBuffer;

/** Transport for reviewed original scissor and stencil callers in Forge GAME.
 * The bootstrap OpenGL API stays intact for the real OpenGL branch. Other native
 * capabilities and queries fail closed; this is not a general GL state facade. */
public final class NativeClipApi {
    private NativeClipApi() {}
    private static boolean nativeRenderer(){return UniversalRendererGate.vulkanRendererEnabled();}
    public static void glEnable(int cap){
        if(!nativeRenderer()){GL11C.glEnable(cap);return;}
        switch(cap){case 3089 -> Renderer.setScissorEnabled(true);case 2960 -> VRenderSystem.enableStencilTest();default -> throw unsupported(cap);}
    }
    public static void glDisable(int cap){
        if(!nativeRenderer()){GL11C.glDisable(cap);return;}
        switch(cap){case 3089 -> Renderer.setScissorEnabled(false);case 2960 -> VRenderSystem.disableStencilTest();default -> throw unsupported(cap);}
    }
    public static void glScissor(int x,int y,int width,int height){
        if(!nativeRenderer()){GL11C.glScissor(x,y,width,height);return;}
        Renderer.setScissor(x,y,width,height);
    }
    public static boolean glGetBoolean(int pname){
        if(!nativeRenderer())return GL11C.glGetBoolean(pname);
        if(pname!=3089)throw unsupported(pname);return Renderer.isScissorEnabled();
    }
    public static void glGetIntegerv(int pname,IntBuffer params){
        if(!nativeRenderer()){GL11C.glGetIntegerv(pname,params);return;}
        if(pname!=3088)throw unsupported(pname);
        if(params.remaining()<4)throw new IllegalArgumentException("Scissor query needs four remaining integers");
        for(int n=0;n<4;n++)params.put(params.position()+n,Renderer.getScissorBox(n));
    }
    public static void glStencilFunc(int function,int reference,int mask){
        if(!nativeRenderer()){GL11C.glStencilFunc(function,reference,mask);return;}
        if(function<512||function>519)throw unsupported(function);
        VRenderSystem.stencilFunc(function,reference,mask);
    }
    public static void glStencilOp(int fail,int depthFail,int pass){
        if(!nativeRenderer()){GL11C.glStencilOp(fail,depthFail,pass);return;}
        requireOperation(fail);requireOperation(depthFail);requireOperation(pass);VRenderSystem.stencilOp(fail,depthFail,pass);
    }
    private static void requireOperation(int operation){switch(operation){case 0,7680,7681,7682,7683,5386,34055,34056 -> {} default -> throw unsupported(operation);}}
    private static IllegalArgumentException unsupported(int parameter){return new IllegalArgumentException("Native clipping parameter is not implemented: "+parameter);}
}
