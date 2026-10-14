import java.util.*;import java.util.jar.*;
import org.objectweb.asm.*;import org.objectweb.asm.tree.*;
import net.vulkanmod.gl.*;
/** Exact original bodies, capability/runtime-field inverses and meaningful refusal controls. */
public final class PhysicsVaoCallerRegression {
 static ClassNode read(byte[] b){var n=new ClassNode();new ClassReader(b).accept(n,0);return n;}
 static void require(boolean b,String s){if(!b)throw new AssertionError(s);}
 static void reject(ClassNode n){try{PhysicsVaoCallers.apply(n);throw new AssertionError("Unsafe outer VAO accepted");}catch(IllegalStateException expected){}}
 public static void main(String[]args)throws Exception{int calls=0,caps=0,fields=0,controls=0;try(var jar=new JarFile(args[0])){for(String name:new String[]{"VAO","VertexFormat","StateTracker"}){
  byte[] raw=jar.getInputStream(jar.getJarEntry("net/diebuddies/opengl/"+name+".class")).readAllBytes();var n=read(raw);String before=ShaderCallerRemapper.canonicalDigest(n);List<String> owners=new ArrayList<>();for(var m:n.methods)for(var i:m.instructions)if(i instanceof MethodInsnNode c&&Set.of("org/lwjgl/opengl/GL32C","org/lwjgl/opengl/GL33C","org/lwjgl/opengl/ARBInstancedArrays").contains(c.owner))owners.add(c.owner);
  var result=PhysicsVaoCallers.apply(n);require(result.calls()==owners.size(),"all original calls");controls++;calls+=result.calls();caps+=result.capabilities();fields+=result.runtimeFields();reject(n);controls++;var unknown=read(raw);unknown.name="unknown/Outer";reject(unknown);controls++;var tampered=read(raw);boolean changed=false;for(var m:tampered.methods)for(var i:m.instructions)if(i instanceof MethodInsnNode c&&c.owner.startsWith("org/lwjgl/opengl/")){c.name="glUnreviewed";changed=true;}require(changed,"real method mutation");reject(tampered);controls++;
  int cursor=0;for(var m:n.methods)for(var i:m.instructions.toArray()){
   if(i instanceof MethodInsnNode c&&c.owner.equals(PhysicsVaoCallers.FACADE))c.owner=owners.get(cursor++);
   else if(i instanceof MethodInsnNode c&&c.owner.equals(PhysicsVaoCallers.RUNTIME)){
    if(c.name.equals("memoryStack"))m.instructions.set(c,new FieldInsnNode(Opcodes.GETSTATIC,"net/diebuddies/physics/StarterClient","memoryStack","Lorg/lwjgl/system/MemoryStack;"));
    else{String field=PhysicsVaoCallers.CAPABILITIES.entrySet().stream().filter(e->e.getValue().equals(c.name)).map(Map.Entry::getKey).findFirst().orElseThrow();c.owner="org/lwjgl/opengl/GL";c.name="getCapabilities";c.desc="()Lorg/lwjgl/opengl/GLCapabilities;";m.instructions.insert(c,new FieldInsnNode(Opcodes.GETFIELD,"org/lwjgl/opengl/GLCapabilities",field,"Z"));}
   }else if(i instanceof FieldInsnNode f&&f.owner.equals(PhysicsVaoCallers.RUNTIME))f.owner="net/diebuddies/physics/StarterClient";
  }
  require(cursor==owners.size()&&ShaderCallerRemapper.canonicalDigest(n).equals(before),"inverse full original body");controls++;
  if(name.equals("VAO")){var cap=read(raw);boolean mutated=false;for(var m:cap.methods)for(var i:m.instructions)if(i instanceof FieldInsnNode f&&f.owner.equals("org/lwjgl/opengl/GLCapabilities")){f.name="GL_Unimplemented";mutated=true;}require(mutated,"real capability mutation");reject(cap);controls++;var stack=read(raw);mutated=false;for(var m:stack.methods)for(var i:m.instructions)if(i instanceof FieldInsnNode f&&f.owner.equals("net/diebuddies/physics/StarterClient")){f.name="unknownThreadState";mutated=true;}require(mutated,"real runtime mutation");reject(stack);controls++;}
 }}require(calls==26&&caps==10&&fields==24,"original site totals");controls++;System.out.println("PHYSICS_VAO_CALLER_REGRESSION_PASS controls="+controls+" originalCalls="+calls+" capabilitySequences="+caps+" runtimeFields="+fields+" inverseBodiesIdentical=true");}
}
