package net.vulkanmod.vulkan;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.vulkanmod.vulkan.queue.CommandPool;
import net.vulkanmod.vulkan.util.VUtil;
import net.vulkanmod.vulkan.util.VkResult;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.VkDevice;

import java.nio.LongBuffer;

import static org.lwjgl.vulkan.VK10.*;

public class Synchronization {
    private static final int ALLOCATION_SIZE = 50;

    public static final Synchronization INSTANCE = new Synchronization(ALLOCATION_SIZE);

    private final LongBuffer fences;
    private int idx = 0;

    private ObjectArrayList<CommandPool.CommandBuffer> commandBuffers = new ObjectArrayList<>();

    Synchronization(int allocSize) {
        this.fences = MemoryUtil.memAllocLong(allocSize);
    }

    public synchronized void addCommandBuffer(CommandPool.CommandBuffer commandBuffer) {
        this.addFence(commandBuffer.getFence());
        this.commandBuffers.add(commandBuffer);
    }

    public synchronized void addFence(long fence) {
        if (idx == ALLOCATION_SIZE)
            waitFences();

        fences.put(idx, fence);
        idx++;
    }

    public synchronized void waitFences() {
        if (idx == 0)
            return;

        VkDevice device = Vulkan.getVkDevice();

        fences.limit(idx);
        try {
            int result = vkWaitForFences(device, fences, true, VUtil.UINT64_MAX);
            throwIfFailure(result, "wait for submitted command buffers");

            this.commandBuffers.forEach(CommandPool.CommandBuffer::reset);
            this.commandBuffers.clear();
            idx = 0;
        } finally {
            fences.limit(ALLOCATION_SIZE);
        }
    }

    public static void waitFence(long fence) {
        VkDevice device = Vulkan.getVkDevice();
        int result = vkWaitForFences(device, fence, true, VUtil.UINT64_MAX);
        throwIfFailure(result, "wait for submitted fence");
    }

    public static boolean checkFenceStatus(long fence) {
        VkDevice device = Vulkan.getVkDevice();
        int result = vkGetFenceStatus(device, fence);
        if (result == VK_NOT_READY)
            return false;
        throwIfFailure(result, "query submitted fence");
        return true;
    }

    private static void throwIfFailure(int result, String operation) {
        if (result != VK_SUCCESS)
            throw new IllegalStateException("Failed to " + operation + ": " + VkResult.decode(result));
    }
}
