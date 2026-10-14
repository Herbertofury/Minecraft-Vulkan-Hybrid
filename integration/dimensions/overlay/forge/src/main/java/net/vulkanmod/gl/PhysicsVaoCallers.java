package net.vulkanmod.gl;
import org.objectweb.asm.*;import org.objectweb.asm.tree.*;import java.lang.reflect.Modifier;import java.util.*;
/** Pinned original outer VAO/state/format algorithms; explicit native queries and calls only. */
public final class PhysicsVaoCallers {
 public static final String FACADE="net/vulkanmod/gl/NativeVertexArrayApi",RUNTIME="net/vulkanmod/gl/NativePhysicsVaoRuntime";
 private static final Map<String,String>PINS=Map.of("net/diebuddies/opengl/VAO","e228130d1d7d8ce27a3cd94f86fa75564fd485f12342cc907e0824f382b2ce15","net/diebuddies/opengl/VertexFormat","eb76deae65731f0d9da5c8449960723e084b4f658eac1194ac9f808ea230ed77","net/diebuddies/opengl/StateTracker","9694a5faeb446b7e7ed32c8e3d4432a173a6f8dde9616288a9a20a40b1f77693");
 public static final Map<String,String> CAPABILITIES=Map.of("GL_ARB_multi_bind","hasArbMultiBinding","OpenGL44","hasCoreMultiBinding","GL_ARB_vertex_attrib_binding","hasArbAttributeBinding","OpenGL43","hasCoreAttributeBinding","OpenGL33","hasCoreInstances","GL_ARB_instanced_arrays","hasArbInstances");
 private static final Set<String>ABI=abi();private static Set<String>abi(){Set<String>s=new HashSet<>();for(var m:NativeVertexArrayApi.class.getDeclaredMethods())if(Modifier.isPublic(m.getModifiers())&&Modifier.isStatic(m.getModifiers())&&m.getName().startsWith("gl"))s.add(m.getName()+Type.getMethodDescriptor(m));return Set.copyOf(s);}
 public static boolean target(String n){return PINS.containsKey(n.replace('.','/'));}
 public record Result(int calls,int capabilities,int runtimeFields){}
 public static Result apply(ClassNode n){if(!Objects.equals(PINS.get(n.name),ShaderCallerRemapper.canonicalDigest(n)))throw new IllegalStateException("Outer VAO needs exact original body");int calls=0,caps=0,fields=0;
  for(var m:n.methods)for(var i:m.instructions.toArray()){
   if(i instanceof MethodInsnNode c&&c.owner.equals("org/lwjgl/opengl/GL")&&c.name.equals("getCapabilities")){
    var next=c.getNext();while(next!=null&&next.getOpcode()<0)next=next.getNext();if(!(next instanceof FieldInsnNode f)||!f.owner.equals("org/lwjgl/opengl/GLCapabilities")||!f.desc.equals("Z")||!CAPABILITIES.containsKey(f.name))throw new IllegalStateException("Unknown outer VAO capability sequence");c.owner=RUNTIME;c.name=CAPABILITIES.get(f.name);c.desc="()Z";m.instructions.remove(f);caps++;
   }else if(i instanceof MethodInsnNode c&&Set.of("org/lwjgl/opengl/GL32C","org/lwjgl/opengl/GL33C","org/lwjgl/opengl/ARBInstancedArrays").contains(c.owner)){
    if(c.getOpcode()!=Opcodes.INVOKESTATIC||c.itf||!ABI.contains(c.name+c.desc))throw new IllegalStateException("Unimplemented original outer VAO invocation");c.owner=FACADE;calls++;
   }else if(i instanceof FieldInsnNode f&&f.getOpcode()==Opcodes.GETSTATIC&&f.owner.equals("net/diebuddies/physics/StarterClient")){
    if(f.name.equals("memoryStack")&&f.desc.equals("Lorg/lwjgl/system/MemoryStack;"))m.instructions.set(f,new MethodInsnNode(Opcodes.INVOKESTATIC,RUNTIME,"memoryStack","()Lorg/lwjgl/system/MemoryStack;",false));else if(f.name.equals("logger")&&f.desc.equals("Lorg/apache/logging/log4j/Logger;"))f.owner=RUNTIME;else throw new IllegalStateException("Unknown original runtime field");fields++;
   }
  }
  int expected=n.name.endsWith("/VAO")?22:n.name.endsWith("/VertexFormat")?3:1;if(calls!=expected||caps!=(n.name.endsWith("/VAO")?10:0)||fields!=(n.name.endsWith("/VAO")?24:0))throw new IllegalStateException("Incomplete outer VAO transport");return new Result(calls,caps,fields);
 }
}