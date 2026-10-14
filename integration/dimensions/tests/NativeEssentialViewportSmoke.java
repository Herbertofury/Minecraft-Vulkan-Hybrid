package mvhpackbench;
import com.google.gson.*;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.vulkanmod.gl.*;
import net.vulkanmod.vulkan.*;
import net.vulkanmod.vulkan.framebuffer.*;
import net.vulkanmod.vulkan.texture.*;
import org.joml.Matrix4f;
import org.lwjgl.system.MemoryUtil;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import java.lang.management.ManagementFactory;
import java.lang.reflect.*;
import java.nio.*;
import java.nio.file.*;
import static org.lwjgl.vulkan.VK10.*;

/** Actual original GAME viewport objects, asymmetric GPU gradient and interruptions. */
final class NativeEssentialViewportSmoke {
 private static final JsonArray controls=new JsonArray();private static int activations,captures;
 private static void require(String label,boolean ok){JsonObject row=new JsonObject();row.addProperty("control",label);row.addProperty("passed",ok);controls.add(row);if(!ok)throw new IllegalStateException("Viewport control: "+label);}
 private static Object invoke(Object target,String method)throws Exception{return OriginalEssentialClipProof.invoke(target.getClass().getMethod(method),target);}
 private static void activate(Class<?> type,int x,int y,int w,int h)throws Exception{invoke(type.getConstructor(int.class,int.class,int.class,int.class).newInstance(x,y,w,h),"activate");activations++;}
 private static void capture(Class<?> type,int x,int y,int w,int h)throws Exception{Object saved=invoke(type.getField("Companion").get(null),"active");String[] names={"getX","getY","getWidth","getHeight"};int[] expected={x,y,w,h};for(int n=0;n<4;n++)require("original-capture-"+type.getSimpleName()+"-"+captures+"-"+n,(int)invoke(saved,names[n])==expected[n]);captures++;}
 private static void forgeProof()throws Exception{
  for(String name:new String[]{"gg.essential.universal.render.ViewportState","gg.essential.universal.render.ViewportState$Companion","gg.essential.util.GlViewportState","gg.essential.util.GlViewportState$Companion"}){
   Class<?> type=Class.forName(name,false,NativeEssentialViewportSmoke.class.getClassLoader());require("game-owner-"+name,type.getClassLoader().getClass().getName().equals("cpw.mods.modlauncher.TransformingClassLoader")&&net.minecraftforge.fml.loading.FMLLoader.getGameLayer().modules().contains(type.getModule()));
   Path export=Minecraft.getInstance().gameDirectory.toPath().resolve(".mixin.out/class/"+name.replace('.','/')+".class");require("fresh-export-"+name,Files.isRegularFile(export)&&Files.getLastModifiedTime(export).toMillis()>=ManagementFactory.getRuntimeMXBean().getStartTime());ClassNode node=new ClassNode();new ClassReader(Files.readAllBytes(export)).accept(node,0);int translated=0,untranslated=0;for(var method:node.methods)for(var instruction:method.instructions)if(instruction instanceof MethodInsnNode call){if(call.owner.equals(EssentialViewportCallers.FACADE))translated++;if(call.owner.equals("org/lwjgl/opengl/GL11"))untranslated++;}require("exact-forge-viewport-sites-"+name,translated==1&&untranslated==0);
  }
 }
 static void run(JsonArray cases)throws Exception{
  forgeProof();Renderer renderer=Renderer.getInstance();RenderPass previous=renderer.getBoundRenderPass();require("owned-native-pass",previous!=null&&Renderer.isRecording());NativeStencilSmoke.State state=new NativeStencilSmoke.State();boolean clipped=Renderer.isScissorEnabled();int[] box=new int[4];for(int n=0;n<4;n++)box[n]=Renderer.getScissorBox(n);Matrix4f projection=new Matrix4f(RenderSystem.getProjectionMatrix());var shader=RenderSystem.getShader();float[] oldColor=RenderSystem.getShaderColor().clone();var model=RenderSystem.getModelViewStack();model.pushPose();model.setIdentity();RenderSystem.applyModelViewMatrix();VulkanImage image=null;Framebuffer fb=null;
  try{
   image=VulkanImage.builder(32,32).setFormat(VK_FORMAT_R8G8B8A8_UNORM).setUsage(VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT|VK_IMAGE_USAGE_TRANSFER_SRC_BIT|VK_IMAGE_USAGE_SAMPLED_BIT).setLinearFiltering(false).createVulkanImage();fb=Framebuffer.builder(image,null).build();require("viewport-native-offscreen-pass",renderer.beginRendering(RenderPass.builder(fb).build(),fb));RenderSystem.setProjectionMatrix(new Matrix4f().setOrtho(0,32,0,32,-1,1),VertexSorting.ORTHOGRAPHIC_Z);RenderSystem.setShader(GameRenderer::getPositionColorShader);RenderSystem.setShaderColor(1,1,1,1);RenderSystem.disableBlend();RenderSystem.disableCull();RenderSystem.disableDepthTest();RenderSystem.depthMask(false);VRenderSystem.disableStencilTest();VRenderSystem.colorMask(true,true,true,true);
   for(String name:new String[]{"gg.essential.universal.render.ViewportState","gg.essential.util.GlViewportState"}){
    Class<?> original=Class.forName(name,true,NativeEssentialViewportSmoke.class.getClassLoader());String tag=name.contains("universal")?"universal":"legacy";Renderer.setScissorEnabled(false);
    activate(original,2,7,24,20);capture(original,2,7,24,20);clear();gradient();check(image,cases,tag+"-asymmetric-gradient",2,7,24,20,false);capture(original,2,7,24,20);
    // No viewport reapplication across either readback or asynchronous submission.
    gradient();check(image,cases,tag+"-readback-resumes-gradient",2,7,24,20,false);capture(original,2,7,24,20);renderer.flushForSync();gradient();check(image,cases,tag+"-async-resumes-gradient",2,7,24,20,false);capture(original,2,7,24,20);
    activate(original,-5,-7,18,21);capture(original,-5,-7,18,21);clear();gradient();check(image,cases,tag+"-negative-origin",-5,-7,18,21,false);
    activate(original,2,7,24,20);Renderer.setScissor(3,9,11,5);Renderer.setScissorEnabled(true);clear();gradient();check(image,cases,tag+"-asymmetric-scissor",2,7,24,20,true);capture(original,2,7,24,20);Renderer.setScissorEnabled(false);
    activate(original,3,5,0,20);capture(original,3,5,0,20);clear();gradient();check(image,cases,tag+"-zero-width-clear-still-applies",3,5,0,20,false);capture(original,3,5,0,20);renderer.flushForSync();capture(original,3,5,0,20);
    activate(original,3,5,20,0);capture(original,3,5,20,0);clear();gradient();check(image,cases,tag+"-zero-height-clear-still-applies",3,5,20,0,false);capture(original,3,5,20,0);
    activate(original,2,7,24,20);clear();gradient();check(image,cases,tag+"-post-zero-gradient",2,7,24,20,false);
    activate(original,40,50,24,20);capture(original,40,50,24,20);clear();gradient();check(image,cases,tag+"-outside-framebuffer",40,50,24,20,false);
    var limits=net.vulkanmod.vulkan.device.DeviceManager.deviceProperties.limits();int maxW=limits.maxViewportDimensions(0),maxH=limits.maxViewportDimensions(1);activate(original,0,0,Integer.MAX_VALUE,Integer.MAX_VALUE);capture(original,0,0,maxW,maxH);clear();gradient();check(image,cases,tag+"-actual-device-extent-clamp",0,0,maxW,maxH,false);
    activate(original,2,7,24,20);activate(original,2,7,-1,20);require("negative-width-error-preserves-state",NativeSyncApi.glGetError()==1281&&NativeSyncApi.glGetError()==0);capture(original,2,7,24,20);activate(original,2,7,24,-1);require("negative-height-error-preserves-state",NativeSyncApi.glGetError()==1281);capture(original,2,7,24,20);
    IntBuffer query=ByteBuffer.allocateDirect(32).order(ByteOrder.nativeOrder()).asIntBuffer();for(int n=0;n<8;n++)query.put(n,12345);query.position(2).limit(7);NativeViewportApi.glGetIntegerv(2978,query);require("query-cursor-limit-sentinels",query.position()==2&&query.limit()==7&&query.get(0)==12345&&query.get(1)==12345&&query.get(6)==12345);for(int n=0;n<4;n++)require("query-offset-values-"+n,query.get(n+2)==new int[]{2,7,24,20}[n]);
   }
   JsonObject report=new JsonObject();report.addProperty("accepted",true);report.addProperty("automatic_forge_original_game_methods_executed",true);report.addProperty("exact_transformed_classes",4);report.addProperty("rewritten_sites",4);report.addProperty("original_activations",activations);report.addProperty("original_captures",captures);report.addProperty("pixel_cases",20);report.addProperty("full_essential_features_proven",false);report.addProperty("fps_accepted",false);report.add("controls",controls);Files.writeString(Minecraft.getInstance().gameDirectory.toPath().resolve("mvh-essential-viewport-proof.json"),new GsonBuilder().setPrettyPrinting().create().toJson(report));
  }finally{renderer.endRenderPass();renderer.beginRendering(previous,previous.getFramebuffer());Renderer.setInvertedViewport(0,0,previous.getFramebuffer().getWidth(),previous.getFramebuffer().getHeight());Renderer.setScissorEnabled(clipped);Renderer.setScissor(box[0],box[1],box[2],box[3]);state.restore();RenderSystem.setProjectionMatrix(projection,VertexSorting.ORTHOGRAPHIC_Z);model.popPose();RenderSystem.applyModelViewMatrix();RenderSystem.setShader(()->shader);RenderSystem.setShaderColor(oldColor[0],oldColor[1],oldColor[2],oldColor[3]);if(fb!=null)fb.cleanUp();else if(image!=null)image.free();}
 }
 private static void clear(){VRenderSystem.setClearColor(0,0,1,1);Renderer.clearAttachments(16384);}
 private static void gradient(){BufferBuilder v=Tesselator.getInstance().getBuilder();v.begin(VertexFormat.Mode.QUADS,DefaultVertexFormat.POSITION_COLOR);v.vertex(0,0,0).color(0,0,33,255).endVertex();v.vertex(32,0,0).color(255,0,33,255).endVertex();v.vertex(32,32,0).color(255,255,33,255).endVertex();v.vertex(0,32,0).color(0,255,33,255).endVertex();BufferUploader.drawWithShader(v.end());}
 private static void check(VulkanImage image,JsonArray cases,String label,int left,int bottom,int width,int height,boolean clipped)throws Exception{
  ByteBuffer pixels=MemoryUtil.memAlloc(4096);int[] viewport=new int[4];for(int n=0;n<4;n++)viewport[n]=Renderer.getOpenGlViewport(n);
  try{ImageUtil.downloadTexture(image,MemoryUtil.memAddress(pixels));for(int n=0;n<4;n++)require("readback-logical-viewport-"+label+"-"+n,Renderer.getOpenGlViewport(n)==viewport[n]);require("readback-logical-scissor-"+label,Renderer.isScissorEnabled()==clipped);int bad=0,max=0;for(int y=0;y<32;y++)for(int x=0;x<32;x++){double gx=x+.5,gy=32-y-.5;boolean ink=width>0&&height>0&&gx>=left&&gx<(long)left+width&&gy>=bottom&&gy<(long)bottom+height&&(!clipped||gx>=3&&gx<14&&gy>=9&&gy<14);int[] expected=ink?new int[]{(int)Math.round(255*(gx-left)/width),(int)Math.round(255*(gy-bottom)/height),33,255}:new int[]{0,0,255,255};boolean mismatch=false;for(int c=0;c<4;c++){int diff=Math.abs(Byte.toUnsignedInt(pixels.get((y*32+x)*4+c))-expected[c]);max=Math.max(max,diff);if(diff>1)mismatch=true;}if(mismatch)bad++;}byte[] raw=new byte[4096];pixels.get(0,raw);Path p=Minecraft.getInstance().gameDirectory.toPath().resolve("mvh-viewport-gpu-readback");Files.createDirectories(p);Files.write(p.resolve(label+".rgba"),raw);JsonObject item=new JsonObject();item.addProperty("case","original-viewport-"+label);item.addProperty("matched",bad==0);item.addProperty("compared_pixels",1024);item.addProperty("mismatched_pixels",bad);item.addProperty("max_channel_difference",max);item.addProperty("channel_tolerance",1);item.addProperty("actual_rgba_sha256",OriginalEssentialClipProof.hash(raw));cases.add(item);require("asymmetric-viewport-pixels-"+label,bad==0);
  }finally{MemoryUtil.memFree(pixels);}
 }
}
