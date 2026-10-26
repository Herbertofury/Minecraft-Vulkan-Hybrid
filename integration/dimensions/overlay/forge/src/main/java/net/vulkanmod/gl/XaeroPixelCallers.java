package net.vulkanmod.gl;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;
import java.util.Map;
import java.util.Set;
/** Exact original map/minimap pixel buffer bodies only; no generic GL15 waiver. */
public final class XaeroPixelCallers {
 public static final String FACADE="net/vulkanmod/gl/NativePixelBufferApi";
 private static final Map<String,String> PINS=Map.of(
  "xaero/map/graphics/PixelBuffers","c0c189ec431da70adba557a2e708868140e072f3ea5ed12bb677699fbeb0e481",
  "xaero/common/graphics/PixelBuffers","7ad5cfb9696b2190f70e401cf5b0f108d4948f00d35c89272d6f01757014f479");
 private static final Set<String> ABI=Set.of("glGenBuffers()I","glBindBuffer(II)V","glBufferData(IJI)V","glMapBuffer(II)Ljava/nio/ByteBuffer;","glMapBuffer(IIJLjava/nio/ByteBuffer;)Ljava/nio/ByteBuffer;","glUnmapBuffer(I)Z","glDeleteBuffers(I)V","glDeleteBuffers(Ljava/nio/IntBuffer;)V");
 private XaeroPixelCallers(){}
 public static boolean target(String name){return PINS.containsKey(name.replace('.','/'));}
 public static int apply(ClassNode node){
  String pin=PINS.get(node.name);if(pin==null||!pin.equals(ShaderCallerRemapper.canonicalDigest(node)))throw new IllegalStateException("Pixel caller requires reviewed original body: "+node.name);
  int changed=0;for(var method:node.methods)for(var instruction:method.instructions)if(instruction instanceof MethodInsnNode call&&call.getOpcode()==Opcodes.INVOKESTATIC&&!call.itf&&call.owner.equals("org/lwjgl/opengl/GL15")&&ABI.contains(call.name+call.desc)){call.owner=FACADE;changed++;}
  if(changed!=8)throw new IllegalStateException("Original pixel call transport incomplete: "+changed);return changed;
 }
}
