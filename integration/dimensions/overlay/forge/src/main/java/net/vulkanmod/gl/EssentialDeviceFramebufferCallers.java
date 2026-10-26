package net.vulkanmod.gl;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import java.util.*;

/** Pinned original clear algorithms and lazy framebuffer allocation; other root paths stay blocked. */
public final class EssentialDeviceFramebufferCallers {
    public static final String FACADE="net/vulkanmod/gl/NativeEssentialFramebufferApi";
    private static final String ROOT="gg/essential/universal/render/UGpuDeviceImpl";
    private static final Map<String,String>PINS=Map.of(ROOT,"ac1a55d26a3540c026566ebda4e832a554511068e7cf4224912a1bdc5e481de1",ROOT+"$colorWriteFrameBuffer$2","b791f9feaa2c4cba7344a16f5d882a2142c9746d1518d022bda33e4354d8a5f0",ROOT+"$depthWriteFrameBuffer$2","ae00dea162f4b257dbae8fc65a6518b6ee94a3d6ea49323c062151eb99e4861f");
    private static final Set<String>METHODS=Set.of("clearColor","clearDepth","glClear","genDepthOnlyFrameBuffer");
    private static final Set<String>ABI=Set.of("glGenFramebuffers()I","glGetInteger(I)I","glBindFramebuffer(II)V","glFramebufferTexture2D(IIIII)V","glDrawBuffer(I)V","glReadBuffer(I)V","glClear(I)V");
    public static boolean target(String name){return PINS.containsKey(name.replace('.','/'));}
    public record Result(int framebufferCalls,int retainedRootGLCalls){}
    public static Result apply(ClassNode node){
        if(!Objects.equals(PINS.get(node.name),ShaderCallerRemapper.canonicalDigest(node)))throw new IllegalStateException("Typed framebuffer needs exact original body");
        boolean root=node.name.equals(ROOT);if(root)EssentialDeviceImageCallers.apply(node);int calls=0;
        for(var method:node.methods)for(var instruction:method.instructions)if(instruction instanceof MethodInsnNode call){
            if(root&&!METHODS.contains(method.name))continue;
            if((call.owner.equals("org/lwjgl/opengl/GL11")||call.owner.equals("org/lwjgl/opengl/GL30"))&&ABI.contains(call.name+call.desc)&&call.getOpcode()==Opcodes.INVOKESTATIC&&!call.itf){call.owner=FACADE;calls++;}
            else if(root&&call.owner.equals("gg/essential/universal/UGraphics")&&(call.name.equals("clearColor")&&call.desc.equals("(FFFF)V")||call.name.equals("clearDepth")&&call.desc.equals("(D)V"))){call.owner=FACADE;calls++;}
        }
        int retained=0;for(var method:node.methods)for(var instruction:method.instructions)if(instruction instanceof MethodInsnNode call&&call.owner.startsWith("org/lwjgl/opengl/"))retained++;
        int expected=root?26:node.name.endsWith("$colorWriteFrameBuffer$2")?1:0;
        if(calls!=expected||retained!=(root?21:0))throw new IllegalStateException("Incomplete or overbroad typed framebuffer clear transport: "+calls+"/"+retained);
        return new Result(calls,retained);
    }
}
