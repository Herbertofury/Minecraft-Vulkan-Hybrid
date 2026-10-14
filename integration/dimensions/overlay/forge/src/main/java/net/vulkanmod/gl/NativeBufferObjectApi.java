package net.vulkanmod.gl;

import com.mojang.blaze3d.systems.RenderSystem;
import net.vulkanmod.compat.UniversalRendererGate;
import net.vulkanmod.vulkan.Vulkan;
import net.vulkanmod.vulkan.device.DeviceManager;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.*;
import java.nio.*;
import java.util.*;
import static org.lwjgl.vulkan.VK10.*;

/** Native storage/transfer facade for pinned Physics helpers, not a VAO/SSBO provider.
 * Storage shares the verified pixel namespace, coherent allocations and frame retirement.
 * Recorded readers retain their allocation; later host writes replace live storage first.
 * GPU writes in this contract are completed transfers, never untracked shader writes. */
public final class NativeBufferObjectApi {
    private static final Set<Integer> TARGETS=Set.of(0x8892,0x8893,0x8A11,0x8C2A,0x88EB,0x88EC,0x8F36,0x8F37,0x90D2,0x8F3F,0x90EE,0x8C8E,0x9192);
    static final int USAGES=VK_BUFFER_USAGE_TRANSFER_SRC_BIT|VK_BUFFER_USAGE_TRANSFER_DST_BIT|VK_BUFFER_USAGE_VERTEX_BUFFER_BIT|VK_BUFFER_USAGE_INDEX_BUFFER_BIT|VK_BUFFER_USAGE_UNIFORM_BUFFER_BIT|VK_BUFFER_USAGE_STORAGE_BUFFER_BIT|VK_BUFFER_USAGE_UNIFORM_TEXEL_BUFFER_BIT|VK_BUFFER_USAGE_STORAGE_TEXEL_BUFFER_BIT|VK_BUFFER_USAGE_INDIRECT_BUFFER_BIT;
    private static long copies,replacements;
    private NativeBufferObjectApi(){}
    private static void owner(){if(!UniversalRendererGate.vulkanRendererEnabled()||Vulkan.getVkDevice()==null)throw new IllegalStateException("Native buffer device unavailable");RenderSystem.assertOnRenderThread();}
    private static boolean target(int target){if(TARGETS.contains(target))return true;NativeGlErrors.record(1280);return false;}
    private static NativePixelBufferApi.Entry bound(int target){if(!target(target))return null;GlBuffer name=GlBuffer.nativeBoundBuffer(target);var entry=name==null?null:NativePixelBufferApi.ENTRIES.get(name.getId());if(entry==null)NativeGlErrors.record(1282);return entry;}
    public static int glGenBuffers(){owner();return NativePixelBufferApi.glGenBuffers();}
    public static void glBindBuffer(int target,int name){owner();if(!target(target))return;if(name!=0&&!NativePixelBufferApi.ENTRIES.containsKey(name))throw new UnsupportedOperationException("Unreviewed native buffer name: "+name);GlBuffer.glBindBuffer(target,name);if(target==0x8893)NativeVertexArrayApi.elementBound(name);}
    public static void glDeleteBuffers(int name){owner();GlBuffer.glDeleteBuffers(name);}
    public static void glBufferData(int target,long size,int usage){allocate(target,size,usage,null);}
    private static boolean allocate(int target,long size,int usage,ByteBuffer input){
        owner();var e=bound(target);if(e==null)return false;
        if(size<0||size>Integer.MAX_VALUE){NativeGlErrors.record(1281);return false;}
        if(!Set.of(0x88E0,0x88E1,0x88E2,0x88E4,0x88E5,0x88E6,0x88E8,0x88E9,0x88EA).contains(usage)){NativeGlErrors.record(1280);return false;}
        var next=size==0?null:new NativePixelBufferApi.Store((int)size,USAGES);var old=e.store;
        // Input may point into old mapped storage. Initialize while it is still alive.
        if(next!=null&&input!=null)MemoryUtil.memCopy(MemoryUtil.memAddress(input),next.pointer,input.remaining());
        e.store=next;e.mapped=false;e.usage=usage;
        GlBuffer name=GlBuffer.nativeBoundBuffer(target);name.size=(int)size;name.version++;if(old!=null)old.delete();return true;
    }
    private static long bytes(Buffer source,int width){return source==null?0:(long)source.remaining()*width;}
    private static ByteBuffer raw(Buffer source,int width){
        if(source==null)return null;if(!source.isDirect())throw new IllegalArgumentException("Native original buffer input must be direct");
        long address=source instanceof ByteBuffer b?MemoryUtil.memAddress(b):source instanceof FloatBuffer b?MemoryUtil.memAddress(b):source instanceof IntBuffer b?MemoryUtil.memAddress(b):MemoryUtil.memAddress((ShortBuffer)source);
        return MemoryUtil.memByteBuffer(address,Math.toIntExact(bytes(source,width)));
    }
    private static void data(int target,Buffer source,int width,int usage){ByteBuffer input=raw(source,width);allocate(target,bytes(source,width),usage,input);}
    public static void glBufferData(int target,ByteBuffer data,int usage){data(target,data,1,usage);}
    public static void glBufferData(int target,FloatBuffer data,int usage){data(target,data,4,usage);}
    public static void glBufferData(int target,IntBuffer data,int usage){data(target,data,4,usage);}
    public static void glBufferData(int target,ShortBuffer data,int usage){data(target,data,2,usage);}
    private static boolean range(NativePixelBufferApi.Entry e,long offset,long size){
        if(e==null)return false;if(e.mapped){NativeGlErrors.record(1282);return false;}
        long extent=e.store==null?0:e.store.size;if(offset<0||size<0||offset>extent||size>extent-offset){NativeGlErrors.record(1281);return false;}return true;
    }
    private static void writable(NativePixelBufferApi.Entry e){
        var old=e.store;if(old==null||old.recordedUses==0)return;
        var next=new NativePixelBufferApi.Store(old.size,USAGES);MemoryUtil.memCopy(old.pointer,next.pointer,old.size);e.store=next;old.delete();replacements++;
    }
    private static void subData(int target,long offset,ByteBuffer source){
        owner();var e=bound(target);if(source==null)throw new IllegalArgumentException("Missing native buffer input");if(!range(e,offset,source.remaining()))return;
        if(source.remaining()==0)return;writable(e);MemoryUtil.memCopy(MemoryUtil.memAddress(source),e.store.pointer+offset,source.remaining());GlBuffer.nativeBoundBuffer(target).version++;
    }
    public static void glBufferSubData(int target,long offset,ByteBuffer source){subData(target,offset,raw(source,1));}
    public static void glBufferSubData(int target,long offset,FloatBuffer source){subData(target,offset,raw(source,4));}
    public static void glBufferSubData(int target,long offset,IntBuffer source){subData(target,offset,raw(source,4));}
    public static void glBufferSubData(int target,long offset,ShortBuffer source){subData(target,offset,raw(source,2));}
    public static ByteBuffer glMapBuffer(int target,int access){
        owner();var e=bound(target);if(e==null)return null;if(e.mapped||e.store==null){NativeGlErrors.record(1282);return null;}
        if(access!=0x88B8&&access!=0x88B9&&access!=0x88BA){NativeGlErrors.record(1280);return null;}
        if(access!=0x88B8)writable(e);e.mapped=true;return MemoryUtil.memByteBuffer(e.store.pointer,e.store.size).order(ByteOrder.nativeOrder());
    }
    public static boolean glUnmapBuffer(int target){owner();var e=bound(target);if(e==null||!e.mapped){NativeGlErrors.record(1282);return false;}e.mapped=false;GlBuffer.nativeBoundBuffer(target).version++;return true;}
    public static void glCopyBufferSubData(int readTarget,int writeTarget,long readOffset,long writeOffset,long size){
        owner();var src=bound(readTarget);var dst=bound(writeTarget);if(!range(src,readOffset,size)||!range(dst,writeOffset,size))return;
        if(src==dst&&size>0&&readOffset<writeOffset+size&&writeOffset<readOffset+size){NativeGlErrors.record(1281);return;}if(size==0)return;
        // COW keeps already recorded vertex/index/pixel readers on their original storage.
        // Vulkan transfer completion makes its coherent destination immediately host readable.
        writable(dst);var source=src.store;var destination=dst.store;var queue=DeviceManager.getGraphicsQueue();var command=queue.beginCommands();
        try(MemoryStack stack=MemoryStack.stackPush()){
            var barrier=VkMemoryBarrier.calloc(1,stack).sType$Default().srcAccessMask(VK_ACCESS_HOST_WRITE_BIT|VK_ACCESS_TRANSFER_WRITE_BIT).dstAccessMask(VK_ACCESS_TRANSFER_READ_BIT|VK_ACCESS_TRANSFER_WRITE_BIT);
            vkCmdPipelineBarrier(command.getHandle(),VK_PIPELINE_STAGE_HOST_BIT|VK_PIPELINE_STAGE_TRANSFER_BIT,VK_PIPELINE_STAGE_TRANSFER_BIT,0,barrier,null,null);
            var region=VkBufferCopy.calloc(1,stack).srcOffset(readOffset).dstOffset(writeOffset).size(size);vkCmdCopyBuffer(command.getHandle(),source.buffer,destination.buffer,region);
            barrier.srcAccessMask(VK_ACCESS_TRANSFER_WRITE_BIT).dstAccessMask(VK_ACCESS_HOST_READ_BIT|VK_ACCESS_VERTEX_ATTRIBUTE_READ_BIT|VK_ACCESS_INDEX_READ_BIT|VK_ACCESS_UNIFORM_READ_BIT|VK_ACCESS_SHADER_READ_BIT|VK_ACCESS_TRANSFER_READ_BIT);
            vkCmdPipelineBarrier(command.getHandle(),VK_PIPELINE_STAGE_TRANSFER_BIT,VK_PIPELINE_STAGE_HOST_BIT|VK_PIPELINE_STAGE_VERTEX_INPUT_BIT|VK_PIPELINE_STAGE_ALL_GRAPHICS_BIT|VK_PIPELINE_STAGE_TRANSFER_BIT,0,barrier,null,null);
        }
        long fence=queue.submitCommands(command);int result=vkWaitForFences(DeviceManager.vkDevice,fence,true,-1L);if(result!=VK_SUCCESS)throw new IllegalStateException("Native buffer copy fence failed: "+result);copies++;GlBuffer.nativeBoundBuffer(writeTarget).version++;
    }
    public static int glGetBufferParameteri(int target,int parameter){owner();var e=bound(target);if(e==null)return 0;return switch(parameter){case 0x8764->e.store==null?0:e.store.size;case 0x8765->e.usage;case 0x88BC->e.mapped?1:0;default->{NativeGlErrors.record(1280);yield 0;}};}
    public static long bufferHandle(int name){owner();var e=NativePixelBufferApi.ENTRIES.get(name);return e==null||e.store==null?0:e.store.buffer;}
    public static Map<String,Long> transferSnapshot(){owner();return Map.of("copies",copies,"retainedReaderReplacements",replacements);}
    static NativePixelBufferApi.Store drawStorage(int name,long offset,long bytes,int usage){
        owner();return drawEntry(NativePixelBufferApi.ENTRIES.get(name),offset,bytes,usage);
    }
    static NativePixelBufferApi.Store drawEntry(NativePixelBufferApi.Entry e,long offset,long bytes,int usage){
        owner();if(e==null||e.store==null||e.mapped)throw new IllegalStateException("Native draw storage unavailable or mapped");
        if(!range(e,offset,bytes))throw new IllegalArgumentException("Native draw storage range");
        if((e.store.vkUsage&usage)==0){var old=e.store;var next=new NativePixelBufferApi.Store(old.size,USAGES);MemoryUtil.memCopy(old.pointer,next.pointer,old.size);e.store=next;old.delete();replacements++;}
        e.store.retain();return e.store;
    }
}
