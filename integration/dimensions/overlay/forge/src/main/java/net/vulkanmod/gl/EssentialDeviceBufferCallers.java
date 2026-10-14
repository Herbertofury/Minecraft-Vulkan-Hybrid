package net.vulkanmod.gl;
import org.objectweb.asm.*;import org.objectweb.asm.tree.*;import java.lang.reflect.Modifier;import java.util.*;
/** Exact original typed buffer methods only.46 root image/framebuffer sites remain blocked.
 * No whole-device/static gate acceptance is granted by this partial method transport. */
public final class EssentialDeviceBufferCallers {
 public static final String BUFFER="net/vulkanmod/gl/NativeBufferObjectApi",VERTEX="net/vulkanmod/gl/NativeVertexArrayApi",RUNTIME="net/vulkanmod/gl/NativeEssentialBufferRuntime";
 private static final Map<String,String>PINS=Map.of("gg/essential/universal/render/UGpuDeviceImpl","ac1a55d26a3540c026566ebda4e832a554511068e7cf4224912a1bdc5e481de1","gg/essential/universal/render/UGpuBufferImpl","222189b2c3a6802e90412b5b7f015ae8eca96f79b12dd94dc6902a7d18a9790c","gg/essential/universal/render/UGpuDeviceImpl$mapBuffer$5","c53ce7ce4b4cf252be3afe5a02a4638d1f33adddb2075439c0e3013cb32eb82e","gg/essential/universal/render/UGpuDeviceImpl$OpenGL30$2","923c326104ff63442e55d8cfe6cd613bdb6d4d6b8a4cd56dfe45111c2b3d105c","gg/essential/universal/render/UGpuDeviceImpl$OpenGL31$2","1ebd0b108ed054b149666d615695539a95b56d7f1568cbc99cadb1f96a346c3a","gg/essential/universal/render/UGpuDeviceImpl$ARB_map_buffer_range$2","0dd9d72e6feb93c78e4cb7be3cabb2555ed1b2485830cb249ab67702195bcb5c","gg/essential/universal/render/UGpuDeviceImpl$tmpVao$2","a34c0260b08016c67d551b72f56c1657b3184b1c2cc1bfda01555fa9358a2edb");private static final Map<String,Integer>COUNTS=Map.of("gg/essential/universal/render/UGpuDeviceImpl",38,"gg/essential/universal/render/UGpuBufferImpl",1,"gg/essential/universal/render/UGpuDeviceImpl$mapBuffer$5",9,"gg/essential/universal/render/UGpuDeviceImpl$OpenGL30$2",1,"gg/essential/universal/render/UGpuDeviceImpl$OpenGL31$2",1,"gg/essential/universal/render/UGpuDeviceImpl$ARB_map_buffer_range$2",1,"gg/essential/universal/render/UGpuDeviceImpl$tmpVao$2",1);
 private static final Map<String,String>CAPS=Map.of("OpenGL30","supportsVertexArrayRouting","OpenGL31","supportsUniformAndCopyStorage","GL_ARB_map_buffer_range","supportsRangeMapping");
 private static final Set<String> ROOT_METHODS=Set.of("createBuffer","mapBuffer","withBufferBound"),OWNERS=Set.of("org/lwjgl/opengl/GL11","org/lwjgl/opengl/GL15","org/lwjgl/opengl/GL30");
 private static final Map<String,String>ABI=abi();private static Map<String,String>abi(){Map<String,String>s=new HashMap<>();for(Class<?>t:new Class<?>[]{NativeBufferObjectApi.class,NativeVertexArrayApi.class})for(var m:t.getDeclaredMethods())if(Modifier.isPublic(m.getModifiers())&&Modifier.isStatic(m.getModifiers())&&m.getName().startsWith("gl")){String key=m.getName()+Type.getMethodDescriptor(m);String owner=t==NativeBufferObjectApi.class?BUFFER:VERTEX;if(key.startsWith("glBindBuffer")||key.startsWith("glGetInteger"))owner=BUFFER;s.put(key,owner);}return Map.copyOf(s);}
 public static boolean target(String name){return PINS.containsKey(name.replace('.','/'));}
 public record Result(int calls,int capabilities,int retainedRootGLCalls){}
 public static Result apply(ClassNode n){if(!Objects.equals(PINS.get(n.name),ShaderCallerRemapper.canonicalDigest(n)))throw new IllegalStateException("Typed Essential buffer needs exact original body");int calls=0,caps=0,retained=0;boolean root=n.name.equals("gg/essential/universal/render/UGpuDeviceImpl");
  for(var m:n.methods)for(var i:m.instructions.toArray()){
   if(i instanceof MethodInsnNode c&&c.owner.startsWith("org/lwjgl/opengl/")){
    if(root&&!ROOT_METHODS.contains(m.name)){retained++;continue;}
    if(c.owner.equals("org/lwjgl/opengl/GL")&&c.name.equals("getCapabilities")){
     var next=c.getNext();while(next!=null&&next.getOpcode()<0)next=next.getNext();if(!(next instanceof FieldInsnNode f)||!f.owner.equals("org/lwjgl/opengl/GLCapabilities")||!f.desc.equals("Z")||!CAPS.containsKey(f.name))throw new IllegalStateException("Unknown buffer capability sequence");c.owner=RUNTIME;c.name=CAPS.get(f.name);c.desc="()Z";m.instructions.remove(f);caps++;
    }else{String replacement=ABI.get(c.name+c.desc);if(!OWNERS.contains(c.owner)||c.getOpcode()!=Opcodes.INVOKESTATIC||c.itf||replacement==null)throw new IllegalStateException("Unimplemented typed buffer invocation");c.owner=replacement;calls++;}
   }
  }
  if(calls+caps!=COUNTS.get(n.name)||retained!=(root?46:0))throw new IllegalStateException("Incomplete or overbroad typed buffer transport");return new Result(calls,caps,retained);
 }
}
