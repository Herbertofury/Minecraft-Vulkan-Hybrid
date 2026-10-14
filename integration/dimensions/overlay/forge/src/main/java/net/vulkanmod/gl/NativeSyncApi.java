package net.vulkanmod.gl;

import net.vulkanmod.compat.UniversalRendererGate;
import org.lwjgl.opengl.GL11C;
import org.lwjgl.opengl.GL32C;

/** Reviewed GAME fence transport. OpenGL branches use the real bootstrap driver. */
public final class NativeSyncApi {
    private NativeSyncApi(){}
    private static boolean nativeRenderer(){return UniversalRendererGate.vulkanRendererEnabled();}
    public static long glFenceSync(int condition,int flags){return nativeRenderer()?GlSync.fenceSync(condition,flags):GL32C.glFenceSync(condition,flags);}
    public static void glDeleteSync(long sync){if(nativeRenderer())GlSync.deleteSync(sync);else GL32C.glDeleteSync(sync);}
    public static int glClientWaitSync(long sync,int flags,long timeout){return nativeRenderer()?GlSync.clientWaitSync(sync,flags,timeout):GL32C.glClientWaitSync(sync,flags,timeout);}
    public static boolean glIsSync(long sync){return nativeRenderer()?GlSync.isSync(sync):GL32C.glIsSync(sync);}
    public static void glWaitSync(long sync,int flags,long timeout){if(nativeRenderer())GlSync.waitSync(sync,flags,timeout);else GL32C.glWaitSync(sync,flags,timeout);}
    public static int glGetSynci(long sync,int pname){return nativeRenderer()?GlSync.getSynci(sync,pname):GL32C.glGetSynci(sync,pname,(java.nio.IntBuffer)null);}
    public static int glGetError(){return nativeRenderer()?GlSync.getError():GL11C.glGetError();}
}
