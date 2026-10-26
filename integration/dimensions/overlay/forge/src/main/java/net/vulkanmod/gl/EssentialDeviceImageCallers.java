package net.vulkanmod.gl;
import org.objectweb.asm.*;import org.objectweb.asm.tree.*;import java.util.*;
/** Exact original texture creation/lifetime/limit paths; full framebuffer/provider stays blocked. */
public final class EssentialDeviceImageCallers {
 public static final String IMAGE="net/vulkanmod/gl/NativeEssentialImageApi";private static final Map<String,String>PINS=Map.of("gg/essential/universal/render/UGpuDeviceImpl","ac1a55d26a3540c026566ebda4e832a554511068e7cf4224912a1bdc5e481de1","gg/essential/universal/render/UGpuTextureImpl","7655b4d3b0031a58847c5309e48087f40bad073e979efbb7520c5bdbac461e4b","gg/essential/universal/render/UGpuDeviceImpl$info$1$limits$1","61eb54b661554dbc62e0aab470104210e6e60beb91ae882461adf8a5c2d465ef");
 public static boolean target(String n){return PINS.containsKey(n.replace('.','/'));}
 public record Result(int imageCalls,int addedMipContext,int retainedRootGLCalls){}
 public static Result apply(ClassNode n){if(!Objects.equals(PINS.get(n.name),ShaderCallerRemapper.canonicalDigest(n)))throw new IllegalStateException("Typed images need exact original body");boolean root=n.name.equals("gg/essential/universal/render/UGpuDeviceImpl");if(root)EssentialDeviceBufferCallers.apply(n);int calls=0,contexts=0;
  for(var m:n.methods)for(var i:m.instructions.toArray())if(i instanceof MethodInsnNode c){
   if(root&&m.name.equals("createGlTexture")&&c.owner.equals("com/mojang/blaze3d/platform/GlStateManager")&&c.name.equals("_genTexture")&&c.desc.equals("()I")){c.owner=IMAGE;c.name="glGenTextures";calls++;}
   else if(root&&m.name.equals("createGlTexture")&&c.owner.equals("gg/essential/universal/UGraphics")&&c.name.equals("configureTexture")&&c.desc.equals("(ILjava/lang/Runnable;)V")){InsnList extra=new InsnList();extra.add(new VarInsnNode(Opcodes.ILOAD,6));extra.add(new VarInsnNode(Opcodes.ALOAD,2));extra.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL,"gg/essential/universal/render/UGpuTexture$Usage","getBits","()I",false));m.instructions.insertBefore(c,extra);c.owner=IMAGE;c.desc="(ILjava/lang/Runnable;II)V";calls++;contexts++;}
   else if(root&&m.name.equals("createGlTexture$lambda$0")&&c.owner.equals("org/lwjgl/opengl/GL11")&&c.name.equals("glTexImage2D")&&c.desc.equals("(IIIIIIIILjava/nio/ByteBuffer;)V")){c.owner=IMAGE;calls++;}
   else if(!root&&n.name.endsWith("/UGpuTextureImpl")&&m.name.equals("free")&&c.owner.equals("com/mojang/blaze3d/systems/RenderSystem")&&c.name.equals("deleteTexture")&&c.desc.equals("(I)V")){c.owner=IMAGE;c.name="glDeleteTextures";calls++;}
   else if(!root&&n.name.endsWith("$limits$1")&&m.name.equals("queryMaxTextureSize")&&c.owner.equals("org/lwjgl/opengl/GL11")&&Set.of("glGetInteger(I)I","glGetTexLevelParameteri(III)I","glTexImage2D(IIIIIIIILjava/nio/ByteBuffer;)V").contains(c.name+c.desc)){c.owner=IMAGE;calls++;}
  }
  int remaining=0;for(var m:n.methods)for(var i:m.instructions)if(i instanceof MethodInsnNode c&&c.owner.startsWith("org/lwjgl/opengl/"))remaining++;
  if(calls!=(root?3:n.name.endsWith("/UGpuTextureImpl")?1:3)||contexts!=(root?1:0)||remaining!=(root?45:0))throw new IllegalStateException("Incomplete or overbroad typed image transport");return new Result(calls,contexts,remaining);
 }
}
