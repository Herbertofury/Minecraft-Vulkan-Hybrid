package net.vulkanmod.gl;

import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import java.lang.reflect.Modifier;
import java.util.*;

/** Replaces supported shader invocations at callers in Forge's GAME loader.
 * Bootstrap LWJGL classes stay untouched, including Forge's early OpenGL display. */
public final class ShaderCallerRemapper {
    public static final String FACADE="net/vulkanmod/gl/NativeShaderApi";
    private static final Set<String> OWNERS=Set.of("org/lwjgl/opengl/GL20", "org/lwjgl/opengl/GL20C", "org/lwjgl/opengl/GL32C", "org/lwjgl/opengl/ARBShaderObjects");
    private static final Set<String> ABI=buildAbi();
    private ShaderCallerRemapper() {}
    private static Set<String> buildAbi() {
        Set<String> methods=new HashSet<>();
        for(var method:NativeShaderApi.class.getDeclaredMethods())
            if(Modifier.isPublic(method.getModifiers()) && Modifier.isStatic(method.getModifiers()) && method.getName().startsWith("gl"))
                methods.add(method.getName()+Type.getMethodDescriptor(method));
        return Set.copyOf(methods);
    }
    public static boolean supports(String owner,String name,String descriptor) {
        return OWNERS.contains(owner) && ABI.contains(name+descriptor)
                && (owner.endsWith("ARBShaderObjects") == name.endsWith("ARB"));
    }
    public record Result(int directCalls,int methodHandles) {}
    public static String canonicalDigest(ClassNode target) {
        // Remove source/line/frame attributes; keep code, members, constants and annotations.
        ClassWriter original=new ClassWriter(0);target.accept(original);
        ClassNode canonical=new ClassNode();new ClassReader(original.toByteArray()).accept(canonical,ClassReader.SKIP_DEBUG|ClassReader.SKIP_FRAMES);
        ClassWriter output=new ClassWriter(0);canonical.accept(output);
        try{return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(output.toByteArray()));}
        catch(java.security.NoSuchAlgorithmException error){throw new AssertionError(error);}
    }
    public static Result apply(ClassNode target) {
        int direct=0;int[] handles={0};
        for(MethodNode method:target.methods) for(AbstractInsnNode instruction:method.instructions) {
            if(instruction instanceof MethodInsnNode call && call.getOpcode()==Opcodes.INVOKESTATIC && !call.itf && supports(call.owner,call.name,call.desc)) {
                call.owner=FACADE;direct++;
            } else if(instruction instanceof InvokeDynamicInsnNode dynamic) {
                // Keep lambda metafactory and every unrelated bootstrap argument exact.
                for(int i=0;i<dynamic.bsmArgs.length;i++)dynamic.bsmArgs[i]=remapValue(dynamic.bsmArgs[i],handles);
            } else if(instruction instanceof LdcInsnNode constant) constant.cst=remapValue(constant.cst,handles);
        }
        return new Result(direct,handles[0]);
    }
    private static Object remapValue(Object value,int[] count) {
        if(value instanceof Handle handle && handle.getTag()==Opcodes.H_INVOKESTATIC && !handle.isInterface() && supports(handle.getOwner(),handle.getName(),handle.getDesc())) {
            count[0]++;return new Handle(handle.getTag(),FACADE,handle.getName(),handle.getDesc(),false);
        }
        if(value instanceof ConstantDynamic constant) {
            Object[] arguments=new Object[constant.getBootstrapMethodArgumentCount()];boolean changed=false;
            for(int i=0;i<arguments.length;i++) {Object before=constant.getBootstrapMethodArgument(i);arguments[i]=remapValue(before,count);changed|=before!=arguments[i];}
            if(changed)return new ConstantDynamic(constant.getName(),constant.getDescriptor(),constant.getBootstrapMethod(),arguments);
        }
        return value;
    }
}
