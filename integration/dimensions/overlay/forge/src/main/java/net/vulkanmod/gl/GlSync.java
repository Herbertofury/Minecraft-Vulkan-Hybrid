package net.vulkanmod.gl;

import com.mojang.blaze3d.systems.RenderSystem;
import net.vulkanmod.vulkan.Renderer;
import net.vulkanmod.vulkan.Vulkan;
import net.vulkanmod.vulkan.memory.MemoryManager;
import net.vulkanmod.vulkan.util.VkResult;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkDevice;
import org.lwjgl.vulkan.VkEventCreateInfo;
import org.lwjgl.vulkan.VkMemoryBarrier;

import java.nio.LongBuffer;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.LockSupport;

import static org.lwjgl.vulkan.VK10.*;

/** Fence completion comes from a device event after preceding graphics commands.
 * Names die on delete; Vulkan objects survive every recorded use and host waiter.
 * Retirement runs only after the renderer's frame fence, or device-idle teardown. */
public final class GlSync {
    public static final int GL_OBJECT_TYPE=0x9112, GL_SYNC_CONDITION=0x9113,
            GL_SYNC_STATUS=0x9114, GL_SYNC_FLAGS=0x9115, GL_SYNC_FENCE=0x9116,
            GL_SYNC_GPU_COMMANDS_COMPLETE=0x9117, GL_UNSIGNALED=0x9118,
            GL_SIGNALED=0x9119, GL_ALREADY_SIGNALED=0x911A,
            GL_TIMEOUT_EXPIRED=0x911B, GL_CONDITION_SATISFIED=0x911C,
            GL_WAIT_FAILED=0x911D, GL_SYNC_FLUSH_COMMANDS_BIT=1;
    private static final int INVALID_ENUM=0x0500, INVALID_VALUE=0x0501,
            INVALID_OPERATION=0x0502;
    private static final AtomicLong NEXT=new AtomicLong(0x564B_0000_0001L);
    private static final ConcurrentHashMap<Long,Fence> LIVE=new ConcurrentHashMap<>();
    private static final Set<Fence> ALLOCATED=ConcurrentHashMap.newKeySet();
    private GlSync(){}

    private static final class Fence {
        final VkDevice device;
        final long event;
        int recordedUses,hostWaiters;
        boolean deleted,destroyed;
        Fence(VkDevice device,long event){this.device=device;this.event=event;}
        synchronized int status(){
            if(destroyed)throw new IllegalStateException("GPU fence retired during use");
            int result=vkGetEventStatus(device,event);
            if(result!=VK_EVENT_SET&&result!=VK_EVENT_RESET)
                throw new IllegalStateException("Cannot query GPU fence: "+VkResult.decode(result));
            return result;
        }
        synchronized void retireRecordedUse(){
            if(recordedUses<=0)throw new IllegalStateException("GPU fence use retired twice");
            recordedUses--;destroyIfUnused();
        }
        synchronized void destroyIfUnused(){
            if(deleted&&recordedUses==0&&hostWaiters==0&&!destroyed){
                vkDestroyEvent(device,event,null);destroyed=true;ALLOCATED.remove(this);
            }
        }
    }
    private static void error(int code){NativeGlErrors.record(code);}
    public static int getError(){return NativeGlErrors.consume();}
    private static Renderer recordingRenderer(){
        Renderer renderer=Renderer.getInstance();
        return RenderSystem.isOnRenderThread()&&renderer!=null&&Renderer.isRecording()
                &&renderer.getBoundRenderPass()!=null?renderer:null;
    }
    private static Fence acquire(long name){
        Fence fence=LIVE.get(name);
        if(fence==null){error(INVALID_VALUE);return null;}
        synchronized(fence){
            if(fence.deleted){error(INVALID_VALUE);return null;}
            fence.hostWaiters++;
        }
        return fence;
    }
    private static void release(Fence fence){synchronized(fence){fence.hostWaiters--;fence.destroyIfUnused();}}
    private static void retainRecordedUse(Fence fence){
        synchronized(fence){
            if(fence.destroyed)throw new IllegalStateException("Cannot record a retired GPU fence");
            fence.recordedUses++;
        }
        MemoryManager.getInstance().addFrameOp(fence::retireRecordedUse);
    }
    public static long fenceSync(int condition,int flags){
        if(condition!=GL_SYNC_GPU_COMMANDS_COMPLETE){error(INVALID_ENUM);return 0;}
        if(flags!=0){error(INVALID_VALUE);return 0;}
        Renderer renderer=recordingRenderer();
        if(renderer==null){error(INVALID_OPERATION);return 0;}
        VkDevice device=Vulkan.getVkDevice();
        long event;
        try(MemoryStack stack=MemoryStack.stackPush()){
            LongBuffer output=stack.mallocLong(1);
            int result=vkCreateEvent(device,VkEventCreateInfo.calloc(stack).sType$Default(),null,output);
            if(result!=VK_SUCCESS)throw new IllegalStateException("Cannot create GPU fence: "+VkResult.decode(result));
            event=output.get(0);
        }
        Fence fence=new Fence(device,event);
        long name=NEXT.getAndIncrement();LIVE.put(name,fence);ALLOCATED.add(fence);
        retainRecordedUse(fence);
        try{
            renderer.runOutsideRenderPass(command->vkCmdSetEvent(command,event,VK_PIPELINE_STAGE_ALL_COMMANDS_BIT));
            return name;
        }catch(RuntimeException failure){deleteSync(name);throw failure;}
    }
    public static boolean isSync(long name){return LIVE.containsKey(name);}
    public static void deleteSync(long name){
        if(name==0)return;
        Fence fence=LIVE.remove(name);
        if(fence==null){error(INVALID_VALUE);return;}
        synchronized(fence){fence.deleted=true;fence.destroyIfUnused();}
    }
    public static int clientWaitSync(long name,int flags,long timeout){
        if((flags&~GL_SYNC_FLUSH_COMMANDS_BIT)!=0){error(INVALID_VALUE);return GL_WAIT_FAILED;}
        Fence fence=acquire(name);if(fence==null)return GL_WAIT_FAILED;
        try{
            if(fence.status()==VK_EVENT_SET)return GL_ALREADY_SIGNALED;
            if((flags&GL_SYNC_FLUSH_COMMANDS_BIT)!=0){
                Renderer renderer=recordingRenderer();
                if(renderer==null){error(INVALID_OPERATION);return GL_WAIT_FAILED;}
                // Submit without a host GPU wait; timeout=0 remains a poll.
                renderer.flushForSync();
            }
            long start=System.nanoTime();
            while(true){
                if(fence.status()==VK_EVENT_SET)return GL_CONDITION_SATISFIED;
                long elapsed=System.nanoTime()-start;
                // GLuint64 values above Long.MAX_VALUE exceed practical wait durations.
                if(timeout>=0&&elapsed>=timeout)return GL_TIMEOUT_EXPIRED;
                long remaining=timeout<0?50_000L:timeout-elapsed;
                LockSupport.parkNanos(Math.min(50_000L,remaining));
            }
        }finally{release(fence);}
    }
    public static void waitSync(long name,int flags,long timeout){
        if(flags!=0){error(INVALID_VALUE);return;}
        if(timeout!=-1L){error(INVALID_VALUE);return;}
        Fence fence=acquire(name);if(fence==null)return;
        try{
            Renderer renderer=recordingRenderer();
            if(renderer==null){error(INVALID_OPERATION);return;}
            retainRecordedUse(fence);
            renderer.runOutsideRenderPass(command->{
                try(MemoryStack stack=MemoryStack.stackPush()){
                    VkMemoryBarrier.Buffer barrier=VkMemoryBarrier.calloc(1,stack).sType$Default()
                            .srcAccessMask(VK_ACCESS_MEMORY_WRITE_BIT)
                            .dstAccessMask(VK_ACCESS_MEMORY_READ_BIT|VK_ACCESS_MEMORY_WRITE_BIT);
                    vkCmdWaitEvents(command,stack.longs(fence.event),VK_PIPELINE_STAGE_ALL_COMMANDS_BIT,
                            VK_PIPELINE_STAGE_ALL_COMMANDS_BIT,barrier,null,null);
                }
            });
        }finally{release(fence);}
    }
    public static int getSynci(long name,int pname){
        Fence fence=acquire(name);if(fence==null)return 0;
        try{return switch(pname){
            case GL_OBJECT_TYPE->GL_SYNC_FENCE;
            case GL_SYNC_CONDITION->GL_SYNC_GPU_COMMANDS_COMPLETE;
            case GL_SYNC_FLAGS->0;
            case GL_SYNC_STATUS->fence.status()==VK_EVENT_SET?GL_SIGNALED:GL_UNSIGNALED;
            default->{error(INVALID_ENUM);yield 0;}
        };}finally{release(fence);}
    }
    /** Called only after Vulkan.cleanUp waits the device and destroys its command pool. */
    public static void cleanUpAfterDeviceIdle(){
        int live=LIVE.size(),allocated=ALLOCATED.size();
        LIVE.clear();
        for(Fence fence:ALLOCATED){synchronized(fence){
            fence.deleted=true;
            // Recorded callbacks may still run later; retain their counts until retirement.
            if(fence.hostWaiters!=0)throw new IllegalStateException("GPU fence host waiter active at device teardown");
            if(!fence.destroyed){vkDestroyEvent(fence.device,fence.event,null);fence.destroyed=true;}
            ALLOCATED.remove(fence);
        }}
        if(allocated!=0)System.out.println("[MVH Sync] device-idle cleanup live="+live+" allocated="+allocated+" remaining="+ALLOCATED.size());
    }
    public static int allocatedFenceCount(){return ALLOCATED.size();}
    public static int liveFenceCount(){return LIVE.size();}
}
