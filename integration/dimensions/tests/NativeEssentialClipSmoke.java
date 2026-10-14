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
import java.nio.*;
import java.nio.file.*;
import static org.lwjgl.vulkan.VK10.*;

/** Real original clipping calls, actual Vulkan pixels, capture and readback restoration. */
final class NativeEssentialClipSmoke {
    static void run(JsonArray cases)throws Exception{
        OriginalEssentialClipForgeApplyProof.run();
        Renderer renderer=Renderer.getInstance();RenderPass previous=renderer.getBoundRenderPass();if(previous==null||!Renderer.isRecording())throw new IllegalStateException("Native recording frame required");
        NativeStencilSmoke.State state=new NativeStencilSmoke.State();boolean oldClip=Renderer.isScissorEnabled();int[] oldBox=new int[4];for(int n=0;n<4;n++)oldBox[n]=Renderer.getScissorBox(n);
        Matrix4f projection=new Matrix4f(RenderSystem.getProjectionMatrix());var shader=RenderSystem.getShader();float[] shaderColor=RenderSystem.getShaderColor().clone();var model=RenderSystem.getModelViewStack();model.pushPose();model.setIdentity();RenderSystem.applyModelViewMatrix();
        VulkanImage color=null,depth=null;Framebuffer framebuffer=null;
        try(OriginalEssentialClipProof original=new OriginalEssentialClipProof()){
            color=VulkanImage.builder(32,32).setFormat(VK_FORMAT_R8G8B8A8_UNORM).setUsage(VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT|VK_IMAGE_USAGE_TRANSFER_SRC_BIT|VK_IMAGE_USAGE_SAMPLED_BIT).setLinearFiltering(false).createVulkanImage();depth=VulkanImage.createDepthImage(VK_FORMAT_D32_SFLOAT_S8_UINT,32,32,VK_IMAGE_USAGE_DEPTH_STENCIL_ATTACHMENT_BIT|VK_IMAGE_USAGE_SAMPLED_BIT,false,true);framebuffer=Framebuffer.builder(color,depth).build();
            RenderPass.Builder builder=RenderPass.builder(framebuffer);builder.getDepthAttachmentInfo().setOps(VK_ATTACHMENT_LOAD_OP_CLEAR,VK_ATTACHMENT_STORE_OP_STORE);
            VRenderSystem.setClearColor(0,0,1,1);VRenderSystem.clearDepth(1);VRenderSystem.clearStencil(0);
            if(!renderer.beginRendering(builder.build(),framebuffer))throw new IllegalStateException("Native clipping pass rejected");Renderer.setInvertedViewport(0,0,32,32);RenderSystem.setProjectionMatrix(new Matrix4f().setOrtho(0,32,32,0,-1,1),VertexSorting.ORTHOGRAPHIC_Z);RenderSystem.setShader(GameRenderer::getPositionColorShader);RenderSystem.setShaderColor(1,1,1,1);RenderSystem.disableBlend();RenderSystem.disableCull();RenderSystem.disableDepthTest();RenderSystem.depthMask(false);VRenderSystem.disableStencilTest();VRenderSystem.colorMask(true,true,true,true);
            for(String name:new String[]{"gg.essential.universal.render.ScissorState","gg.essential.util.ScissorState"}){
                String tag=name.contains("universal")?"universal":"legacy";
                Object enabled=original.state(name,true,3,7,11,5);original.activate(enabled);original.capture(name,true,3,7,11,5);clear();quad(0,1,0);check(color,cases,tag+"-asymmetric",3,7,11,5,0,1,0);original.capture(name,true,3,7,11,5);
                // No box/viewport reapplication between these draws: readback must preserve both.
                quad(1,0,0);check(color,cases,tag+"-readback-resumes-clip",3,7,11,5,1,0,0);original.capture(name,true,3,7,11,5);
                Object disabled=original.state(name,false,3,7,11,5);original.activate(disabled);original.capture(name,false,3,7,11,5);clear();quad(0,1,0);check(color,cases,tag+"-disabled-stored-box",0,0,32,32,0,1,0);
                // Enabling must apply the saved logical box even before the next glScissor call.
                NativeClipApi.glEnable(3089);original.capture(name,true,3,7,11,5);clear();quad(1,0,0);check(color,cases,tag+"-reenabled-stored-box",3,7,11,5,1,0,0);
                Object coerced=original.coerce(original.state(name,true,-3,-4,14,13),2,3,6,4);original.activate(coerced);original.capture(name,true,2,3,6,4);clear();quad(0,1,0);check(color,cases,tag+"-coerced",2,3,6,4,0,1,0);
                original.activate(original.state(name,true,-5,-7,8,10));original.capture(name,true,-5,-7,8,10);clear();quad(0,1,0);check(color,cases,tag+"-negative-origin",0,0,3,3,0,1,0);
                original.activate(original.state(name,true,Integer.MAX_VALUE,Integer.MAX_VALUE,Integer.MAX_VALUE,Integer.MAX_VALUE));original.capture(name,true,Integer.MAX_VALUE,Integer.MAX_VALUE,Integer.MAX_VALUE,Integer.MAX_VALUE);clear();quad(0,1,0);check(color,cases,tag+"-overflow-outside",0,0,0,0,0,1,0);
                original.activate(original.state(name,true,3,7,0,5));original.capture(name,true,3,7,0,5);clear();quad(0,1,0);check(color,cases,tag+"-zero-extent",0,0,0,0,0,1,0);
            }
            NativeClipApi.glDisable(3089);Object effect=original.stencil(),matrix=original.matrix();VRenderSystem.stencilMask(0xff);VRenderSystem.clearStencil(0);clear();Renderer.clearAttachments(1024);original.stencil(effect,matrix,"beforeDraw");VRenderSystem.colorMask(false,false,false,false);rect(8,8,24,24,0,1,0);
            if(!VRenderSystem.stencilTest||VRenderSystem.stencilFunc!=519||VRenderSystem.stencilRef!=2||VRenderSystem.stencilFailOp!=7681||VRenderSystem.stencilDepthFailOp!=7681||VRenderSystem.stencilPassOp!=7681)throw new IllegalStateException("Original stencil write state changed");
            original.stencil(effect,matrix,"beforeChildrenDraw");VRenderSystem.colorMask(true,true,true,true);quad(0,1,0);check(color,cases,"original-stencil-child-clip",8,8,16,16,0,1,0);
            quad(1,0,0);check(color,cases,"original-stencil-readback-resumes",8,8,16,16,1,0,0);original.stencil(effect,matrix,"afterDraw");if(VRenderSystem.stencilTest)throw new IllegalStateException("Original stencil disable missed renderer");quad(0,1,0);check(color,cases,"original-stencil-disabled-full",0,0,32,32,0,1,0);
            IntBuffer integers=ByteBuffer.allocateDirect(32).order(ByteOrder.nativeOrder()).asIntBuffer();for(int n=0;n<8;n++)integers.put(n,12345);integers.position(2).limit(7);NativeClipApi.glGetIntegerv(3088,integers);if(integers.position()!=2||integers.limit()!=7||integers.get(0)!=12345||integers.get(1)!=12345||integers.get(6)!=12345)throw new IllegalStateException("Native query changed cursor or sentinels");
            original.complete();
        }finally{
            renderer.endRenderPass();renderer.beginRendering(previous,previous.getFramebuffer());Renderer.setInvertedViewport(0,0,previous.getFramebuffer().getWidth(),previous.getFramebuffer().getHeight());Renderer.setScissorEnabled(oldClip);Renderer.setScissor(oldBox[0],oldBox[1],oldBox[2],oldBox[3]);state.restore();RenderSystem.setProjectionMatrix(projection,VertexSorting.ORTHOGRAPHIC_Z);model.popPose();RenderSystem.applyModelViewMatrix();RenderSystem.setShader(()->shader);RenderSystem.setShaderColor(shaderColor[0],shaderColor[1],shaderColor[2],shaderColor[3]);if(framebuffer!=null)framebuffer.cleanUp();else{if(color!=null)color.free();if(depth!=null)depth.free();}
        }
    }
    private static void clear(){VRenderSystem.setClearColor(0,0,1,1);Renderer.clearAttachments(16384);}
    private static void quad(int red,int green,int blue){rect(0,0,32,32,red,green,blue);}
    private static void rect(float left,float top,float right,float bottom,int red,int green,int blue){BufferBuilder v=Tesselator.getInstance().getBuilder();v.begin(VertexFormat.Mode.QUADS,DefaultVertexFormat.POSITION_COLOR);v.vertex(left,bottom,0).color(red*255,green*255,blue*255,255).endVertex();v.vertex(right,bottom,0).color(red*255,green*255,blue*255,255).endVertex();v.vertex(right,top,0).color(red*255,green*255,blue*255,255).endVertex();v.vertex(left,top,0).color(red*255,green*255,blue*255,255).endVertex();BufferUploader.drawWithShader(v.end());}
    private static void check(VulkanImage image,JsonArray cases,String label,int left,int bottom,int width,int height,int red,int green,int blue)throws Exception{
        int[] box=new int[4];for(int n=0;n<4;n++)box[n]=Renderer.getScissorBox(n);boolean enabled=Renderer.isScissorEnabled();ByteBuffer pixels=MemoryUtil.memAlloc(4096);
        try{ImageUtil.downloadTexture(image,MemoryUtil.memAddress(pixels));if(Renderer.isScissorEnabled()!=enabled)throw new IllegalStateException("Readback changed scissor enabled state");for(int n=0;n<4;n++)if(Renderer.getScissorBox(n)!=box[n])throw new IllegalStateException("Readback changed logical scissor box");int bad=0;for(int y=0;y<32;y++)for(int x=0;x<32;x++){boolean ink=x>=left&&x<left+width&&y>=32-bottom-height&&y<32-bottom;int[] expected=ink?new int[]{red*255,green*255,blue*255,255}:new int[]{0,0,255,255};boolean mismatch=false;for(int c=0;c<4;c++)if(Byte.toUnsignedInt(pixels.get((y*32+x)*4+c))!=expected[c])mismatch=true;if(mismatch)bad++;}
            byte[] bytes=new byte[4096];pixels.get(0,bytes);Path path=Minecraft.getInstance().gameDirectory.toPath().resolve("mvh-clip-gpu-readback");Files.createDirectories(path);Files.write(path.resolve(label+".rgba"),bytes);JsonObject item=new JsonObject();item.addProperty("case","original-clip-"+label);item.addProperty("matched",bad==0);item.addProperty("compared_pixels",1024);item.addProperty("mismatched_pixels",bad);item.addProperty("channel_tolerance",0);item.addProperty("actual_rgba_sha256",OriginalEssentialClipProof.hash(bytes));item.addProperty("logical_scissor_preserved_by_readback",true);cases.add(item);if(bad!=0)throw new IllegalStateException("Original clipping pixels mismatch "+label+": "+bad);
        }finally{MemoryUtil.memFree(pixels);}
    }
}
