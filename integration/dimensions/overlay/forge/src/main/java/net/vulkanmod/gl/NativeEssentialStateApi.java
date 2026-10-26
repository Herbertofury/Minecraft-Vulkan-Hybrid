package net.vulkanmod.gl;
import java.nio.ByteBuffer;
import com.mojang.blaze3d.systems.RenderSystem;
import net.vulkanmod.vulkan.VRenderSystem;
import net.vulkanmod.vulkan.shader.PipelineState;
/** Exact original managed-state queries and blend activation use Hari's actual Vulkan state.
 * No capability values, unsupported query defaults, or physical GL calls are provided. */
public final class NativeEssentialStateApi {
    private NativeEssentialStateApi() {}
    public static boolean glGetBoolean(int parameter) {
        RenderSystem.assertOnRenderThread();
        return switch(parameter) {
            case 2929 -> VRenderSystem.depthTest;
            case 2884 -> VRenderSystem.cull;
            case 3058 -> VRenderSystem.logicOp;
            case 2930 -> VRenderSystem.depthMask;
            case 32823 -> VRenderSystem.isPolygonOffsetEnabled();
            case 3042 -> PipelineState.blendInfo.enabled;
            default -> throw new IllegalArgumentException("Unimplemented managed boolean query: "+parameter);
        };
    }
    public static int glGetInteger(int parameter) {
        RenderSystem.assertOnRenderThread();
        return switch(parameter) {
            case 2932 -> VRenderSystem.depthFun;
            case 3056 -> VRenderSystem.logicOpFun;
            case 32777 -> blendEquationValue(PipelineState.blendInfo.blendOpRgb);
            case 32969 -> blendFactor(PipelineState.blendInfo.srcRgbFactor);
            case 32968 -> blendFactor(PipelineState.blendInfo.dstRgbFactor);
            case 32971 -> blendFactor(PipelineState.blendInfo.srcAlphaFactor);
            case 32970 -> blendFactor(PipelineState.blendInfo.dstAlphaFactor);
            default -> throw new IllegalArgumentException("Unimplemented managed integer query: "+parameter);
        };
    }
    private static int blendEquationValue(int operation) {
        return switch(operation) {
            case 0 -> 0x8006; case 1 -> 0x800A; case 2 -> 0x800B;
            case 3 -> 0x8007; case 4 -> 0x8008;
            default -> throw new IllegalStateException("Unimplemented native blend equation: "+operation);
        };
    }
    private static int blendFactor(int nativeFactor) {
        return switch(nativeFactor) {
            case 0 -> 0; case 1 -> 1; case 2 -> 768; case 3 -> 769;
            case 4 -> 774; case 5 -> 775; case 6 -> 770; case 7 -> 771;
            case 8 -> 772; case 9 -> 773; case 10 -> 32769; case 11 -> 32770;
            case 12 -> 32771; case 13 -> 32772; case 14 -> 776;
            default -> throw new IllegalStateException("Unimplemented native blend factor: "+nativeFactor);
        };
    }
    public static float glGetFloat(int parameter) {
        RenderSystem.assertOnRenderThread();
        return switch(parameter) {
            case 32824 -> VRenderSystem.getPolygonOffsetFactor();
            case 10752 -> VRenderSystem.getPolygonOffsetUnits();
            default -> throw new IllegalArgumentException("Unimplemented managed float query: "+parameter);
        };
    }
    public static void glGetBooleanv(int parameter,ByteBuffer destination) {
        RenderSystem.assertOnRenderThread();
        if(parameter!=3107)throw new IllegalArgumentException("Unimplemented managed boolean vector: "+parameter);
        if(destination.remaining()<4)throw new IllegalArgumentException("Managed color mask needs four bytes");
        int mask=VRenderSystem.getColorMask();
        for(int i=0;i<4;i++)destination.put(destination.position()+i,(byte)((mask&(1<<i))!=0?1:0));
    }
    public static void _logicOp(int operation) {
        RenderSystem.assertOnRenderThread();
        if(operation<0x1500||operation>0x150F)throw new IllegalArgumentException("Unknown logic operation: "+operation);
        VRenderSystem.logicOpFun=operation;
    }
    public static void enableBlend() { RenderSystem.enableBlend(); }
    public static void disableBlend() { RenderSystem.disableBlend(); }
    public static void blendEquation(int operation) { RenderSystem.blendEquation(operation); }
    public static void tryBlendFuncSeparate(int sourceRGB,int destinationRGB,int sourceAlpha,int destinationAlpha) {
        RenderSystem.blendFuncSeparate(sourceRGB,destinationRGB,sourceAlpha,destinationAlpha);
    }
}
