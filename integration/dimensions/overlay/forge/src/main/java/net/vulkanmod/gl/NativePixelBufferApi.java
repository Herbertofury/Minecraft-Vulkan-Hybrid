package net.vulkanmod.gl;

import com.mojang.blaze3d.systems.RenderSystem;
import net.vulkanmod.compat.UniversalRendererGate;
import net.vulkanmod.compat.gl.GlPixelStore;
import net.vulkanmod.vulkan.*;
import net.vulkanmod.vulkan.memory.MemoryManager;
import net.vulkanmod.vulkan.texture.*;
import net.vulkanmod.vulkan.device.DeviceManager;
import org.lwjgl.system.*;
import org.lwjgl.vulkan.*;
import org.lwjgl.util.vma.*;
import java.nio.*;
import java.util.*;
import static org.lwjgl.vulkan.VK10.*;
import static org.lwjgl.util.vma.Vma.*;

/** Real host-coherent VkBuffer storage for reviewed pixel pack/unpack callers.
 * Names share GlBuffer's namespace. Generic vertex/index/SSBO contracts remain
 * unaccepted. Mapped storage never masquerades as a malloc-owned GlBuffer copy. */
public final class NativePixelBufferApi {
    public static final int PACK=0x88EB,UNPACK=0x88EC;
    static final Map<Integer,Entry> ENTRIES=new HashMap<>();
    private static final Set<Store> ALLOCATIONS=new HashSet<>();
    private static final Map<VulkanImage,Integer> PENDING_IMAGES=new IdentityHashMap<>();
    private static long gpuUploads,gpuDownloads;
    static final class Entry { Store store; boolean mapped,deleted; int usage=0x88E4,vertexReferences,mapFlags; long mapOffset,mapLength; }
    static final class Store {
        final long allocator,buffer,allocation,pointer; final int size,vkUsage;
        int recordedUses; boolean deleted,destroyed;
        Store(int size) { this(size,VK_BUFFER_USAGE_TRANSFER_SRC_BIT|VK_BUFFER_USAGE_TRANSFER_DST_BIT); }
        Store(int size,int vkUsage) {
            this.size=size;this.vkUsage=vkUsage;allocator=Vulkan.getAllocator();
            try(MemoryStack stack=MemoryStack.stackPush()) {
                var info=VkBufferCreateInfo.calloc(stack).sType$Default().size(size)
                        .usage(vkUsage)
                        .sharingMode(VK_SHARING_MODE_EXCLUSIVE);
                var ai=VmaAllocationCreateInfo.calloc(stack).requiredFlags(VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT|VK_MEMORY_PROPERTY_HOST_COHERENT_BIT)
                        .preferredFlags(VK_MEMORY_PROPERTY_HOST_CACHED_BIT);
                var b=stack.callocLong(1);var a=stack.callocPointer(1);var p=stack.callocPointer(1);
                int result=vmaCreateBufferWithAlignment(allocator,info,ai,64,b,a,null);
                if(result!=VK_SUCCESS)throw new IllegalStateException("Native pixel buffer creation failed: "+result);
                buffer=b.get(0);allocation=a.get(0);
                try {
                    result=vmaMapMemory(allocator,allocation,p);
                    if(result!=VK_SUCCESS||p.get(0)==0)throw new IllegalStateException("Native pixel buffer map failed: "+result);
                    pointer=p.get(0);
                    if((pointer&63)!=0)throw new IllegalStateException("Native pixel mapping alignment");
                } catch(RuntimeException|Error failure) {vmaDestroyBuffer(allocator,buffer,allocation);throw failure;}
            }
            ALLOCATIONS.add(this);
        }
        void retain(){if(deleted||destroyed)throw new IllegalStateException("Pixel storage used after retirement");recordedUses++;MemoryManager.getInstance().addFrameOp(()->{if(recordedUses<=0)throw new IllegalStateException("Pixel storage retired twice");recordedUses--;destroyIfReady();});}
        void delete(){deleted=true;destroyIfReady();}
        void destroyIfReady(){if(deleted&&recordedUses==0&&!destroyed)destroy();}
        void destroy(){if(!destroyed){vmaUnmapMemory(allocator,allocation);vmaDestroyBuffer(allocator,buffer,allocation);destroyed=true;ALLOCATIONS.remove(this);}}
    }
    private NativePixelBufferApi(){}
    private static void owner(){if(!UniversalRendererGate.vulkanRendererEnabled())throw new IllegalStateException("Native pixel buffers require Vulkan");RenderSystem.assertOnRenderThread();if(Vulkan.getVkDevice()==null)throw new IllegalStateException("Native pixel device unavailable");}
    private static boolean target(int target){if(target!=PACK&&target!=UNPACK)throw new UnsupportedOperationException("Unreviewed native pixel target: "+target);return true;}
    private static GlBuffer boundName(int target){target(target);return target==PACK?GlBuffer.getPixelPackBufferBound():GlBuffer.getPixelUnpackBufferBound();}
    private static Entry bound(int target){var name=boundName(target);return name==null?null:ENTRIES.get(name.getId());}
    private static void error(int code){NativeGlErrors.record(code);}
    public static boolean ownsBound(int target){var name=boundName(target);return name!=null&&ENTRIES.containsKey(name.getId());}
    public static int glGenBuffers(){owner();int name=GlBuffer.glGenBuffers();ENTRIES.put(name,new Entry());return name;}
    public static void glBindBuffer(int target,int name){owner();target(target);if(name!=0&&!ENTRIES.containsKey(name))throw new UnsupportedOperationException("Unreviewed foreign pixel name: "+name);GlBuffer.glBindBuffer(target,name);}
    public static void glBufferData(int target,long size,int usage){
        owner();Entry entry=bound(target);if(entry==null){error(1282);return;}
        if(size<0||size>Integer.MAX_VALUE){error(1281);return;}
        if(!Set.of(0x88E0,0x88E1,0x88E2,0x88E4,0x88E5,0x88E6,0x88E8,0x88E9,0x88EA).contains(usage)){error(1280);return;}
        Store replacement=size==0?null:new Store((int)size);Store old=entry.store;entry.store=replacement;entry.mapped=false;entry.mapOffset=0;entry.mapLength=0;entry.mapFlags=0;entry.usage=usage;
        var name=boundName(target);name.size=(int)size;name.version++;
        if(old!=null)old.delete();
    }
    public static ByteBuffer glMapBuffer(int target,int access){Entry entry=bound(target);return glMapBuffer(target,access,entry==null||entry.store==null?0:entry.store.size,null);}
    public static ByteBuffer glMapBuffer(int target,int access,long length,ByteBuffer previous){
        owner();Entry entry=bound(target);if(entry==null||entry.store==null||entry.mapped){error(1282);return null;}
        if(access!=0x88B8&&access!=0x88B9&&access!=0x88BA){error(1280);return null;}
        if(length<0||length>entry.store.size){error(1281);return null;}
        entry.mapped=true;entry.mapOffset=0;entry.mapLength=entry.store.size;entry.mapFlags=access==0x88B8?1:access==0x88B9?2:3;
        if(previous!=null&&previous.isDirect()&&MemoryUtil.memAddress0(previous)==entry.store.pointer&&previous.capacity()==length)return previous;
        return MemoryUtil.memByteBuffer(entry.store.pointer,(int)length).order(ByteOrder.nativeOrder());
    }
    public static boolean glUnmapBuffer(int target){owner();Entry entry=bound(target);if(entry==null||!entry.mapped){error(1282);return false;}entry.mapped=false;entry.mapOffset=0;entry.mapLength=0;entry.mapFlags=0;return true;}
    public static void glDeleteBuffers(int name){owner();GlBuffer.glDeleteBuffers(name);}
    static void retireNameIfOwned(int name){Entry entry=ENTRIES.remove(name);if(entry==null)return;entry.mapped=false;entry.deleted=true;NativeVertexArrayApi.nameDeleted(name,entry);if(entry.vertexReferences==0&&entry.store!=null)entry.store.delete();}
    public static void glDeleteBuffers(IntBuffer names){owner();for(int n=names.position();n<names.limit();n++)glDeleteBuffers(names.get(n));}
    /** Nonmutating diagnostic views of actual native allocation/ownership. */
    public static Map<String,Long> snapshot(){owner();long bytes=0;for(Store s:ALLOCATIONS)bytes+=s.size;return Map.of("objects",(long)ENTRIES.size(),"allocations",(long)ALLOCATIONS.size(),"bytes",bytes,"uploads",gpuUploads,"downloads",gpuDownloads);}
    public static long bufferHandle(int target){owner();Entry e=bound(target);return e==null||e.store==null?0:e.store.buffer;}
    private static Store transfer(int target,long offset,long bytes){Entry e=bound(target);if(e==null||e.store==null||e.mapped||offset<0||bytes<0||offset>e.store.size||bytes>e.store.size-offset){error(1282);return null;}return e.store;}
    /** CPU conversion fallback still reads the actual mapped Vulkan allocation;
     * callers copy it synchronously to the existing renderer staging buffer. */
    public static ByteBuffer unpackSource(long offset,long bytes){owner();Store s=transfer(UNPACK,offset,bytes);return s==null?null:MemoryUtil.memByteBuffer(s.pointer+offset,(int)bytes).order(ByteOrder.nativeOrder());}
    public static boolean upload(VulkanImage image,int level,int x,int y,int width,int height,int format,int type,long offset,int rowLength,int rowStride,int skipRows,int skipPixels){
        owner();if(!ownsBound(UNPACK))return false;
        // Converted formats/unaligned offsets use the existing exact CPU converter.
        if(format!=0x1908||(type!=0x1401&&type!=0x8367)||offset%4!=0||rowStride%4!=0)return false;
        if(level!=0||(image.format!=VK_FORMAT_R8G8B8A8_UNORM&&image.format!=VK_FORMAT_R8G8B8A8_SRGB))throw new UnsupportedOperationException("Native pixel image upload layout");
        if(type!=0x1401&&GlPixelStore.getInteger(GlPixelStore.GL_UNPACK_SWAP_BYTES)!=0)throw new UnsupportedOperationException("Native pixel packed byte swap");
        if(width==0||height==0)return true;
        if(x<0||y<0||width<0||height<0||x>(long)image.width-width||y>(long)image.height-height){error(1281);return true;}
        long base=offset+(long)skipRows*rowStride+(long)skipPixels*4;long bytes=(long)(height-1)*rowStride+(long)width*4;
        Store s=transfer(UNPACK,base,bytes);if(s==null)return true;
        var renderer=Renderer.getInstance();if(renderer==null||!Renderer.isRecording())throw new IllegalStateException("Native pixel upload requires recording stream");
        s.retain();PENDING_IMAGES.merge(image,1,Integer::sum);MemoryManager.getInstance().addFrameOp(()->PENDING_IMAGES.computeIfPresent(image,(key,n)->n==1?null:n-1));
        renderer.runOutsideRenderPass(command->{try(MemoryStack stack=MemoryStack.stackPush()){
            VkMemoryBarrier.Buffer barrier=VkMemoryBarrier.calloc(1,stack).sType$Default().srcAccessMask(VK_ACCESS_HOST_WRITE_BIT).dstAccessMask(VK_ACCESS_TRANSFER_READ_BIT);
            vkCmdPipelineBarrier(command,VK_PIPELINE_STAGE_HOST_BIT,VK_PIPELINE_STAGE_TRANSFER_BIT,0,barrier,null,null);
            image.transitionImageLayout(stack,command,VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL);
            ImageUtil.copyBufferToImageCmd(command,s.buffer,image.getId(),level,width,height,x,y,Math.toIntExact(base),rowStride/4,0);
            image.transitionImageLayout(stack,command,VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL);
        }});gpuUploads++;return true;
    }
    public static void download(VulkanImage image,int level,int format,int type,long offset){
        owner();if(level<0||level>=image.mipLevels||(image.format!=VK_FORMAT_R8G8B8A8_UNORM&&image.format!=VK_FORMAT_R8G8B8A8_SRGB)||(format!=0x1908&&format!=0x80E1)||(type!=0x1401&&type!=0x8035&&type!=0x8367))throw new UnsupportedOperationException("Native pixel readback layout");
        if(type!=0x1401&&GlPixelStore.getInteger(GlPixelStore.GL_PACK_SWAP_BYTES)!=0)throw new UnsupportedOperationException("Native pixel packed byte swap");
        int width=Math.max(1,image.width>>level),height=Math.max(1,image.height>>level);
        int alignment=GlPixelStore.getInteger(GlPixelStore.GL_PACK_ALIGNMENT),rowLength=GlPixelStore.getInteger(GlPixelStore.GL_PACK_ROW_LENGTH);
        if((alignment!=1&&alignment!=2&&alignment!=4&&alignment!=8)||rowLength<0||(rowLength>0&&rowLength<width))throw new UnsupportedOperationException("Native pixel pack alignment");
        int stride=Math.toIntExact((((long)(rowLength==0?width:rowLength)*4+alignment-1)/alignment)*alignment);
        long base=offset+(long)GlPixelStore.getInteger(GlPixelStore.GL_PACK_SKIP_ROWS)*stride+(long)GlPixelStore.getInteger(GlPixelStore.GL_PACK_SKIP_PIXELS)*4;
        long bytes=(long)(height-1)*stride+(long)width*4;Store s=transfer(PACK,base,bytes);if(s==null)return;
        if(base%4!=0||stride%4!=0)throw new UnsupportedOperationException("Native pixel buffer offset alignment");
        Renderer renderer=Renderer.getInstance();if(renderer!=null&&Renderer.isRecording())renderer.flushForReadback();
        int previous=image.getCurrentLayout();var queue=DeviceManager.getGraphicsQueue();var command=queue.beginCommands();
        try(MemoryStack stack=MemoryStack.stackPush()){
            image.transitionImageLayout(stack,command.getHandle(),VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL);
            ImageUtil.copyImageToBuffer(command.getHandle(),s.buffer,image.getId(),level,width,height,0,0,Math.toIntExact(base),stride/4,0);
            VkMemoryBarrier.Buffer barrier=VkMemoryBarrier.calloc(1,stack).sType$Default().srcAccessMask(VK_ACCESS_TRANSFER_WRITE_BIT).dstAccessMask(VK_ACCESS_HOST_READ_BIT);
            vkCmdPipelineBarrier(command.getHandle(),VK_PIPELINE_STAGE_TRANSFER_BIT,VK_PIPELINE_STAGE_HOST_BIT,0,barrier,null,null);
            image.transitionImageLayout(stack,command.getHandle(),previous);
        }
        long fence=queue.submitCommands(command);int result=vkWaitForFences(DeviceManager.vkDevice,fence,true,-1L);if(result!=VK_SUCCESS)throw new IllegalStateException("Native pixel readback fence failed: "+result);
        int[] swizzle=GlUtil.rgbaByteOffsets(format,type);if(swizzle!=null)for(int row=0;row<height;row++)GlUtil.swizzleFromRGBA(s.pointer+base+(long)row*stride,width,swizzle);
        gpuDownloads++;
    }
    /** Order the recorded upload before the existing separate mipmap submission.
     * The same graphics queue covers it; no CPU wait or world preparation delay. */
    public static void beforeMipmaps(VulkanImage image){if(PENDING_IMAGES.containsKey(image)){var renderer=Renderer.getInstance();if(renderer!=null&&Renderer.isRecording())renderer.flushForSync();}}
    /** Renderer calls this only after device idle, before allocator destruction. */
    public static void cleanUpAfterDeviceIdle(){int objects=ENTRIES.size(),allocations=ALLOCATIONS.size();for(Store s:new ArrayList<>(ALLOCATIONS))s.destroy();for(int name:new ArrayList<>(ENTRIES.keySet()))GlBuffer.glDeleteBuffers(name);ENTRIES.clear();PENDING_IMAGES.clear();System.out.println("[MVH Pixel] device-idle cleanup objects="+objects+" allocations="+allocations+" remaining="+ALLOCATIONS.size());}
}
