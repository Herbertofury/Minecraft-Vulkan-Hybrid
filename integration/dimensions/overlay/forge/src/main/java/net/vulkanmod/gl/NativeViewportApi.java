package net.vulkanmod.gl;
import java.nio.IntBuffer;
import com.mojang.blaze3d.systems.RenderSystem;
import net.vulkanmod.compat.UniversalRendererGate;
import net.vulkanmod.vulkan.Renderer;
import org.lwjgl.opengl.GL11C;

/** Reviewed original viewport calls on the owned render stream. GL uses bottom-left
 * coordinates; Vulkan uses a negative-height viewport. Queries retain logical zero
 * extents while an empty dynamic scissor suppresses only drawing. Other state/API
 * and context contracts retain their gates. The actual GL branch stays intact. */
public final class NativeViewportApi {
    private NativeViewportApi(){}
    private static boolean nativeRenderer(){return UniversalRendererGate.vulkanRendererEnabled();}
    private static boolean owned(){return RenderSystem.isOnRenderThread()&&Renderer.getInstance()!=null&&Renderer.isRecording()&&Renderer.getInstance().getBoundRenderPass()!=null;}
    public static void glViewport(int x,int y,int width,int height){
        if(!nativeRenderer()){GL11C.glViewport(x,y,width,height);return;}
        if(width<0||height<0){NativeGlErrors.record(1281);return;}
        if(!owned()){NativeGlErrors.record(1282);return;}
        Renderer.setOpenGlViewport(x,y,width,height);
    }
    public static void glGetIntegerv(int pname,IntBuffer params){
        if(!nativeRenderer()){GL11C.glGetIntegerv(pname,params);return;}
        if(pname!=2978)throw new IllegalArgumentException("Unimplemented native viewport query: "+pname);
        if(params.remaining()<4)throw new IllegalArgumentException("Viewport query requires four remaining integers");
        if(!owned()){NativeGlErrors.record(1282);return;}
        for(int n=0;n<4;n++)params.put(params.position()+n,Renderer.getOpenGlViewport(n));
    }
}
