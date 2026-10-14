package net.vulkanmod.gl;

import com.mojang.blaze3d.systems.RenderSystem;
import net.vulkanmod.compat.UniversalRendererGate;
import net.vulkanmod.vulkan.Renderer;
import net.vulkanmod.vulkan.VRenderSystem;
import net.vulkanmod.vulkan.device.DeviceManager;
import net.vulkanmod.vulkan.framebuffer.Framebuffer;
import net.vulkanmod.vulkan.framebuffer.RenderPass;
import net.vulkanmod.vulkan.texture.VulkanImage;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;
import java.util.*;
import static org.lwjgl.vulkan.VK10.*;

/** Exact typed-device clear framebuffer state. This does not implement general MRT/draw passes. */
public final class NativeEssentialFramebufferApi {
    private static final Map<Integer,Target> TARGETS=new HashMap<>();
    private static long recordedClears;
    private static final class Target {
        int color,depth,draw=0x8CE0,read=0x8CE0;
        VulkanImage colorImage,depthImage;
        Framebuffer framebuffer;
        RenderPass pass;
        void retire(){if(framebuffer!=null)framebuffer.cleanUp(false);if(pass!=null)pass.cleanUp();framebuffer=null;pass=null;colorImage=null;depthImage=null;}
    }
    private NativeEssentialFramebufferApi(){}
    private static void owner(){RenderSystem.assertOnRenderThread();if(!UniversalRendererGate.vulkanRendererEnabled()||DeviceManager.vkDevice==null)throw new IllegalStateException("Native framebuffer device unavailable");}
    public static int glGenFramebuffers(){owner();int id=GlFramebuffer.genFramebufferId();TARGETS.put(id,new Target());return id;}
    public static void glDeleteFramebuffers(int id){owner();Target target=TARGETS.remove(id);if(target!=null)target.retire();GlFramebuffer.deleteFramebuffer(id);}
    public static int glGetInteger(int parameter){owner();return switch(parameter){case 0x8CA6->GlFramebuffer.getBoundId();case 0x8CAA->GlFramebuffer.getReadId();default->throw new UnsupportedOperationException("Additional typed framebuffer query required");};}
    public static void glBindFramebuffer(int target,int id){owner();GlFramebuffer.bindFramebuffer(target,id);}
    private static Target target(int binding){int id=binding==0x8CA8?GlFramebuffer.getReadId():GlFramebuffer.getBoundId();Target target=TARGETS.get(id);if(target==null)throw new UnsupportedOperationException("Typed clear requires an owned framebuffer");return target;}
    public static void glDrawBuffer(int value){owner();if(value!=0&&value!=0x8CE0)throw new UnsupportedOperationException("Typed clear supports NONE or COLOR_ATTACHMENT0");target(0x8CA9).draw=value;}
    public static void glReadBuffer(int value){owner();if(value!=0&&value!=0x8CE0)throw new UnsupportedOperationException("Typed clear supports NONE or COLOR_ATTACHMENT0");target(0x8CA8).read=value;}
    public static void glFramebufferTexture2D(int binding,int attachment,int textureTarget,int texture,int level){
        owner();if(binding!=0x8D40&&binding!=0x8CA8&&binding!=0x8CA9)throw new IllegalArgumentException("Framebuffer attachment binding");if(textureTarget!=0x0DE1||level!=0)throw new UnsupportedOperationException("Typed clear mip/layer attachment requires separate native view contract");
        Target target=target(binding);if(attachment!=0x8CE0&&attachment!=0x8D00)throw new UnsupportedOperationException("Additional typed framebuffer attachment required");
        if(texture!=0){VulkanImage image=NativeEssentialImageApi.image(texture);int required=attachment==0x8CE0?VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT:VK_IMAGE_USAGE_DEPTH_STENCIL_ATTACHMENT_BIT;if(!image.hasUsage(required)||(attachment==0x8CE0?image.aspect!=VK_IMAGE_ASPECT_COLOR_BIT:(image.aspect&VK_IMAGE_ASPECT_DEPTH_BIT)==0))throw new IllegalArgumentException("Native framebuffer attachment format/usage");}
        int old=attachment==0x8CE0?target.color:target.depth;if(old==texture)return;target.retire();if(attachment==0x8CE0)target.color=texture;else target.depth=texture;
    }
    public static void clearColor(float red,float green,float blue,float alpha){owner();RenderSystem.clearColor(red,green,blue,alpha);}
    public static void clearDepth(double depth){owner();RenderSystem.clearDepth(Math.max(0.0,Math.min(1.0,depth)));}
    private static void prepare(Target target){
        VulkanImage color=target.color==0?null:NativeEssentialImageApi.image(target.color),depth=target.depth==0?null:NativeEssentialImageApi.image(target.depth);
        if(color==null&&depth==null)throw new IllegalStateException("Incomplete native typed clear framebuffer");
        if(color!=null&&depth!=null&&(color.width!=depth.width||color.height!=depth.height))throw new IllegalArgumentException("Typed clear attachment extents differ");
        if(target.framebuffer!=null&&target.colorImage==color&&target.depthImage==depth)return;
        target.retire();target.colorImage=color;target.depthImage=depth;target.framebuffer=Framebuffer.builder(color,depth).setBaseLevelAttachmentViews().build();var builder=RenderPass.builder(target.framebuffer);
        if(color!=null)builder.getColorAttachmentInfo().setOps(VK_ATTACHMENT_LOAD_OP_LOAD,VK_ATTACHMENT_STORE_OP_STORE).setFinalLayout(VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL);
        if(depth!=null)builder.getDepthAttachmentInfo().setOps(VK_ATTACHMENT_LOAD_OP_LOAD,VK_ATTACHMENT_STORE_OP_STORE).setFinalLayout(VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL);
        target.pass=builder.build();
    }
    private static int edge(long value,int extent){return(int)Math.max(0L,Math.min(value,(long)extent));}
    public static void glClear(int mask){
        owner();if((mask&~(0x4000|0x100))!=0)throw new UnsupportedOperationException("Additional typed framebuffer clear aspect required");if(mask==0)return;if(!Renderer.isRecording())throw new IllegalStateException("Native framebuffer clear outside owned recording");
        Target target=target(0x8CA9);prepare(target);boolean color=(mask&0x4000)!=0&&target.draw!=0&&target.colorImage!=null,depth=(mask&0x100)!=0&&target.depthImage!=null;if(!color&&!depth)return;
        int width=target.framebuffer.getWidth(),height=target.framebuffer.getHeight();int left=0,bottom=0,right=width,top=height;
        if(Renderer.isScissorEnabled()){left=edge(Renderer.getScissorBox(0),width);bottom=edge(Renderer.getScissorBox(1),height);right=edge((long)Renderer.getScissorBox(0)+Renderer.getScissorBox(2),width);top=edge((long)Renderer.getScissorBox(1)+Renderer.getScissorBox(3),height);}
        if(right<=left||top<=bottom)return;
        final int x=left,y=height-top,w=right-left,h=top-bottom;
        float[] value=new float[4];for(int c=0;c<4;c++)value[c]=VRenderSystem.clearColor.get(c);float depthValue=VRenderSystem.clearDepthValue;
        Renderer renderer=Renderer.getInstance();renderer.runOutsideRenderPass(command->{
            try(MemoryStack stack=MemoryStack.stackPush()){
                target.framebuffer.beginRenderPass(command,target.pass,stack);
                try{
                    var attachments=VkClearAttachment.calloc((color?1:0)+(depth?1:0),stack);int index=0;
                    if(color){var attachment=attachments.get(index++).aspectMask(VK_IMAGE_ASPECT_COLOR_BIT).colorAttachment(0);attachment.clearValue().color().float32(0,value[0]).float32(1,value[1]).float32(2,value[2]).float32(3,value[3]);}
                    if(depth){var attachment=attachments.get(index).aspectMask(VK_IMAGE_ASPECT_DEPTH_BIT);attachment.clearValue().depthStencil().depth(depthValue);}
                    var rect=VkClearRect.calloc(1,stack).baseArrayLayer(0).layerCount(1);rect.rect().offset().set(x,y);rect.rect().extent().set(w,h);vkCmdClearAttachments(command,attachments,rect);recordedClears++;
                }finally{renderer.endRenderPass(command);}
            }
        });
    }
    public static Map<String,Long> snapshot(int id){owner();Target target=TARGETS.get(id);var result=new HashMap<String,Long>();result.put("ownedFramebuffers",(long)TARGETS.size());result.put("recordedClears",recordedClears);result.put("drawBinding",(long)GlFramebuffer.getBoundId());result.put("readBinding",(long)GlFramebuffer.getReadId());if(target!=null){result.put("color",(long)target.color);result.put("depth",(long)target.depth);result.put("drawBuffer",(long)target.draw);result.put("readBuffer",(long)target.read);result.put("renderPass",target.pass==null?0:target.pass.getId());}return Map.copyOf(result);}
}
