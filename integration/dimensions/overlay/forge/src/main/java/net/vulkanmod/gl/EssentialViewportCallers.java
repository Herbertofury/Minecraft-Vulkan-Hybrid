package net.vulkanmod.gl;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;
import java.util.Map;
import java.util.Set;
/** Only exact original viewport state bodies receive this reviewed GAME repair. */
public final class EssentialViewportCallers {
    public static final String FACADE="net/vulkanmod/gl/NativeViewportApi";
    private static final Map<String,String> PINS=Map.of(
        "gg/essential/universal/render/ViewportState","ef9f2bb10195033df8254289081ff62a1a66c5198e6e6ac6f8e50142fe17bfc0",
        "gg/essential/universal/render/ViewportState$Companion","25357bf35cc2c9bb1437ad9c2c0df8ba230fb9b633c22b797edc9f73ba5a9885",
        "gg/essential/util/GlViewportState","cd6278b8f3893eca6d964a99191dc94f48b25175a3c3cf7ece37a09cd15c32bb",
        "gg/essential/util/GlViewportState$Companion","82a34f6be54b83d1a1b699498b03a73954dfe3b1bb6af0bd8511c4edcf88b1d7");
    private static final Set<String> ABI=Set.of("glViewport(IIII)V","glGetIntegerv(ILjava/nio/IntBuffer;)V");
    private EssentialViewportCallers(){}
    public static boolean target(String name){return PINS.containsKey(name.replace('.','/'));}
    public static int apply(ClassNode target){
        String pin=PINS.get(target.name);
        if(pin==null||!pin.equals(ShaderCallerRemapper.canonicalDigest(target)))throw new IllegalStateException("Viewport caller requires reviewed original bytecode: "+target.name);
        int changed=0;
        for(MethodNode method:target.methods)for(AbstractInsnNode instruction:method.instructions)
            if(instruction instanceof MethodInsnNode call&&call.getOpcode()==Opcodes.INVOKESTATIC&&!call.itf&&call.owner.equals("org/lwjgl/opengl/GL11")&&ABI.contains(call.name+call.desc)){call.owner=FACADE;changed++;}
        if(changed!=1)throw new IllegalStateException("Original viewport call incomplete: "+target.name);return changed;
    }
}
