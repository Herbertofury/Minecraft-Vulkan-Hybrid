package net.vulkanmod.gl;
import com.mojang.blaze3d.systems.RenderSystem;
import net.vulkanmod.compat.UniversalRendererGate;
import net.vulkanmod.vulkan.device.DeviceManager;
import net.vulkanmod.vulkan.Renderer;
import net.vulkanmod.vulkan.texture.*;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;
import java.nio.ByteBuffer;
import java.util.*;
import static org.lwjgl.vulkan.VK10.*;

/** Actual typed Essential image allocation; no GL proxy or fabricated capability result. */
public final class NativeEssentialImageApi {
 private record Config(int name,int mips,int usage){}
 private record ImageInfo(VulkanImage image,int internalFormat,int pixelFormat,int pixelType,int usage){}
 private static Config configuring;private static int proxyWidth,proxyHeight;private static final Map<Integer,ImageInfo>IMAGES=new HashMap<>();private static final Set<Integer>NAMES=new HashSet<>();
 private NativeEssentialImageApi(){}
 private static void owner(){RenderSystem.assertOnRenderThread();if(!UniversalRendererGate.vulkanRendererEnabled()||DeviceManager.vkDevice==null)throw new IllegalStateException("Native Essential image device unavailable");}
 public static int glGenTextures(){owner();int name=GlTexture.genTextureId();NAMES.add(name);return name;}
 public static void glDeleteTextures(int name){owner();if(name==0)return;if(!NAMES.remove(name))throw new UnsupportedOperationException("Unowned typed texture deletion");IMAGES.remove(name);GlTexture.glDeleteTextures(name);}
 public static void configureTexture(int name,Runnable operation,int mipLevels,int usage){
  owner();if(!NAMES.contains(name)||operation==null||mipLevels<=0||usage==0)throw new IllegalArgumentException("Native Essential image allocation arguments");int oldName=GlTexture.getBoundTextureId(0x0DE1),unit=GlTexture.getActiveTexture()-0x84C0;var oldImage=VTextureSelector.getImage(unit);Config previous=configuring;configuring=new Config(name,mipLevels,usage);GlTexture.bindTexture(name);
  try{operation.run();if(!IMAGES.containsKey(name))throw new IllegalStateException("Original texture configuration did not allocate native image");}catch(RuntimeException|Error failure){glDeleteTextures(name);throw failure;}finally{configuring=previous;GlTexture.bindTexture(oldName);VTextureSelector.bindTexture(unit,oldImage);}
 }
 private static int format(int internal,int pixels,int type){return switch(internal){
  case 0x8058->VK_FORMAT_R8G8B8A8_UNORM;case 0x8C43->VK_FORMAT_R8G8B8A8_SRGB;
  case 0x8229->VK_FORMAT_R8_UNORM;case 0x822B->VK_FORMAT_R8G8_UNORM;case 0x822A->VK_FORMAT_R16_UNORM;case 0x822C->VK_FORMAT_R16G16_UNORM;
  case 0x822D->VK_FORMAT_R16_SFLOAT;case 0x822F->VK_FORMAT_R16G16_SFLOAT;case 0x822E->VK_FORMAT_R32_SFLOAT;case 0x8230->VK_FORMAT_R32G32_SFLOAT;
  case 0x881A->VK_FORMAT_R16G16B16A16_SFLOAT;case 0x8814->VK_FORMAT_R32G32B32A32_SFLOAT;
  case 0x8CAC->VK_FORMAT_D32_SFLOAT;case 0x81A5->VK_FORMAT_D16_UNORM;case 0x81A6->VK_FORMAT_X8_D24_UNORM_PACK32;
  case 0x88F0->VK_FORMAT_D24_UNORM_S8_UINT;case 0x8CAD->VK_FORMAT_D32_SFLOAT_S8_UINT;
  case 0x1908->{if(type!=0x1401)throw new UnsupportedOperationException("Typed unsized color format needs a precision variant");yield VK_FORMAT_R8G8B8A8_UNORM;}
  case 0x1902->{if(type!=0x1406)throw new UnsupportedOperationException("Typed unsized depth format needs a precision variant");yield VK_FORMAT_D32_SFLOAT;}
  default->throw new UnsupportedOperationException("Additional native typed image format required: "+internal);
 };}
 private static int usage(int bits,int vkFormat){if((bits&~15)!=0)throw new UnsupportedOperationException("Additional typed image usage required");int u=0;if((bits&1)!=0)u|=VK_IMAGE_USAGE_TRANSFER_DST_BIT;if((bits&2)!=0)u|=VK_IMAGE_USAGE_TRANSFER_SRC_BIT;if((bits&4)!=0)u|=VK_IMAGE_USAGE_SAMPLED_BIT;if((bits&8)!=0)u|=VulkanImage.isDepthFormat(vkFormat)?VK_IMAGE_USAGE_DEPTH_STENCIL_ATTACHMENT_BIT:VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT;return u;}
 private static VkImageFormatProperties properties(MemoryStack stack,int vkFormat,int usage){var p=VkImageFormatProperties.calloc(stack);int result=vkGetPhysicalDeviceImageFormatProperties(DeviceManager.physicalDevice,vkFormat,VK_IMAGE_TYPE_2D,VK_IMAGE_TILING_OPTIMAL,usage,0,p);if(result!=VK_SUCCESS)throw new UnsupportedOperationException("Physical device cannot allocate requested native image format/usage: "+result);return p;}
 public static int maxTextureSize(int internal,int pixels,int type){owner();int f=format(internal,pixels,type);int u=VK_IMAGE_USAGE_TRANSFER_SRC_BIT|VK_IMAGE_USAGE_TRANSFER_DST_BIT|VK_IMAGE_USAGE_SAMPLED_BIT|(VulkanImage.isDepthFormat(f)?VK_IMAGE_USAGE_DEPTH_STENCIL_ATTACHMENT_BIT:VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT);try(MemoryStack stack=MemoryStack.stackPush()){var p=properties(stack,f,u);return Math.min(p.maxExtent().width(),p.maxExtent().height());}}
 public static int glGetInteger(int parameter){owner();if(parameter==0x0D33)return DeviceManager.deviceProperties.limits().maxImageDimension2D();if(parameter==0x8069)return GlTexture.getBoundTextureId(0x0DE1);if(parameter==0x84E0)return GlTexture.getActiveTexture();throw new UnsupportedOperationException("Additional typed image query required: "+parameter);}
 public static int glGetTexLevelParameteri(int target,int level,int parameter){owner();if(target==0x8064&&level==0){if(parameter==0x1000)return proxyWidth;if(parameter==0x1001)return proxyHeight;}throw new UnsupportedOperationException("Additional typed image level query required");}
 public static void glTexImage2D(int target,int level,int internal,int width,int height,int border,int pixels,int type,ByteBuffer input){
  owner();if(target==0x8064&&level==0&&border==0&&input==null){int maximum=maxTextureSize(internal,pixels,type);proxyWidth=width>0&&height>0&&width<=maximum&&height<=maximum?width:0;proxyHeight=proxyWidth==0?0:height;return;}Config c=configuring;if(c==null)throw new IllegalStateException("Native typed texture needs original configuration scope");if(target!=0x0DE1||level!=0||border!=0||input!=null)throw new UnsupportedOperationException("Typed image creation requires separate upload/nonzero-level contract");if(width<=0||height<=0||c.mips<=0||c.mips>32-Integer.numberOfLeadingZeros(Math.max(width,height)))throw new IllegalArgumentException("Native texture extent/mips");int f=format(internal,pixels,type),u=usage(c.usage,f);
  try(MemoryStack stack=MemoryStack.stackPush()){var p=properties(stack,f,u);if(width>p.maxExtent().width()||height>p.maxExtent().height()||c.mips>p.maxMipLevels())throw new IllegalArgumentException("Native texture exceeds actual format limits");}
  var image=VulkanImage.builder(width,height).setFormat(f).setMipLevels(c.mips).setUsage(u).setLevelViews(true).setLinearFiltering(false).createVulkanImage();image.updateTextureSampler(c.mips-1,(byte)(SamplerManager.UNBIASED_LOD_BIT|(c.mips>1?SamplerManager.USE_MIPMAPS_BIT|SamplerManager.MIPMAP_LINEAR_FILTERING_BIT:0)));ImageInfo old=IMAGES.put(c.name,new ImageInfo(image,internal,pixels,type,c.usage));if(old!=null)old.image.free();GlTexture.bindIdToImage(c.name,image);VTextureSelector.bindTexture(GlTexture.getActiveTexture()-0x84C0,image);
 }
 public static VulkanImage image(int name){owner();var i=IMAGES.get(name);if(i==null)throw new UnsupportedOperationException("Unowned typed native image");return i.image;}
 public static void clearColor(int name,int mip,float red,float green,float blue,float alpha){owner();var i=IMAGES.get(name);if(i==null||(i.usage&1)==0||i.image.aspect!=VK_IMAGE_ASPECT_COLOR_BIT||mip<0||mip>=i.image.mipLevels)throw new IllegalArgumentException("Native color clear target/usage/mip");if(Renderer.isRecording())Renderer.getInstance().flushForReadback();var queue=DeviceManager.getGraphicsQueue();var command=queue.beginCommands();try(MemoryStack stack=MemoryStack.stackPush()){i.image.transitionImageLayout(stack,command.getHandle(),VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL);var value=VkClearColorValue.calloc(stack).float32(0,red).float32(1,green).float32(2,blue).float32(3,alpha);var range=VkImageSubresourceRange.calloc(1,stack).aspectMask(VK_IMAGE_ASPECT_COLOR_BIT).baseMipLevel(mip).levelCount(1).baseArrayLayer(0).layerCount(1);vkCmdClearColorImage(command.getHandle(),i.image.getId(),VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL,value,range);i.image.transitionImageLayout(stack,command.getHandle(),VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL);}long fence=queue.submitCommands(command);int result=vkWaitForFences(DeviceManager.vkDevice,fence,true,-1L);if(result!=VK_SUCCESS)throw new IllegalStateException("Native typed image clear failed: "+result);}
 public static void clearDepth(int name,int mip,float depth){owner();var i=IMAGES.get(name);if(i==null||(i.usage&1)==0||(i.image.aspect&VK_IMAGE_ASPECT_DEPTH_BIT)==0||mip<0||mip>=i.image.mipLevels)throw new IllegalArgumentException("Native depth clear target/usage/mip");if(Renderer.isRecording())Renderer.getInstance().flushForReadback();var queue=DeviceManager.getGraphicsQueue();var command=queue.beginCommands();try(MemoryStack stack=MemoryStack.stackPush()){i.image.transitionImageLayout(stack,command.getHandle(),VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL);var value=VkClearDepthStencilValue.calloc(stack).depth(depth).stencil(0);var range=VkImageSubresourceRange.calloc(1,stack).aspectMask(i.image.aspect).baseMipLevel(mip).levelCount(1).baseArrayLayer(0).layerCount(1);vkCmdClearDepthStencilImage(command.getHandle(),i.image.getId(),VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL,value,range);i.image.transitionImageLayout(stack,command.getHandle(),VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL);}long fence=queue.submitCommands(command);int result=vkWaitForFences(DeviceManager.vkDevice,fence,true,-1L);if(result!=VK_SUCCESS)throw new IllegalStateException("Native typed depth clear failed: "+result);}
 public static Map<String,Long> snapshot(int name){owner();var i=IMAGES.get(name);if(i==null)return Map.of("ownedImages",(long)IMAGES.size());var image=i.image;return Map.of("ownedImages",(long)IMAGES.size(),"image",image.getId(),"view",image.getImageView(),"format",(long)image.format,"width",(long)image.width,"height",(long)image.height,"mips",(long)image.mipLevels,"usage",(long)i.usage);}
}
