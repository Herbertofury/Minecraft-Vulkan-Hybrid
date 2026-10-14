import net.vulkanmod.vulkan.shader.NativeUiProgram;
import net.vulkanmod.vulkan.shader.NativeUiPipeline;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.nio.*;
import java.nio.file.*;
import java.util.*;
import static org.lwjgl.vulkan.VK10.*;

/** Exact production classes, real original UI GLSL, native sampled texture/pixels. */
public final class NativeUiGpuRegression {
    static VkInstance instance;static VkDevice device;static VkQueue queue;static VkPhysicalDevice gpu;
    static long commandPool,pass,image,imageMemory,view,framebuffer,readBuffer,readMemory,readAddress;
    static long texture,textureMemory,textureView,sampler;
    static final int SIZE=32,BYTES=SIZE*SIZE*4;
    static void require(boolean v,String m){if(!v)throw new AssertionError(m);}
    static void check(int r,String m){require(r==VK_SUCCESS,m+" VkResult="+r);}
    static int memoryType(int bits,int wanted,MemoryStack s){VkPhysicalDeviceMemoryProperties p=VkPhysicalDeviceMemoryProperties.calloc(s);vkGetPhysicalDeviceMemoryProperties(gpu,p);for(int i=0;i<p.memoryTypeCount();i++)if((bits&(1<<i))!=0&&(p.memoryTypes(i).propertyFlags()&wanted)==wanted)return i;throw new AssertionError("required native memory type");}
    static long allocate(VkMemoryRequirements r,int wanted,MemoryStack s){LongBuffer out=s.mallocLong(1);check(vkAllocateMemory(device,VkMemoryAllocateInfo.calloc(s).sType$Default().allocationSize(r.size()).memoryTypeIndex(memoryType(r.memoryTypeBits(),wanted,s)),null,out),"allocate");return out.get(0);}
    static void initialize(){try(MemoryStack s=MemoryStack.stackPush()){
        VkInstanceCreateInfo ci=VkInstanceCreateInfo.calloc(s).sType$Default().pApplicationInfo(VkApplicationInfo.calloc(s).sType$Default().apiVersion(VK_MAKE_VERSION(1,2,0)).pApplicationName(s.UTF8("MVH Original Essential UI QA")));
        PointerBuffer out=s.mallocPointer(1);check(vkCreateInstance(ci,null,out),"instance");instance=new VkInstance(out.get(0),ci);
        IntBuffer count=s.ints(0);check(vkEnumeratePhysicalDevices(instance,count,null),"device count");PointerBuffer devices=s.mallocPointer(count.get(0));check(vkEnumeratePhysicalDevices(instance,count,devices),"devices");
        for(int i=0;i<devices.capacity();i++){VkPhysicalDevice p=new VkPhysicalDevice(devices.get(i),instance);VkPhysicalDeviceProperties props=VkPhysicalDeviceProperties.calloc(s);vkGetPhysicalDeviceProperties(p,props);if(props.deviceType()==VK_PHYSICAL_DEVICE_TYPE_DISCRETE_GPU){gpu=p;System.out.println("GPU="+props.deviceNameString()+" driver="+props.driverVersion());break;}}
        require(gpu!=null,"real hardware GPU required");vkGetPhysicalDeviceQueueFamilyProperties(gpu,count,null);VkQueueFamilyProperties.Buffer families=VkQueueFamilyProperties.calloc(count.get(0),s);vkGetPhysicalDeviceQueueFamilyProperties(gpu,count,families);int family=-1;for(int i=0;i<families.capacity();i++)if((families.get(i).queueFlags()&VK_QUEUE_GRAPHICS_BIT)!=0){family=i;break;}require(family>=0,"graphics queue");
        VkDeviceCreateInfo di=VkDeviceCreateInfo.calloc(s).sType$Default().pQueueCreateInfos(VkDeviceQueueCreateInfo.calloc(1,s).sType$Default().queueFamilyIndex(family).pQueuePriorities(s.floats(1)));
        check(vkCreateDevice(gpu,di,null,out),"device");device=new VkDevice(out.get(0),gpu,di);vkGetDeviceQueue(device,family,0,out);queue=new VkQueue(out.get(0),device);
        LongBuffer h=s.mallocLong(1);check(vkCreateCommandPool(device,VkCommandPoolCreateInfo.calloc(s).sType$Default().queueFamilyIndex(family).flags(VK_COMMAND_POOL_CREATE_RESET_COMMAND_BUFFER_BIT),null,h),"command pool");commandPool=h.get(0);
        VkImageCreateInfo ii=VkImageCreateInfo.calloc(s).sType$Default().imageType(VK_IMAGE_TYPE_2D).format(VK_FORMAT_R8G8B8A8_UNORM).mipLevels(1).arrayLayers(1).samples(VK_SAMPLE_COUNT_1_BIT).tiling(VK_IMAGE_TILING_OPTIMAL).usage(VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT|VK_IMAGE_USAGE_TRANSFER_SRC_BIT).sharingMode(VK_SHARING_MODE_EXCLUSIVE);ii.extent().set(SIZE,SIZE,1);
        check(vkCreateImage(device,ii,null,h),"image");image=h.get(0);VkMemoryRequirements req=VkMemoryRequirements.calloc(s);vkGetImageMemoryRequirements(device,image,req);imageMemory=allocate(req,VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT,s);check(vkBindImageMemory(device,image,imageMemory,0),"image bind");
        view=imageView(image,s);VkAttachmentDescription.Buffer attachments=VkAttachmentDescription.calloc(1,s).format(VK_FORMAT_R8G8B8A8_UNORM).samples(VK_SAMPLE_COUNT_1_BIT).loadOp(VK_ATTACHMENT_LOAD_OP_CLEAR).storeOp(VK_ATTACHMENT_STORE_OP_STORE).stencilLoadOp(VK_ATTACHMENT_LOAD_OP_DONT_CARE).stencilStoreOp(VK_ATTACHMENT_STORE_OP_DONT_CARE).initialLayout(VK_IMAGE_LAYOUT_UNDEFINED).finalLayout(VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL);
        VkAttachmentReference.Buffer ref=VkAttachmentReference.calloc(1,s).attachment(0).layout(VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL);
        VkSubpassDescription.Buffer sub=VkSubpassDescription.calloc(1,s).pipelineBindPoint(VK_PIPELINE_BIND_POINT_GRAPHICS).colorAttachmentCount(1).pColorAttachments(ref);
        VkSubpassDependency.Buffer deps=VkSubpassDependency.calloc(2,s);deps.get(0).srcSubpass(VK_SUBPASS_EXTERNAL).dstSubpass(0).srcStageMask(VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT).dstStageMask(VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT).dstAccessMask(VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT);deps.get(1).srcSubpass(0).dstSubpass(VK_SUBPASS_EXTERNAL).srcStageMask(VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT).dstStageMask(VK_PIPELINE_STAGE_TRANSFER_BIT).srcAccessMask(VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT).dstAccessMask(VK_ACCESS_TRANSFER_READ_BIT);
        check(vkCreateRenderPass(device,VkRenderPassCreateInfo.calloc(s).sType$Default().pAttachments(attachments).pSubpasses(sub).pDependencies(deps),null,h),"render pass");pass=h.get(0);
        check(vkCreateFramebuffer(device,VkFramebufferCreateInfo.calloc(s).sType$Default().renderPass(pass).pAttachments(s.longs(view)).width(SIZE).height(SIZE).layers(1),null,h),"framebuffer");framebuffer=h.get(0);
        check(vkCreateBuffer(device,VkBufferCreateInfo.calloc(s).sType$Default().size(BYTES).usage(VK_BUFFER_USAGE_TRANSFER_DST_BIT).sharingMode(VK_SHARING_MODE_EXCLUSIVE),null,h),"readback");readBuffer=h.get(0);vkGetBufferMemoryRequirements(device,readBuffer,req);readMemory=allocate(req,VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT|VK_MEMORY_PROPERTY_HOST_COHERENT_BIT,s);check(vkBindBufferMemory(device,readBuffer,readMemory,0),"read bind");check(vkMapMemory(device,readMemory,0,BYTES,0,out),"read map");readAddress=out.get(0);
        ii.usage(VK_IMAGE_USAGE_SAMPLED_BIT|VK_IMAGE_USAGE_TRANSFER_DST_BIT);ii.extent().set(1,1,1);check(vkCreateImage(device,ii,null,h),"original font sampler image");texture=h.get(0);vkGetImageMemoryRequirements(device,texture,req);textureMemory=allocate(req,VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT,s);check(vkBindImageMemory(device,texture,textureMemory,0),"font sampler bind");textureView=imageView(texture,s);
        check(vkCreateSampler(device,VkSamplerCreateInfo.calloc(s).sType$Default().magFilter(VK_FILTER_NEAREST).minFilter(VK_FILTER_NEAREST).mipmapMode(VK_SAMPLER_MIPMAP_MODE_NEAREST).addressModeU(VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE).addressModeV(VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE).addressModeW(VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE).maxLod(0),null,h),"font sampler");sampler=h.get(0);
    }}
    static long imageView(long image,MemoryStack s){VkImageViewCreateInfo i=VkImageViewCreateInfo.calloc(s).sType$Default().image(image).viewType(VK_IMAGE_VIEW_TYPE_2D).format(VK_FORMAT_R8G8B8A8_UNORM);i.subresourceRange().aspectMask(VK_IMAGE_ASPECT_COLOR_BIT).levelCount(1).layerCount(1);LongBuffer out=s.mallocLong(1);check(vkCreateImageView(device,i,null,out),"view");return out.get(0);}
    static VkCommandBuffer begin(MemoryStack s){PointerBuffer out=s.mallocPointer(1);check(vkAllocateCommandBuffers(device,VkCommandBufferAllocateInfo.calloc(s).sType$Default().commandPool(commandPool).level(VK_COMMAND_BUFFER_LEVEL_PRIMARY).commandBufferCount(1),out),"commands");VkCommandBuffer c=new VkCommandBuffer(out.get(0),device);check(vkBeginCommandBuffer(c,VkCommandBufferBeginInfo.calloc(s).sType$Default().flags(VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT)),"begin");return c;}
    static long fence(MemoryStack s){LongBuffer out=s.mallocLong(1);check(vkCreateFence(device,VkFenceCreateInfo.calloc(s).sType$Default(),null,out),"fence");return out.get(0);}
    static void submit(VkCommandBuffer c,long f,MemoryStack s){check(vkEndCommandBuffer(c),"end");check(vkQueueSubmit(queue,VkSubmitInfo.calloc(s).sType$Default().pCommandBuffers(s.pointers(c)),f),"submit");}
    static void waitFence(long f){check(vkWaitForFences(device,f,true,10_000_000_000L),"submitted draw wait");}
    static void texture(boolean white,boolean first){try(MemoryStack s=MemoryStack.stackPush()){
        VkCommandBuffer c=begin(s);VkImageMemoryBarrier.Buffer b=VkImageMemoryBarrier.calloc(1,s).sType$Default().oldLayout(first?VK_IMAGE_LAYOUT_UNDEFINED:VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL).newLayout(VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL).srcQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED).dstQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED).image(texture).srcAccessMask(first?0:VK_ACCESS_SHADER_READ_BIT).dstAccessMask(VK_ACCESS_TRANSFER_WRITE_BIT);b.subresourceRange().aspectMask(VK_IMAGE_ASPECT_COLOR_BIT).levelCount(1).layerCount(1);
        vkCmdPipelineBarrier(c,first?VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT:VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT,VK_PIPELINE_STAGE_TRANSFER_BIT,0,null,null,b);
        VkClearColorValue color=VkClearColorValue.calloc(s);for(int i=0;i<4;i++)color.float32(i,white?1:0);vkCmdClearColorImage(c,texture,VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL,color,b.subresourceRange());
        b.oldLayout(VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL).newLayout(VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL).srcAccessMask(VK_ACCESS_TRANSFER_WRITE_BIT).dstAccessMask(VK_ACCESS_SHADER_READ_BIT);vkCmdPipelineBarrier(c,VK_PIPELINE_STAGE_TRANSFER_BIT,VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT,0,null,null,b);
        long f=fence(s);submit(c,f,s);waitFence(f);vkDestroyFence(device,f,null);vkFreeCommandBuffers(device,commandPool,c);
    }}
    static ByteBuffer vertices(NativeUiProgram p){ByteBuffer b=ByteBuffer.allocateDirect(6*p.attributes().size()*16).order(ByteOrder.LITTLE_ENDIAN);int[][] positions={{0,0},{32,0},{32,32},{0,0},{32,32},{0,32}};for(int[] xy:positions)for(NativeUiProgram.Attribute a:p.attributes()){float[] values=switch(a.name()){case "mvh_Vertex"->new float[]{xy[0],xy[1],0,1};case "mvh_Color"->new float[]{0.25f,0.5f,1,0.8f};case "mvh_TexCoord"->new float[]{xy[0]/32f,xy[1]/32f,0,1};default->throw new AssertionError("original input name");};for(float f:values)b.putFloat(f);}return b.flip();}
    static ByteBuffer uniforms(NativeUiProgram p,String kind,float radius){ByteBuffer u=p.allocateUniforms();p.writeFloats(u,"mvh_ModelViewProjection",2f/32,0,0,0,0,2f/32,0,0,0,0,1,0,-1,-1,0,1);switch(kind){case "circle"->{p.writeFloats(u,"u_Radius",radius);p.writeFloats(u,"u_CenterPos",16,16);}case "rounded_rect"->{p.writeFloats(u,"u_Radius",radius);p.writeFloats(u,"u_InnerRect",8,8,24,24);}case "font"->{p.writeFloats(u,"fgColor",0.1f,0.7f,0.2f,1);p.writeFloats(u,"doffset",0.1f);p.writeFloats(u,"hint_amount",0.5f);p.writeFloats(u,"subpixel_amount",0);p.writeFloats(u,"sdf_texel",1f/32,1f/32);}default->throw new AssertionError();}return u;}
    static byte[] draw(NativeUiProgram program,NativeUiPipeline pipeline,String kind,float radius){
        Map<String,NativeUiPipeline.Texture> tex=kind.equals("font")?Map.of("msdf",new NativeUiPipeline.Texture(textureView,sampler)):Map.of();
        NativeUiPipeline.Packet packet=pipeline.packet(vertices(program),uniforms(program,kind,radius),tex);
        try(MemoryStack s=MemoryStack.stackPush()){
            VkCommandBuffer c=begin(s);VkClearValue.Buffer clear=VkClearValue.calloc(1,s);VkRenderPassBeginInfo begin=VkRenderPassBeginInfo.calloc(s).sType$Default().renderPass(pass).framebuffer(framebuffer).pClearValues(clear);begin.renderArea().extent().set(SIZE,SIZE);vkCmdBeginRenderPass(c,begin,VK_SUBPASS_CONTENTS_INLINE);
            VkViewport.Buffer viewport=VkViewport.calloc(1,s).width(SIZE).height(SIZE).minDepth(0).maxDepth(1);VkRect2D.Buffer scissor=VkRect2D.calloc(1,s);scissor.extent().set(SIZE,SIZE);vkCmdSetViewport(c,0,viewport);vkCmdSetScissor(c,0,scissor);packet.record(c);vkCmdEndRenderPass(c);
            try{packet.close();throw new AssertionError("recorded packet freed before completion fence");}catch(IllegalStateException expected){require(expected.getMessage().contains("no submission"),"recorded ownership retained");}
            VkBufferImageCopy.Buffer copy=VkBufferImageCopy.calloc(1,s);copy.imageSubresource().aspectMask(VK_IMAGE_ASPECT_COLOR_BIT).layerCount(1);copy.imageExtent().set(SIZE,SIZE,1);vkCmdCopyImageToBuffer(c,image,VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL,readBuffer,copy);
            VkBufferMemoryBarrier.Buffer b=VkBufferMemoryBarrier.calloc(1,s).sType$Default().srcAccessMask(VK_ACCESS_TRANSFER_WRITE_BIT).dstAccessMask(VK_ACCESS_HOST_READ_BIT).srcQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED).dstQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED).buffer(readBuffer).size(BYTES);vkCmdPipelineBarrier(c,VK_PIPELINE_STAGE_TRANSFER_BIT,VK_PIPELINE_STAGE_HOST_BIT,0,null,b,null);
            long f=fence(s);submit(c,f,s);packet.submitted(f);waitFence(f);byte[] pixels=new byte[BYTES];MemoryUtil.memByteBuffer(readAddress,BYTES).get(pixels);packet.close();packet.close();vkDestroyFence(device,f,null);vkFreeCommandBuffers(device,commandPool,c);return pixels;
        }
    }
    static double smooth(double lo,double hi,double x){double t=Math.max(0,Math.min(1,(x-lo)/(hi-lo)));return t*t*(3-2*t);}
    static void pixels(byte[] b,String kind,float radius,boolean white){int bad=0,max=0;for(int y=0;y<SIZE;y++)for(int x=0;x<SIZE;x++){
        double a;if(kind.equals("circle")){a=(1-smooth(radius-1,radius,Math.hypot(x+0.5-16,y+0.5-16)))*0.8;}else if(kind.equals("rounded_rect")){double dx=Math.max(x+0.5-24,8-x-0.5),dy=Math.max(y+0.5-24,8-y-0.5);a=(1-smooth(0,1,Math.hypot(Math.max(0,dx),Math.max(0,dy))-radius))*0.8;}else a=white?1:0;
        double[] rgb=kind.equals("font")?new double[]{0.1,0.7,0.2}:new double[]{0.25,0.5,1};for(int c=0;c<4;c++){int expected=(int)Math.round(255*a*(c==3?1:rgb[c]));int error=Math.abs((b[(y*SIZE+x)*4+c]&255)-expected);max=Math.max(max,error);if(error>1)bad++;}
    }require(bad==0,"original UI pixels "+kind+" radius="+radius+" mismatches="+bad+" max="+max);System.out.println("GPU_UI_PIXELS="+kind+" radius="+radius+" whiteTexture="+white+" channels="+BYTES+" mismatch=0 tolerance=1 max="+max);}
    static void image(Path out,String name,byte[] b)throws Exception{BufferedImage im=new BufferedImage(SIZE,SIZE,BufferedImage.TYPE_INT_ARGB);for(int y=0;y<SIZE;y++)for(int x=0;x<SIZE;x++){int at=(y*SIZE+x)*4;im.setRGB(x,y,(b[at+3]&255)<<24|(b[at]&255)<<16|(b[at+1]&255)<<8|(b[at+2]&255));}ImageIO.write(im,"png",out.resolve(name+".png").toFile());}
    static NativeUiProgram compile(Path p,String kind)throws Exception{return NativeUiProgram.compile(kind,Files.readString(p.resolve(kind.equals("font")?"002-font.vsh":"003-rect.vsh")),Files.readString(p.resolve(switch(kind){case "circle"->"000-circle.fsh";case "font"->"001-font.fsh";default->"004-rounded_rect.fsh";})));}
    public static void main(String[] args)throws Exception{Path inputs=Path.of(args[0]),out=Path.of(args[1]);Files.createDirectories(out);try{initialize();texture(true,true);
        for(String kind:new String[]{"circle","rounded_rect","font"}){
            NativeUiProgram program=compile(inputs,kind);
            try(NativeUiPipeline pipeline=new NativeUiPipeline(device,program,pass)){
                byte[] a=draw(program,pipeline,kind,8);pixels(a,kind,8,true);image(out,kind+"-original",a);
                if(!kind.equals("font")){byte[] b=draw(program,pipeline,kind,4);pixels(b,kind,4,true);require(!Arrays.equals(a,b),"live uniform affects native pixels");image(out,kind+"-uniform4",b);}else{texture(false,false);byte[] b=draw(program,pipeline,kind,8);pixels(b,kind,8,false);require(!Arrays.equals(a,b),"actual native texture affects original MSDF shader");image(out,kind+"-black-sampler",b);texture(true,false);}
            }
            try(NativeUiPipeline reloaded=new NativeUiPipeline(device,compile(inputs,kind),pass)){pixels(draw(program,reloaded,kind,8),kind,8,true);}
        }
        System.out.println("NATIVE_ORIGINAL_UI_GPU_PASS shaderPairs=3 actualDraws=9 pixelChannels=36864 reloads=3 recordedOwnershipControls=9");
    }finally{if(device!=null){vkDeviceWaitIdle(device);if(sampler!=0)vkDestroySampler(device,sampler,null);if(textureView!=0)vkDestroyImageView(device,textureView,null);if(texture!=0)vkDestroyImage(device,texture,null);if(textureMemory!=0)vkFreeMemory(device,textureMemory,null);if(readAddress!=0)vkUnmapMemory(device,readMemory);if(readBuffer!=0)vkDestroyBuffer(device,readBuffer,null);if(readMemory!=0)vkFreeMemory(device,readMemory,null);if(framebuffer!=0)vkDestroyFramebuffer(device,framebuffer,null);if(pass!=0)vkDestroyRenderPass(device,pass,null);if(view!=0)vkDestroyImageView(device,view,null);if(image!=0)vkDestroyImage(device,image,null);if(imageMemory!=0)vkFreeMemory(device,imageMemory,null);if(commandPool!=0)vkDestroyCommandPool(device,commandPool,null);vkDestroyDevice(device,null);}if(instance!=null)vkDestroyInstance(instance,null);}}
}
