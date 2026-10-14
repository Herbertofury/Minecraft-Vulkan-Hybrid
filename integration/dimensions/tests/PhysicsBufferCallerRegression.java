import java.nio.file.*;import java.util.jar.*;
import org.objectweb.asm.*;import org.objectweb.asm.tree.*;
import net.vulkanmod.gl.PhysicsBufferCallers;import net.vulkanmod.gl.ShaderCallerRemapper;

/** Exact packaged original-body transport, inverse identity and refusal controls. */
public final class PhysicsBufferCallerRegression {
    static void require(boolean v,String m){if(!v)throw new AssertionError(m);}
    static ClassNode read(byte[] raw){ClassNode n=new ClassNode();new ClassReader(raw).accept(n,0);return n;}
    static void reject(ClassNode n){try{PhysicsBufferCallers.apply(n);throw new AssertionError("Unsafe buffer caller accepted");}catch(IllegalStateException expected){}}
    public static void main(String[]args)throws Exception{int sites=0;try(JarFile jar=new JarFile(args[0])){for(String name:new String[]{"BufferObject","ArenaBuffer"}){
        byte[] raw=jar.getInputStream(jar.getJarEntry("net/diebuddies/opengl/"+name+".class")).readAllBytes();String before=ShaderCallerRemapper.canonicalDigest(read(raw));ClassNode changed=read(raw);int count=PhysicsBufferCallers.apply(changed);require(count==(name.equals("BufferObject")?28:19),"all original sites");reject(changed);
        ClassNode unknown=read(raw);unknown.name="unknown/BufferObject";reject(unknown);ClassNode bad=read(raw);boolean edited=false;for(var method:bad.methods)for(var instruction:method.instructions)if(instruction instanceof MethodInsnNode c&&c.owner.equals("org/lwjgl/opengl/GL32C")){c.name="glUnreviewedCall";edited=true;break;}require(edited,"actual mutation exists");reject(bad);
        int reversed=0;for(var method:changed.methods)for(var instruction:method.instructions)if(instruction instanceof MethodInsnNode c&&c.owner.equals(PhysicsBufferCallers.FACADE)){c.owner="org/lwjgl/opengl/GL32C";reversed++;}require(reversed==count&&ShaderCallerRemapper.canonicalDigest(changed).equals(before),"inverse original body identical");sites+=count;
    }}System.out.println("PHYSICS_BUFFER_CALLER_REGRESSION_PASS controls=10 originalSites="+sites+" inverseBodiesIdentical=true");}
}
