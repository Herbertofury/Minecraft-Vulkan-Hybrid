package net.vulkanmod.gl;
import com.mojang.blaze3d.systems.RenderSystem;
import net.vulkanmod.compat.UniversalRendererGate;
import net.vulkanmod.compat.gl.GlPixelStore;
import net.vulkanmod.vulkan.Renderer;
import net.vulkanmod.vulkan.texture.*;
import org.lwjgl.system.*;
import org.lwjgl.vulkan.*;
import java.util.*;
import static org.lwjgl.vulkan.VK10.*;

/** Typed original buffer-to-texture compatibility signatures, backed only by Vulkan storage. */
public final class NativeEssentialTransferApi {
    private static final int[] PARAMETERS={0xCF2,0x806E,0xCF3,0xCF4,0xCF5};
    private static long directUploads,repackedUploads;
    private NativeEssentialTransferApi(){}
    private static void owner(){RenderSystem.assertOnRenderThread();if(!UniversalRendererGate.vulkanRendererEnabled())throw new IllegalStateException("Typed uploads require native Vulkan");}
    public static void configureTexture(int name,Runnable operation){
        owner();NativeEssentialImageApi.image(name);int previousName=GlTexture.getBoundTextureId(0x0DE1),unit=GlTexture.getActiveTexture()-0x84C0,previousBuffer=NativeBufferObjectApi.glGetInteger(0x88EF);var previousImage=VTextureSelector.getImage(unit);int[] before=new int[PARAMETERS.length];for(int i=0;i<before.length;i++)before[i]=GlPixelStore.getInteger(PARAMETERS[i]);
        GlTexture.bindTexture(name);try{operation.run();}catch(RuntimeException|Error failure){NativeBufferObjectApi.glBindBuffer(0x88EC,previousBuffer);for(int i=0;i<before.length;i++)GlPixelStore.setInteger(PARAMETERS[i],before[i]);throw failure;}finally{GlTexture.bindTexture(previousName);VTextureSelector.bindTexture(unit,previousImage);}
    }
    public static void glPixelStorei(int parameter,int value){owner();boolean known=false;for(int p:PARAMETERS)known|=p==parameter;if(!known)throw new UnsupportedOperationException("Additional typed upload pixel store required");if(value<0||parameter==0xCF5&&value!=1&&value!=2&&value!=4&&value!=8)throw new IllegalArgumentException("Typed upload pixel store value");GlPixelStore.setInteger(parameter,value);}
    public static void glBindBuffer(int target,int name){owner();if(target!=0x88EC)throw new UnsupportedOperationException("Typed texture copy requires unpack binding");NativeBufferObjectApi.glBindBuffer(target,name);}
    public static void glTexSubImage2D(int target,int level,int x,int y,int width,int height,int format,int type,long offset){
        owner();var image=NativeEssentialImageApi.image(GlTexture.getBoundTextureId(0x0DE1));if(target!=0x0DE1||format!=0x1908||type!=0x1401&&type!=0x1400||image.format!=VK_FORMAT_R8G8B8A8_UNORM&&image.format!=VK_FORMAT_R8G8B8A8_SRGB)throw new UnsupportedOperationException("Additional native typed upload format required");
        if(!image.hasUsage(VK_IMAGE_USAGE_TRANSFER_DST_BIT)||level<0||level>=image.mipLevels||width<0||height<0||x<0||y<0||x>(long)Math.max(1,image.width>>level)-width||y>(long)Math.max(1,image.height>>level)-height)throw new IllegalArgumentException("Typed upload image extent/usage/mip");if(width==0||height==0)return;
        int alignment=GlPixelStore.getInteger(0xCF5),rowLength=GlPixelStore.getInteger(0xCF2);if(rowLength==0)rowLength=width;if(rowLength<width)throw new IllegalArgumentException("Typed upload row length");long stride=((Math.multiplyExact((long)rowLength,4)+alignment-1)/alignment)*alignment;long base=Math.addExact(offset,Math.addExact(Math.multiplyExact((long)GlPixelStore.getInteger(0xCF3),stride),Math.multiplyExact((long)GlPixelStore.getInteger(0xCF4),4)));long bytes=Math.addExact(Math.multiplyExact((long)(height-1),stride),(long)width*4);
        int buffer=NativeBufferObjectApi.glGetInteger(0x88EF);var entry=NativePixelBufferApi.ENTRIES.get(buffer);if(entry==null||entry.store==null||entry.mapped||base<0||base>entry.store.size||bytes>entry.store.size-base)throw new IllegalArgumentException("Typed upload needs unmapped complete native source range");
        if(!Renderer.isRecording())throw new IllegalStateException("Typed upload outside native command recording");boolean repack=type==0x1400||(base&3)!=0||(stride&3)!=0;NativePixelBufferApi.Store source;long copyOffset;int copyRowLength;
        if(repack){source=new NativePixelBufferApi.Store(Math.toIntExact((long)width*height*4));try{for(int row=0;row<height;row++){long from=entry.store.pointer+base+row*stride,to=source.pointer+(long)row*width*4;if(type==0x1401)MemoryUtil.memCopy(from,to,(long)width*4);else for(int c=0;c<width*4;c++){int value=MemoryUtil.memGetByte(from+c);MemoryUtil.memPutByte(to+c,(byte)Math.round(Math.max(0,value)*255f/127f));}}source.retain();}catch(RuntimeException|Error failure){source.delete();throw failure;}source.delete();copyOffset=0;copyRowLength=width;repackedUploads++;}
        else{source=NativeBufferObjectApi.drawStorage(buffer,base,bytes,VK_BUFFER_USAGE_TRANSFER_SRC_BIT);copyOffset=base;copyRowLength=Math.toIntExact(stride/4);directUploads++;}
        final var storage=source;final long start=copyOffset;final int rows=copyRowLength;
        Renderer.getInstance().runOutsideRenderPass(command->{try(MemoryStack stack=MemoryStack.stackPush()){
            var barrier=VkMemoryBarrier.calloc(1,stack).sType$Default().srcAccessMask(VK_ACCESS_HOST_WRITE_BIT).dstAccessMask(VK_ACCESS_TRANSFER_READ_BIT);vkCmdPipelineBarrier(command,VK_PIPELINE_STAGE_HOST_BIT,VK_PIPELINE_STAGE_TRANSFER_BIT,0,barrier,null,null);image.transitionImageLayout(stack,command,VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL);
            var region=VkBufferImageCopy.calloc(1,stack).bufferOffset(start).bufferRowLength(rows).bufferImageHeight(0);region.imageSubresource().aspectMask(VK_IMAGE_ASPECT_COLOR_BIT).mipLevel(level).baseArrayLayer(0).layerCount(1);region.imageOffset().set(x,y,0);region.imageExtent().set(width,height,1);vkCmdCopyBufferToImage(command,storage.buffer,image.getId(),VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL,region);image.transitionImageLayout(stack,command,VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL);
        }});
    }
    public static Map<String,Long> snapshot(){owner();return Map.of("directUploads",directUploads,"repackedUploads",repackedUploads);}
}
