package net.vulkanmod.gl;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import java.lang.reflect.Modifier;
import java.util.*;

/** Exact original storage helpers only; VAO, descriptors and complete draws stay separate. */
public final class PhysicsBufferCallers {
    public static final String FACADE="net/vulkanmod/gl/NativeBufferObjectApi";
    private static final Map<String,String> PINS=Map.of("net/diebuddies/opengl/BufferObject","c672d1b0518a9e34375a21193650c16550e448b6dd6947d57e32ded57810d347","net/diebuddies/opengl/ArenaBuffer","bbe08138ccb44ca7b61cbdb02b6ca0a8191b47636eee0b01537d3d9b39ef067a");
    private static final Set<String> ABI=buildAbi();
    private PhysicsBufferCallers(){}
    private static Set<String> buildAbi(){Set<String> result=new HashSet<>();for(var m:NativeBufferObjectApi.class.getDeclaredMethods())if(Modifier.isPublic(m.getModifiers())&&Modifier.isStatic(m.getModifiers())&&m.getName().startsWith("gl"))result.add(m.getName()+Type.getMethodDescriptor(m));return Set.copyOf(result);}
    public static boolean target(String name){return PINS.containsKey(name.replace('.','/'));}
    public static int apply(ClassNode node){
        if(!Objects.equals(PINS.get(node.name),ShaderCallerRemapper.canonicalDigest(node)))throw new IllegalStateException("Physics buffers require exact reviewed original body");
        int changed=0;for(var method:node.methods)for(var instruction:method.instructions)if(instruction instanceof MethodInsnNode call&&call.owner.equals("org/lwjgl/opengl/GL32C")){
            if(call.getOpcode()!=Opcodes.INVOKESTATIC||call.itf||!ABI.contains(call.name+call.desc))throw new IllegalStateException("Unknown Physics buffer invocation");call.owner=FACADE;changed++;
        }
        if(changed!=(node.name.endsWith("/BufferObject")?28:19))throw new IllegalStateException("Incomplete original buffer transport");return changed;
    }
}
