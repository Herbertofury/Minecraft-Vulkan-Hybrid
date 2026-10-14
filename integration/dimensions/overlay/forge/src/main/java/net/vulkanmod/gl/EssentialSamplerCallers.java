package net.vulkanmod.gl;
import org.objectweb.asm.*;import org.objectweb.asm.tree.*;import java.util.*;
/** One pinned original sampler constructor/copy/configure body; no entire-provider acceptance. */
public final class EssentialSamplerCallers {
    public static final String FACADE="net/vulkanmod/gl/NativeEssentialSamplerApi";
    public static boolean target(String name){return name.replace('.','/').equals("gg/essential/universal/render/UGpuSamplerImpl");}
    public record Result(int calls){}
    public static Result apply(ClassNode node){if(!target(node.name)||!"29ca7fbb681407137f7b30a7c293c4d0846fdcc063bbebef7ca934e82bd9432a".equals(ShaderCallerRemapper.canonicalDigest(node)))throw new IllegalStateException("Sampler needs exact original body");int calls=0;
        for(var method:node.methods)for(var insn:method.instructions)if(insn instanceof MethodInsnNode call){boolean parameter=call.owner.equals("org/lwjgl/opengl/GL11")&&Set.of("glTexParameteri(III)V","glTexParameterf(IIF)V").contains(call.name+call.desc);boolean scope=call.owner.equals("gg/essential/universal/UGraphics")&&call.name.equals("configureTexture")&&call.desc.equals("(ILjava/lang/Runnable;)V");if(parameter||scope){if(call.getOpcode()!=Opcodes.INVOKESTATIC||call.itf)throw new IllegalStateException("Changed sampler call");call.owner=FACADE;calls++;}else if(call.owner.startsWith("org/lwjgl/opengl/"))throw new IllegalStateException("Unimplemented sampler call");}
        if(calls!=6)throw new IllegalStateException("Incomplete original sampler transport: "+calls);return new Result(calls);
    }
}
