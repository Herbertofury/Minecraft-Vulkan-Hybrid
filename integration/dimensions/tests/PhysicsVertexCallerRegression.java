import java.util.jar.*;import org.objectweb.asm.*;import org.objectweb.asm.tree.*;
import net.vulkanmod.gl.*;
/** Four original packaged delegate transports and meaningful inverse/refusal controls. */
public final class PhysicsVertexCallerRegression {
 static ClassNode read(byte[] b){var n=new ClassNode();new ClassReader(b).accept(n,0);return n;}
 static void require(boolean b,String s){if(!b)throw new AssertionError(s);}
 static void reject(ClassNode n){try{PhysicsVertexCallers.apply(n);throw new AssertionError("Unsafe vertex delegate accepted");}catch(IllegalStateException expected){}}
 public static void main(String[]a)throws Exception{int sites=0;try(var jar=new JarFile(a[0])){for(String suffix:new String[]{"GL43VertexAttribBinding","ARBVertexAttribBinding","GL44MultiBind","ARBMultiBind"}){
  byte[] raw=jar.getInputStream(jar.getJarEntry("net/diebuddies/opengl/VAO$"+suffix+".class")).readAllBytes();var n=read(raw);String before=ShaderCallerRemapper.canonicalDigest(n);int count=PhysicsVertexCallers.apply(n);require(count==(suffix.contains("MultiBind")?1:5),"all original sites");reject(n);var unknown=read(raw);unknown.name="unknown/Delegate";reject(unknown);var bad=read(raw);boolean mutation=false;for(var m:bad.methods)for(var i:m.instructions)if(i instanceof MethodInsnNode c&&c.owner.startsWith("org/lwjgl/opengl/")){c.name="glUnreviewed";mutation=true;}require(mutation,"actual mutation exists");reject(bad);
  String owner=suffix.startsWith("GL43")?"org/lwjgl/opengl/GL43C":suffix.startsWith("GL44")?"org/lwjgl/opengl/GL44C":suffix.contains("Multi")?"org/lwjgl/opengl/ARBMultiBind":"org/lwjgl/opengl/ARBVertexAttribBinding";int reversed=0;for(var m:n.methods)for(var i:m.instructions)if(i instanceof MethodInsnNode c&&c.owner.equals(PhysicsVertexCallers.FACADE)){c.owner=owner;reversed++;}require(reversed==count&&ShaderCallerRemapper.canonicalDigest(n).equals(before),"inverse original body identity");sites+=count;
 }}System.out.println("PHYSICS_VERTEX_CALLER_REGRESSION_PASS controls=20 originalSites="+sites+" inverseBodiesIdentical=true");}
}
