package net.vulkanmod.gl;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;
import java.util.Map;
import java.util.Set;

/** Only these exact original bodies have native clipping semantics and proof. */
public final class EssentialClipCallers {
    public static final String FACADE="net/vulkanmod/gl/NativeClipApi";
    private static final Map<String,String> PINS=Map.of(
        "gg/essential/universal/render/ScissorState","13b1ee590daf89ac39cff6102024450f1ff29fb73447d1c38f6715e465d5237e",
        "gg/essential/universal/render/ScissorState$Companion","13a6dc160c043e351fce998a26822965a7fdb292cad90863fbc1864c922a9f1e",
        "gg/essential/util/ScissorState","663b26a49d798b1b9f272f9c17e47d7b71677b4a60f8d125a39e225e7f35a4ff",
        "gg/essential/util/ScissorState$Companion","4173aa8526708c2a0992f85496c938086403f915ad57f2222db25ae06ccb901b",
        "gg/essential/elementa/effects/StencilEffect","50f5dcda2aa475cdf84e11fd70bf20c99b710c6949a50326030b24f94eff74e3");
    private static final Map<String,Integer> CALLS=Map.of("gg/essential/universal/render/ScissorState",3,"gg/essential/universal/render/ScissorState$Companion",2,"gg/essential/util/ScissorState",3,"gg/essential/util/ScissorState$Companion",2,"gg/essential/elementa/effects/StencilEffect",6);
    private static final Set<String> ABI=Set.of("glEnable(I)V","glDisable(I)V","glScissor(IIII)V","glGetBoolean(I)Z","glGetIntegerv(ILjava/nio/IntBuffer;)V","glStencilFunc(III)V","glStencilOp(III)V");
    private EssentialClipCallers(){}
    public static boolean target(String name){return PINS.containsKey(name.replace('.','/'));}
    public static boolean supports(String owner,String name,String descriptor){return (owner.equals("org/lwjgl/opengl/GL11")||owner.equals("org/lwjgl/opengl/GL11C"))&&ABI.contains(name+descriptor);}
    public static int apply(ClassNode target){
        String pin=PINS.get(target.name);
        if(pin==null||!pin.equals(ShaderCallerRemapper.canonicalDigest(target)))throw new IllegalStateException("Native clipping caller requires reviewed original bytecode: "+target.name);
        int count=0;
        for(MethodNode method:target.methods)for(AbstractInsnNode instruction:method.instructions)
            if(instruction instanceof MethodInsnNode call&&call.getOpcode()==Opcodes.INVOKESTATIC&&!call.itf&&supports(call.owner,call.name,call.desc)){call.owner=FACADE;count++;}
        if(count!=CALLS.get(target.name))throw new IllegalStateException("Original clipping call sites incomplete: "+target.name);
        return count;
    }
}
