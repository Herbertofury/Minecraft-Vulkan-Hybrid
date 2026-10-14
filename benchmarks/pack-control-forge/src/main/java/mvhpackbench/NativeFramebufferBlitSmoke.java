package mvhpackbench;

import com.google.gson.*;
import net.vulkanmod.gl.*;
import net.vulkanmod.vulkan.*;
import net.vulkanmod.vulkan.texture.*;
import org.lwjgl.opengl.*;
import org.lwjgl.system.*;
import org.lwjgl.vulkan.*;
import java.nio.ByteBuffer;
import static org.lwjgl.vulkan.VK10.*;

/** Actual registered LWJGL calls and read-back pixels; excluded from FPS runs. */
final class NativeFramebufferBlitSmoke {
    private static final int[][] COLORS={{255,0,0},{0,255,0},{255,255,0},{255,0,255}};
    static final JsonArray cases=new JsonArray();
    static final JsonArray dispatches=new JsonArray();
    static JsonArray run() {
        if(!Renderer.isRecording()||Renderer.getInstance().getBoundRenderPass()==null)throw new IllegalStateException("Native GPU pixel diagnostic requires active command recording before allocations or commands");
        Renderer renderer=Renderer.getInstance();int oldDraw=GlFramebuffer.getBoundId(),oldRead=GlFramebuffer.getReadId();
        boolean scissor=Renderer.isScissorEnabled(),cull=VRenderSystem.cull;int[] box=new int[4];for(int i=0;i<4;i++)box[i]=Renderer.getScissorBox(i);
        int source=0,target=0,sourceTexture=0,targetTexture=0;VulkanImage sourceImage=null,targetImage=null;
        try {
            phase("before_native_image_allocation");
            sourceImage=VulkanImage.builder(16,16).setFormat(VK_FORMAT_R8G8B8A8_UNORM).addUsage(VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT).setLinearFiltering(false).createVulkanImage();
            targetImage=VulkanImage.builder(32,16).setFormat(VK_FORMAT_R8G8B8A8_UNORM).addUsage(VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT).setLinearFiltering(false).createVulkanImage();
            sourceTexture=GlTexture.genTextureId();targetTexture=GlTexture.genTextureId();GlTexture.bindIdToImage(sourceTexture,sourceImage);GlTexture.bindIdToImage(targetTexture,targetImage);
            source=gl("GL30","glGenFramebuffers");target=gl("GL30","glGenFramebuffers");
            gl("GL30","glBindFramebuffer",GL30.GL_FRAMEBUFFER,source);gl("GL30","glFramebufferTexture2D",GL30.GL_FRAMEBUFFER,GL30.GL_COLOR_ATTACHMENT0,GL11.GL_TEXTURE_2D,sourceTexture,0);
            gl("GL30","glBindFramebuffer",GL30.GL_FRAMEBUFFER,source);Renderer.setScissorEnabled(false);
            for(int i=0;i<4;i++)clear((i%2)*8,(i/2)*8,8,8,COLORS[i]);
            gl("GL30","glBindFramebuffer",GL30.GL_DRAW_FRAMEBUFFER,target);gl("GL30","glFramebufferTexture2D",GL30.GL_DRAW_FRAMEBUFFER,GL30.GL_COLOR_ATTACHMENT0,GL11.GL_TEXTURE_2D,targetTexture,0);
            gl("GL30","glBindFramebuffer",GL30.GL_DRAW_FRAMEBUFFER,target);
            if(gl("GL11","glGetInteger",GL30.GL_READ_FRAMEBUFFER_BINDING)!=source||gl("GL11","glGetInteger",GL30.GL_DRAW_FRAMEBUFFER_BINDING)!=target)
                throw new IllegalStateException("Actual native registry conflates READ/DRAW framebuffer bindings");
            gl("GL30","glBlitFramebuffer",0,0,16,16,0,0,32,16,GL11.GL_COLOR_BUFFER_BIT,GL11.GL_NEAREST);
            check(cases,targetImage,"native_registry_nearest_scaled",false,false,false,0);
            gl("GL30","glBlitFramebuffer",16,0,0,16,0,0,32,16,GL11.GL_COLOR_BUFFER_BIT,GL11.GL_NEAREST);
            check(cases,targetImage,"native_registry_reversed_source_x",true,false,false,0);
            clear(0,0,32,16,new int[]{0,0,255});Renderer.setScissor(8,4,16,8);Renderer.setScissorEnabled(true);
            gl("GL30","glBlitFramebuffer",0,0,16,16,0,0,32,16,GL11.GL_COLOR_BUFFER_BIT,GL11.GL_NEAREST);
            check(cases,targetImage,"native_registry_scissor_preserves_outside",false,false,true,0);
            Renderer.setScissorEnabled(false);gl("GL30","glBindFramebuffer",GL30.GL_FRAMEBUFFER,oldDraw);
            int namedRead=gl("GL11","glGetInteger",GL30.GL_READ_FRAMEBUFFER_BINDING),namedDraw=gl("GL11","glGetInteger",GL30.GL_DRAW_FRAMEBUFFER_BINDING);
            gl("GL45","glBlitNamedFramebuffer",source,target,0,16,16,0,0,0,32,16,GL11.GL_COLOR_BUFFER_BIT,GL11.GL_NEAREST);
            if(gl("GL11","glGetInteger",GL30.GL_READ_FRAMEBUFFER_BINDING)!=namedRead||gl("GL11","glGetInteger",GL30.GL_DRAW_FRAMEBUFFER_BINDING)!=namedDraw)
                throw new IllegalStateException("Named native blit changed framebuffer bindings");
            check(cases,targetImage,"native_registry_named_reversed_source_y_bindings_preserved",false,true,false,0);
            gl("GL30","glBindFramebuffer",GL30.GL_READ_FRAMEBUFFER,source);gl("GL30","glBindFramebuffer",GL30.GL_DRAW_FRAMEBUFFER,target);
            gl("EXTFramebufferBlit","glBlitFramebufferEXT",0,0,16,16,0,0,32,16,GL11.GL_COLOR_BUFFER_BIT,GL11.GL_NEAREST);
            check(cases,targetImage,"native_registry_ext_entrypoint",false,false,false,0);
            gl("GL30","glBlitFramebuffer",0,0,16,16,0,0,32,16,GL11.GL_COLOR_BUFFER_BIT,GL11.GL_LINEAR);
            check(cases,targetImage,"native_registry_linear_scaled",false,false,false,1);
            return cases;
        } finally {
            gl("GL30","glBindFramebuffer",GL30.GL_FRAMEBUFFER,oldDraw);gl("GL30","glBindFramebuffer",GL30.GL_READ_FRAMEBUFFER,oldRead);
            Renderer.setScissor(box[0],box[1],box[2],box[3]);Renderer.setScissorEnabled(scissor);if(cull)VRenderSystem.enableCull();else VRenderSystem.disableCull();
            if(source!=0)gl("GL30","glDeleteFramebuffers",source);if(target!=0)gl("GL30","glDeleteFramebuffers",target);
            if(sourceTexture!=0)GlTexture.glDeleteTextures(sourceTexture);else if(sourceImage!=null)sourceImage.free();
            if(targetTexture!=0)GlTexture.glDeleteTextures(targetTexture);else if(targetImage!=null)targetImage.free();renderer.invalidateRenderState();
        }
    }
    private static void phase(String name) {
        try{var q=new JsonObject();q.addProperty("phase",name);q.addProperty("recording",Renderer.isRecording());q.addProperty("has_render_pass",Renderer.getInstance().getBoundRenderPass()!=null);q.add("completed_pixel_cases",cases.deepCopy());q.add("dispatches",dispatches.deepCopy());q.addProperty("main_framebuffer_id",net.minecraft.client.Minecraft.getInstance().getMainRenderTarget().frameBufferId);java.nio.file.Files.writeString(net.minecraft.client.Minecraft.getInstance().gameDirectory.toPath().resolve("mvh-create-framebuffer-diag-stage.json"),q.toString());}catch(java.io.IOException failure){throw new java.io.UncheckedIOException(failure);}
    }
    private static int gl(String owner,String method,int...args) {
        try {
            phase("before_"+owner+"_"+method);
            if(owner.equals("GL45")||owner.equals("EXTFramebufferBlit")) {
                Object caps=Class.forName("org.lwjgl.opengl.GL").getMethod("getCapabilities").invoke(null);
                long advertised=caps.getClass().getField(method).getLong(caps);
                JsonObject dispatch=new JsonObject();dispatch.addProperty("entrypoint",method);dispatch.addProperty("advertised_capability_pointer_available",advertised!=0);
                if(advertised==0) {
                    // This diagnostic exercises a bounded callback, not advertised GL 4.5/extension support.
                    long exported=((Number)Class.forName("net.vulkanmod.compat.opengl.GlFunctionRegistry").getMethod("address",String.class).invoke(null,method)).longValue();
                    if(exported==0)throw new IllegalStateException("Missing registered framebuffer callback: "+method);
                    dispatch.addProperty("dispatch","bounded_registry_callback_via_official_LWJGL_JNI");dispatches.add(dispatch);phase("before_bounded_callback_"+method);
                    Class<?>[] signature=new Class<?>[args.length+1];java.util.Arrays.fill(signature,int.class);signature[args.length]=long.class;
                    Object[] values=new Object[args.length+1];for(int i=0;i<args.length;i++)values[i]=args[i];values[args.length]=exported;
                    Class.forName("org.lwjgl.system.JNI").getMethod("callV",signature).invoke(null,values);phase("after_bounded_callback_"+method);return 0;
                }
                dispatch.addProperty("dispatch","advertised_LWJGL_entrypoint");dispatches.add(dispatch);
            }
            Class<?>[] types=new Class<?>[args.length];java.util.Arrays.fill(types,int.class);
            Object[] boxed=new Object[args.length];for(int i=0;i<args.length;i++)boxed[i]=args[i];
            Object result=Class.forName("org.lwjgl.opengl."+owner).getMethod(method,types).invoke(null,boxed);
            return result==null?0:((Number)result).intValue();
        }catch(ReflectiveOperationException failure){throw new IllegalStateException("Actual native registry diagnostic call failed: "+method,failure);}
    }
    static void captureOriginalOffscreen(net.minecraft.client.Minecraft mc)throws Exception {
        Object target=Class.forName("net.createmod.catnip.gui.UIRenderHelper").getField("framebuffer").get(null);
        int id=((com.mojang.blaze3d.pipeline.RenderTarget)target).frameBufferId;
        VulkanImage image=GlFramebuffer.getFramebuffer(id).getFramebuffer().getColorAttachment();
        ByteBuffer pixels=MemoryUtil.memAlloc(Math.multiplyExact(Math.multiplyExact(image.width,image.height),4));
        try(com.mojang.blaze3d.platform.NativeImage copy=new com.mojang.blaze3d.platform.NativeImage(image.width,image.height,false)) {
            phase("before_native_image_readback");
            ImageUtil.downloadTexture(image,MemoryUtil.memAddress(pixels));
            for(int y=0;y<image.height;y++)for(int x=0;x<image.width;x++) {
                int p=(y*image.width+x)*4;int red=Byte.toUnsignedInt(pixels.get(p)),green=Byte.toUnsignedInt(pixels.get(p+1)),blue=Byte.toUnsignedInt(pixels.get(p+2)),alpha=Byte.toUnsignedInt(pixels.get(p+3));
                if(image.format==VK_FORMAT_B8G8R8A8_UNORM){int t=red;red=blue;blue=t;}
                copy.setPixelRGBA(x,y,red|(green<<8)|(blue<<16)|(alpha<<24));
            }
            copy.writeToFile(mc.gameDirectory.toPath().resolve("screenshots/mvh-create-original_registered_create_config_offscreen_ui.png"));
        }finally{MemoryUtil.memFree(pixels);}
    }
    private static void clear(int x,int y,int width,int height,int[] color) {
        try(MemoryStack stack=MemoryStack.stackPush()) {
            var attachment=VkClearAttachment.calloc(1,stack).aspectMask(VK_IMAGE_ASPECT_COLOR_BIT).colorAttachment(0);
            for(int i=0;i<3;i++)attachment.clearValue().color().float32(i,color[i]/255f);attachment.clearValue().color().float32(3,1);
            var rect=VkClearRect.calloc(1,stack).baseArrayLayer(0).layerCount(1);rect.rect().offset().set(x,y);rect.rect().extent().set(width,height);
            phase("before_native_clear_rect");
            vkCmdClearAttachments(Renderer.getCommandBuffer(),attachment,rect);
        }
    }
    private static void check(JsonArray cases,VulkanImage image,String name,boolean flipX,boolean flipY,boolean clip,int tolerance) {
        ByteBuffer pixels=MemoryUtil.memAlloc(32*16*4);int mismatches=0,maxError=0;
        try {
            phase("before_native_image_readback");
            ImageUtil.downloadTexture(image,MemoryUtil.memAddress(pixels));
            for(int y=0;y<16;y++)for(int x=0;x<32;x++) {
                boolean outside=clip&&(x<8||x>=24||y<4||y>=12);int[] expected;
                if(outside)expected=new int[]{0,0,255};
                else {
                    int row=(flipY?15-y:y)/8;
                    if(tolerance==0){int column=(flipX?31-x:x)/16;expected=COLORS[row*2+column];}
                    else {double sample=(x+.5)*.5-.5;int left=Math.max(0,Math.min(15,(int)Math.floor(sample))),right=Math.max(0,Math.min(15,left+1));double weight=sample-Math.floor(sample);expected=new int[3];for(int c=0;c<3;c++)expected[c]=(int)Math.round(COLORS[row*2+left/8][c]*(1-weight)+COLORS[row*2+right/8][c]*weight);}
                }
                boolean mismatch=false;for(int c=0;c<3;c++){int error=Math.abs(Byte.toUnsignedInt(pixels.get((y*32+x)*4+c))-expected[c]);maxError=Math.max(maxError,error);mismatch|=error>tolerance;}mismatch|=Byte.toUnsignedInt(pixels.get((y*32+x)*4+3))!=255;if(mismatch)mismatches++;
            }
            JsonObject q=new JsonObject();q.addProperty("case",name);q.addProperty("pixels_checked",512);q.addProperty("mismatched_pixels",mismatches);q.addProperty("maximum_channel_error",maxError);q.addProperty("channel_tolerance",tolerance);q.addProperty("matched",mismatches==0);cases.add(q);phase("completed_"+name);
            if(mismatches!=0)throw new IllegalStateException("Native framebuffer blit pixel mismatch: "+name+" "+mismatches);
        } finally {MemoryUtil.memFree(pixels);}
    }
}
