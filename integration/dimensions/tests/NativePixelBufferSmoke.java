package mvhpackbench;

import com.google.gson.*;
import net.minecraft.client.Minecraft;
import net.vulkanmod.gl.*;
import net.vulkanmod.vulkan.*;
import net.vulkanmod.vulkan.texture.*;
import org.lwjgl.system.MemoryUtil;
import org.objectweb.asm.*;import org.objectweb.asm.tree.*;
import java.nio.*;import java.nio.file.*;import java.lang.reflect.*;import java.security.*;import java.util.*;import java.net.*;import java.util.jar.*;
import static org.lwjgl.vulkan.VK10.*;

/** Exact original pixel methods, actual VkBuffer/image data and native lifetime. No FPS. */
final class NativePixelBufferSmoke {
 private static final JsonArray controls=new JsonArray();private static int invocations,rewrites;
 private static void require(String name,boolean ok){JsonObject row=new JsonObject();row.addProperty("control",name);row.addProperty("passed",ok);controls.add(row);if(!ok)throw new IllegalStateException("Native pixel control: "+name);}
 private static String hash(byte[] b)throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(b));}
 private static Object invoke(Class<?> type,String name,Object... args)throws Exception{
  Class<?>[] types=new Class<?>[args.length];for(int n=0;n<args.length;n++)types[n]=args[n] instanceof Integer?int.class:args[n] instanceof Long?long.class:args[n] instanceof IntBuffer?IntBuffer.class:ByteBuffer.class;
  try{invocations++;return type.getMethod(name,types).invoke(null,args);}catch(InvocationTargetException failure){throw new IllegalStateException("Original pixel method failed: "+name,failure.getCause());}
 }
 private static final class OriginalLoader extends URLClassLoader {
  private final JarFile jar;private final String name,pin;
  OriginalLoader(Path file,String name,String pin)throws Exception{super(new URL[]{file.toUri().toURL()},NativePixelBufferSmoke.class.getClassLoader());jar=new JarFile(file.toFile());this.name=name;this.pin=pin;}
  @Override protected Class<?> loadClass(String name,boolean resolve)throws ClassNotFoundException{if(!name.equals(this.name))return super.loadClass(name,resolve);synchronized(getClassLoadingLock(name)){Class<?> type=findLoadedClass(name);if(type==null)type=findClass(name);if(resolve)resolveClass(type);return type;}}
  @Override protected Class<?> findClass(String name)throws ClassNotFoundException{try{byte[] raw=jar.getInputStream(jar.getJarEntry(name.replace('.','/')+".class")).readAllBytes();require("original-private-class-pin-"+name,hash(raw).equals(pin));ClassNode node=new ClassNode();new ClassReader(raw).accept(node,0);new net.vulkanmod.mixin.MixinPlugin().preApply(name,node,"net.vulkanmod.mixin.compatibility.xaero.XaeroPixelCallerMixin",null);int calls=0;for(var method:node.methods)for(var instruction:method.instructions)if(instruction instanceof MethodInsnNode call&&call.owner.equals(XaeroPixelCallers.FACADE))calls++;require("original-pixel-callback-eight-sites-"+name,calls==8);rewrites+=calls;ClassWriter output=new ClassWriter(0);node.accept(output);return defineClass(name,output.toByteArray(),0,output.toByteArray().length);}catch(Exception e){throw new ClassNotFoundException(name,e);}}
  @Override public void close()throws java.io.IOException{try{jar.close();}finally{super.close();}}
 }
 private static void gameProof(Class<?> type)throws Exception {
  require("original-GAME-pixel-loader-"+type.getName(),type.getClassLoader().getClass().getName().equals("cpw.mods.modlauncher.TransformingClassLoader")&&net.minecraftforge.fml.loading.FMLLoader.getGameLayer().modules().contains(type.getModule()));
  Path export=Minecraft.getInstance().gameDirectory.toPath().resolve(".mixin.out/class/"+type.getName().replace('.','/')+".class");require("fresh-pixel-GAME-export-"+type.getName(),Files.isRegularFile(export)&&Files.getLastModifiedTime(export).toMillis()>=java.lang.management.ManagementFactory.getRuntimeMXBean().getStartTime());
  ClassNode node=new ClassNode();new ClassReader(Files.readAllBytes(export)).accept(node,0);int translated=0,old=0;for(var method:node.methods)for(var instruction:method.instructions)if(instruction instanceof MethodInsnNode call){if(call.owner.equals(XaeroPixelCallers.FACADE))translated++;if(call.owner.equals("org/lwjgl/opengl/GL15"))old++;}
  require("original-pixel-GAME-eight-call-sites-"+type.getName(),translated==8&&old==0);rewrites+=translated;
 }
 static void run(JsonArray cases)throws Exception{
  Path root=Minecraft.getInstance().gameDirectory.toPath();var initial=NativePixelBufferApi.snapshot();int oldTexture=GlTexture.getBoundTextureId(0x0DE1);var oldImage=VTextureSelector.getImage(0);
  String[][] fixtures={{"xaeroworldmap-forge-1.20.1-1.46.0.jar","xaero.map.graphics.PixelBuffers","0c52e8e472181cbd91eab5d7cbff5e8e8251dcf87de55c5a8697f0d5f46181d2"},{"xaerominimap-forge-1.20.1-26.5.0.jar","xaero.common.graphics.PixelBuffers","e3fb1593355182d3a55908078f01cfd650f2e4a46936b3be09ef9be2a660be8e"}};
  try{
   for(String[] fixture:fixtures)try(var loader=new OriginalLoader(root.resolve("mvh-pixel-original-inputs").resolve(fixture[0]),fixture[1],fixture[2])){
    boolean gameMode=Files.isRegularFile(root.resolve("mvh-pixel-game-originals.json"));
    Class<?> original=gameMode?Class.forName(fixture[1],true,NativePixelBufferSmoke.class.getClassLoader()):Class.forName(fixture[1],true,loader);
    if(gameMode)gameProof(original);
    String tag=fixture[1].contains("common")?"minimap":"worldmap";
    int unpack=(int)invoke(original,"glGenBuffers"),pack=(int)invoke(original,"glGenBuffers"),texture=GlTexture.genTextureId();VulkanImage image=null;
    try{
     invoke(original,"glBindBuffer",NativePixelBufferApi.UNPACK,unpack);invoke(original,"glBufferData",NativePixelBufferApi.UNPACK,16384L,0x88E0);
     require(tag+"-actual-vk-upload-buffer",NativePixelBufferApi.bufferHandle(NativePixelBufferApi.UNPACK)!=0);
     ByteBuffer mapped=(ByteBuffer)invoke(original,"glMapBuffer",NativePixelBufferApi.UNPACK,0x88B9,16384L,(ByteBuffer)null);require(tag+"-mapped-native-size-and-alignment",mapped.capacity()==16384&&(MemoryUtil.memAddress0(mapped)&63)==0);
     fill(mapped,0,true);require(tag+"-original-unmap",(boolean)invoke(original,"glUnmapBuffer",NativePixelBufferApi.UNPACK));
     image=VulkanImage.builder(8,8).setMipLevels(4).setFormat(VK_FORMAT_R8G8B8A8_UNORM).setUsage(VK_IMAGE_USAGE_SAMPLED_BIT|VK_IMAGE_USAGE_TRANSFER_DST_BIT|VK_IMAGE_USAGE_TRANSFER_SRC_BIT).setLinearFiltering(false).createVulkanImage();GlTexture.setVulkanImage(texture,image);GlTexture.bindTexture(texture);
     GlTexture.texSubImage2D(0x0DE1,0,0,0,8,8,0x80E1,0x8035,0L);invoke(original,"glBindBuffer",NativePixelBufferApi.UNPACK,0);
     check(image,cases,tag+"-original-packed-bgra-upload",false);
     invoke(original,"glBindBuffer",NativePixelBufferApi.PACK,pack);invoke(original,"glBufferData",NativePixelBufferApi.PACK,16384L,0x88E1);require(tag+"-actual-vk-readback-buffer",NativePixelBufferApi.bufferHandle(NativePixelBufferApi.PACK)!=0);
     GlTexture.getTexImage(0x0DE1,0,0x80E1,0x8035,64L);ByteBuffer downloaded=(ByteBuffer)invoke(original,"glMapBuffer",NativePixelBufferApi.PACK,0x88B8);verifyPacked(downloaded,64,tag+"-original-bgra-readback");require(tag+"-readback-unmap",(boolean)invoke(original,"glUnmapBuffer",NativePixelBufferApi.PACK));
     invoke(original,"glBindBuffer",NativePixelBufferApi.PACK,0);invoke(original,"glBindBuffer",NativePixelBufferApi.UNPACK,unpack);
     ByteBuffer reuse=(ByteBuffer)invoke(original,"glMapBuffer",NativePixelBufferApi.UNPACK,0x88B9,16384L,mapped);require(tag+"-old-mapped-view-reused",reuse==mapped);fill(reuse,0,false);invoke(original,"glUnmapBuffer",NativePixelBufferApi.UNPACK);
     long uploads=NativePixelBufferApi.snapshot().get("uploads");GlTexture.texSubImage2D(0x0DE1,0,0,0,8,8,0x1908,0x1401,0L);require(tag+"-direct-vk-buffer-to-image",NativePixelBufferApi.snapshot().get("uploads")==uploads+1);
     GlTexture.generateMipmap(0x0DE1);
     // Delete original names before the queued copy completes. Its allocation
     // must remain owned until the native frame fence retires the recorded use.
     invoke(original,"glDeleteBuffers",unpack);require(tag+"-recorded-upload-storage-retained",NativePixelBufferApi.snapshot().get("allocations")>initial.get("allocations"));
     check(image,cases,tag+"-direct-upload-and-original-deletion",false);
     invoke(original,"glBindBuffer",NativePixelBufferApi.PACK,pack);GlTexture.getTexImage(0x0DE1,0,0x1908,0x1401,32L);downloaded=(ByteBuffer)invoke(original,"glMapBuffer",NativePixelBufferApi.PACK,0x88B8);verifyRgba(downloaded,32,tag+"-direct-native-image-to-buffer");
     require(tag+"-double-map-refused",NativePixelBufferApi.glMapBuffer(NativePixelBufferApi.PACK,0x88B8)==null&&NativeSyncApi.glGetError()==1282);invoke(original,"glUnmapBuffer",NativePixelBufferApi.PACK);
     GlTexture.getTexImage(0x0DE1,1,0x1908,0x1401,64L);ByteBuffer mip=(ByteBuffer)invoke(original,"glMapBuffer",NativePixelBufferApi.PACK,0x88B8);checkMip(mip,64,cases,tag);invoke(original,"glUnmapBuffer",NativePixelBufferApi.PACK);
     GlTexture.getTexImage(0x0DE1,0,0x1908,0x1401,16380L);require(tag+"-out-of-range-readback-refused",NativeSyncApi.glGetError()==1282);
     IntBuffer deleted=ByteBuffer.allocateDirect(16).order(ByteOrder.nativeOrder()).asIntBuffer();deleted.put(0,12345);deleted.put(1,pack);deleted.put(2,0);deleted.put(3,12345);deleted.position(1).limit(3);invoke(original,"glDeleteBuffers",deleted);require(tag+"-original-delete-buffer-cursor-preserved",deleted.position()==1&&deleted.limit()==3&&deleted.get(0)==12345);
    }finally{NativePixelBufferApi.glBindBuffer(NativePixelBufferApi.UNPACK,0);NativePixelBufferApi.glBindBuffer(NativePixelBufferApi.PACK,0);NativePixelBufferApi.glDeleteBuffers(unpack);NativePixelBufferApi.glDeleteBuffers(pack);GlTexture.glDeleteTextures(texture);}
   }
   // Keep one real allocation for the mandatory device-idle cleanup proof.
   int live=NativePixelBufferApi.glGenBuffers();NativePixelBufferApi.glBindBuffer(NativePixelBufferApi.UNPACK,live);NativePixelBufferApi.glBufferData(NativePixelBufferApi.UNPACK,64,0x88E0);NativePixelBufferApi.glBindBuffer(NativePixelBufferApi.UNPACK,0);
   JsonObject report=new JsonObject();report.addProperty("accepted",true);report.addProperty("original_executable_component_only",true);report.addProperty("automatic_game_class_application",Files.isRegularFile(root.resolve("mvh-pixel-game-originals.json")));report.addProperty("original_callback_rewrites",rewrites);report.addProperty("original_method_invocations",invocations);report.addProperty("live_device_idle_cleanup_allocation",1);report.addProperty("fps_accepted",false);report.add("controls",controls);Files.writeString(root.resolve("mvh-pixel-buffer-proof.json"),new GsonBuilder().setPrettyPrinting().create().toJson(report));
  }finally{GlTexture.bindTexture(oldTexture);VTextureSelector.bindTexture(0,oldImage);}
 }
 private static void fill(ByteBuffer b,int offset,boolean argb){for(int y=0;y<8;y++)for(int x=0;x<8;x++){int p=offset+(y*8+x)*4,r=17+x*23,g=19+y*25,blue=43;if(argb){b.put(p,(byte)255);b.put(p+1,(byte)r);b.put(p+2,(byte)g);b.put(p+3,(byte)blue);}else{b.put(p,(byte)r);b.put(p+1,(byte)g);b.put(p+2,(byte)blue);b.put(p+3,(byte)255);}}}
 private static void verifyPacked(ByteBuffer b,int offset,String tag){int bad=0;for(int y=0;y<8;y++)for(int x=0;x<8;x++){int p=offset+(y*8+x)*4;if(Byte.toUnsignedInt(b.get(p))!=255||Byte.toUnsignedInt(b.get(p+1))!=17+x*23||Byte.toUnsignedInt(b.get(p+2))!=19+y*25||Byte.toUnsignedInt(b.get(p+3))!=43)bad++;}require(tag,bad==0);}
 private static void verifyRgba(ByteBuffer b,int offset,String tag){int bad=0;for(int y=0;y<8;y++)for(int x=0;x<8;x++){int p=offset+(y*8+x)*4;if(Byte.toUnsignedInt(b.get(p))!=17+x*23||Byte.toUnsignedInt(b.get(p+1))!=19+y*25||Byte.toUnsignedInt(b.get(p+2))!=43||Byte.toUnsignedInt(b.get(p+3))!=255)bad++;}require(tag,bad==0);}
 private static void checkMip(ByteBuffer data,int offset,JsonArray cases,String tag)throws Exception{int bad=0,max=0;byte[] raw=new byte[64];for(int y=0;y<4;y++)for(int x=0;x<4;x++){int[] expected={(int)Math.round(28.5+46*x),(int)Math.round(31.5+50*y),43,255};for(int c=0;c<4;c++){int actual=Byte.toUnsignedInt(data.get(offset+(y*4+x)*4+c)),error=Math.abs(actual-expected[c]);raw[(y*4+x)*4+c]=(byte)actual;max=Math.max(max,error);if(error>1)bad++;}}JsonObject row=new JsonObject();row.addProperty("case","original-pixel-"+tag+"-ordered-mipmap-upload");row.addProperty("matched",bad==0);row.addProperty("compared_pixels",16);row.addProperty("mismatched_pixels",bad);row.addProperty("max_channel_difference",max);row.addProperty("actual_rgba_sha256",hash(raw));cases.add(row);Path p=Minecraft.getInstance().gameDirectory.toPath().resolve("mvh-pixel-gpu-readback");Files.createDirectories(p);Files.write(p.resolve(tag+"-ordered-mipmap-upload.rgba"),raw);require(tag+"-ordered-mipmap-pixels",bad==0);}
 private static void check(VulkanImage image,JsonArray cases,String name,boolean ignored)throws Exception{ByteBuffer raw=MemoryUtil.memAlloc(256);try{ImageUtil.downloadTexture(image,MemoryUtil.memAddress(raw));int bad=0,max=0;for(int y=0;y<8;y++)for(int x=0;x<8;x++){int[] expected={17+x*23,19+y*25,43,255};for(int c=0;c<4;c++){int diff=Math.abs(Byte.toUnsignedInt(raw.get((y*8+x)*4+c))-expected[c]);max=Math.max(max,diff);if(diff!=0)bad++;}}byte[] bytes=new byte[256];raw.get(0,bytes);JsonObject item=new JsonObject();item.addProperty("case","original-pixel-"+name);item.addProperty("matched",bad==0);item.addProperty("compared_pixels",64);item.addProperty("mismatched_pixels",bad);item.addProperty("max_channel_difference",max);item.addProperty("actual_rgba_sha256",hash(bytes));cases.add(item);Path dir=Minecraft.getInstance().gameDirectory.toPath().resolve("mvh-pixel-gpu-readback");Files.createDirectories(dir);Files.write(dir.resolve(name+".rgba"),bytes);require(name+"-actual-gpu-pixels",bad==0);}finally{MemoryUtil.memFree(raw);}}
}
