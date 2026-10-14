import java.nio.file.*;import java.util.jar.*;
import org.objectweb.asm.*;import org.objectweb.asm.tree.*;
import net.vulkanmod.gl.PhysicsShaderCallers;import net.vulkanmod.gl.ShaderCallerRemapper;

/** Exact packaged transport and inverse original body with meaningful refusal controls. */
public final class PhysicsShaderCallerRegression {
    static void require(boolean v,String m){if(!v)throw new AssertionError(m);}
    static ClassNode read(byte[] bytes){ClassNode n=new ClassNode();new ClassReader(bytes).accept(n,0);return n;}
    static void reject(ClassNode n,String m){try{PhysicsShaderCallers.apply(n);throw new AssertionError("Unsafe caller accepted: "+m);}catch(IllegalStateException expected){}}
    public static void main(String[]args)throws Exception{
        byte[] bytes;try(JarFile jar=new JarFile(args[0])){bytes=jar.getInputStream(jar.getJarEntry("net/diebuddies/opengl/Shader.class")).readAllBytes();}
        String before=ShaderCallerRemapper.canonicalDigest(read(bytes));ClassNode changed=read(bytes);var result=PhysicsShaderCallers.apply(changed);require(result.directCalls()==52 && result.methodHandles()==0,"all52 reviewed original sites");reject(changed,"repeat rewrite");
        ClassNode unknown=read(bytes);unknown.name="unreviewed/Shader";reject(unknown,"unknown caller");
        ClassNode tampered=read(bytes);boolean edited=false;for(var method:tampered.methods)for(var instruction:method.instructions)if(instruction instanceof MethodInsnNode call && call.owner.equals("org/lwjgl/opengl/GL32C")){call.name="glUnreviewedCall";edited=true;break;}require(edited,"negative mutation existed");reject(tampered,"original code mutation");
        int reversed=0;for(var method:changed.methods)for(var instruction:method.instructions)if(instruction instanceof MethodInsnNode call && call.owner.equals(ShaderCallerRemapper.FACADE)){call.owner="org/lwjgl/opengl/GL32C";reversed++;}
        require(reversed==52 && ShaderCallerRemapper.canonicalDigest(changed).equals(before),"only owner changes; inverse original body");
        System.out.println("PHYSICS_SHADER_CALLER_REGRESSION_PASS controls=5 originalSites=52 inverseBodyIdentical=true");
    }
}
