package net.vulkanmod.gl;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import java.util.*;
/** Composed exact original create/map/image/clear/upload/draw-framebuffer paths. */
public final class EssentialDeviceTransferCallers {
    public static final String TRANSFER="net/vulkanmod/gl/NativeEssentialTransferApi";
    private static final String ROOT="gg/essential/universal/render/UGpuDeviceImpl";
    public static boolean target(String name){return name.replace('.','/').equals(ROOT);}
    public record Result(int uploadCalls,int framebufferCalls,int scopedActions,int remainingGLCalls){}
    public static Result apply(ClassNode node){
        if(!node.name.equals(ROOT))throw new IllegalStateException("Unknown typed device transfer caller");EssentialDeviceFramebufferCallers.apply(node);int upload=0,framebuffers=0,actions=0;
        for(var method:node.methods)for(var instruction:method.instructions)if(instruction instanceof MethodInsnNode call){
            if(method.name.equals("copyBufferToTexture")&&call.owner.equals("gg/essential/universal/UGraphics")&&call.name.equals("configureTexture")&&call.desc.equals("(ILjava/lang/Runnable;)V")){call.owner=TRANSFER;upload++;}
            else if(method.name.equals("copyBufferToTexture$lambda$19")&&((call.owner.equals("org/lwjgl/opengl/GL11")&&Set.of("glPixelStorei(II)V","glTexSubImage2D(IIIIIIIIJ)V").contains(call.name+call.desc))||(call.owner.equals("org/lwjgl/opengl/GL15")&&call.name.equals("glBindBuffer")&&call.desc.equals("(II)V")))){call.owner=TRANSFER;upload++;}
            else if(method.name.equals("withDrawFramebuffer")&&call.owner.startsWith("org/lwjgl/opengl/")&&Set.of("glGetInteger(I)I","glBindFramebuffer(II)V","glFramebufferTexture2D(IIIII)V").contains(call.name+call.desc)){call.owner=EssentialDeviceFramebufferCallers.FACADE;framebuffers++;}
            else if(method.name.equals("withDrawFramebuffer")&&call.owner.equals("kotlin/jvm/functions/Function0")&&call.name.equals("invoke")&&call.desc.equals("()Ljava/lang/Object;")){call.owner=EssentialDeviceFramebufferCallers.FACADE;call.name="invokeFramebufferAction";call.desc="(Ljava/lang/Object;)Ljava/lang/Object;";call.setOpcode(Opcodes.INVOKESTATIC);call.itf=false;actions++;}
        }
        int remaining=0;for(var method:node.methods)for(var instruction:method.instructions)if(instruction instanceof MethodInsnNode call&&call.owner.startsWith("org/lwjgl/opengl/"))remaining++;
        if(upload!=14||framebuffers!=8||actions!=1||remaining!=0)throw new IllegalStateException("Incomplete or overbroad original transfer/action transport");return new Result(upload,framebuffers,actions,remaining);
    }
}
