package net.vulkanmod.gl;
import org.objectweb.asm.*;import org.objectweb.asm.tree.*;import java.util.*;
/** Pinned original ManagedGlState/BlendState algorithms, including constructor/copy/restore bodies.
 * Query and apply calls are native; this is not whole-provider or static-gate acceptance. */
public final class EssentialManagedStateCallers {
    public static final String FACADE="net/vulkanmod/gl/NativeEssentialStateApi";
    private static final Map<String,String>PINS=Map.of("gg/essential/universal/render/ManagedGlState","44da2704e1ecb5d19dff8e48c2eae41c1ff2744d86d914ea2592860dd97496ca","gg/essential/universal/render/ManagedGlState$Companion","aed33c85dbe2dec11acf74deee0c10afae40a3b2fb61b1248b8a6ba1a612fca3","gg/essential/universal/shader/BlendState","a7dd3b380cd6dc1836043d7f419da8494459ca9dff2e5c5243eb3803475f4955","gg/essential/universal/shader/BlendState$Companion","eacdaa998cacf6726b8a5121e54199c8d0677a4aef257bd50121e3fb147942b3");
    private static final Map<String,Integer>COUNTS=Map.of("gg/essential/universal/render/ManagedGlState",1,"gg/essential/universal/render/ManagedGlState$Companion",10,"gg/essential/universal/shader/BlendState",4,"gg/essential/universal/shader/BlendState$Companion",6);
    public static boolean target(String name){return PINS.containsKey(name.replace('.','/'));}
    public record Result(int calls){}
    public static Result apply(ClassNode node){
        if(!Objects.equals(PINS.get(node.name),ShaderCallerRemapper.canonicalDigest(node)))throw new IllegalStateException("Managed state needs exact original body");int calls=0;
        for(var method:node.methods)for(var instruction:method.instructions)if(instruction instanceof MethodInsnNode call){
            boolean query=call.owner.equals("org/lwjgl/opengl/GL11")&&Set.of("glGetBoolean(I)Z","glGetInteger(I)I","glGetFloat(I)F","glGetBooleanv(ILjava/nio/ByteBuffer;)V").contains(call.name+call.desc);
            boolean logic=call.owner.equals("com/mojang/blaze3d/platform/GlStateManager")&&call.name.equals("_logicOp")&&call.desc.equals("(I)V");
            boolean blend=call.owner.equals("gg/essential/universal/UGraphics")&&Set.of("enableBlend()V","disableBlend()V","blendEquation(I)V","tryBlendFuncSeparate(IIII)V").contains(call.name+call.desc);
            if(query||logic||blend){if(call.getOpcode()!=Opcodes.INVOKESTATIC||call.itf)throw new IllegalStateException("Changed managed state invocation");call.owner=FACADE;calls++;}
            else if(call.owner.startsWith("org/lwjgl/opengl/"))throw new IllegalStateException("Unimplemented managed state operation");
        }
        if(calls!=COUNTS.get(node.name))throw new IllegalStateException("Incomplete original managed state transport: "+calls);
        return new Result(calls);
    }
}
