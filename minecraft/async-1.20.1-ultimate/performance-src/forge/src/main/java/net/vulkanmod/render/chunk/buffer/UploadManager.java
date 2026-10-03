package net.vulkanmod.render.chunk.buffer;

import net.vulkanmod.vulkan.Synchronization;
import net.vulkanmod.vulkan.Vulkan;
import net.vulkanmod.vulkan.device.DeviceManager;
import net.vulkanmod.vulkan.memory.Buffer;
import net.vulkanmod.vulkan.memory.StagingBuffer;
import net.vulkanmod.vulkan.queue.CommandPool;
import net.vulkanmod.vulkan.queue.Queue;
import net.vulkanmod.vulkan.queue.TransferQueue;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkBufferCopy;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.lwjgl.vulkan.VkMemoryBarrier;
import java.nio.ByteBuffer;
import static org.lwjgl.vulkan.VK10.*;

/** Ordered multi-region uploads; barriers cover real dependencies, not shared handles. */
public class UploadManager {
    public static UploadManager INSTANCE;
    public static final boolean QA = Boolean.getBoolean("harimt.qa.performance");
    public long qaRegions, qaCopyCalls, qaWriteBarriers;
    // Terrain is consumed on the graphics queue. A pipeline barrier cannot
    // order a distinct transfer queue against an earlier graphics draw.
    private final Queue queue = DeviceManager.getGraphicsQueue();
    private CommandPool.CommandBuffer commandBuffer;
    private final UploadRanges writes = new UploadRanges();
    private final UploadCopies copies = new UploadCopies();

    public static void createInstance() { INSTANCE = new UploadManager(); }

    public void submitUploads() {
        if (this.commandBuffer == null) return;
        flushCopies();
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkMemoryBarrier.Buffer barrier = VkMemoryBarrier.calloc(1, stack);
            barrier.sType$Default().srcAccessMask(VK_ACCESS_TRANSFER_WRITE_BIT)
                    .dstAccessMask(VK_ACCESS_VERTEX_ATTRIBUTE_READ_BIT | VK_ACCESS_INDEX_READ_BIT
                            | VK_ACCESS_INDIRECT_COMMAND_READ_BIT);
            vkCmdPipelineBarrier(this.commandBuffer.getHandle(), VK_PIPELINE_STAGE_TRANSFER_BIT,
                    VK_PIPELINE_STAGE_VERTEX_INPUT_BIT | VK_PIPELINE_STAGE_DRAW_INDIRECT_BIT,
                    0, barrier, null, null);
        }
        this.queue.submitCommands(this.commandBuffer);
        Synchronization.INSTANCE.addCommandBuffer(this.commandBuffer);
        this.commandBuffer = null;
        this.writes.clear();
    }

    public void recordUpload(Buffer buffer, long dstOffset, long bufferSize, ByteBuffer src) {
        if (bufferSize == 0) return;
        if (bufferSize < 0 || bufferSize > src.remaining() || dstOffset < 0
                || dstOffset > buffer.getBufferSize() - bufferSize)
            throw new IllegalArgumentException("Upload exceeds source or destination buffer");
        beginCommands();
        StagingBuffer staging = Vulkan.getStagingBuffer();
        staging.align(4);
        staging.copyBuffer(Math.toIntExact(bufferSize), src);
        long source = staging.getId(), destination = buffer.getId();

        if (this.writes.overlaps(destination, dstOffset, bufferSize)) {
            flushCopies();
            writeBarrier(VK_ACCESS_TRANSFER_WRITE_BIT);
            this.writes.clear();
        }
        if (this.copies.needsFlush(source, destination)) flushCopies();
        this.copies.add(source, staging.getOffset(), destination, dstOffset, bufferSize);
        this.writes.add(destination, dstOffset, bufferSize);
        if (QA) qaRegions++;
    }

    private void flushCopies() {
        int count = this.copies.count();
        if (count == 0) return;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkBufferCopy.Buffer regions = VkBufferCopy.calloc(count, stack);
            for (int i = 0; i < count; i++) {
                regions.get(i).srcOffset(this.copies.sourceOffset(i))
                        .dstOffset(this.copies.destinationOffset(i)).size(this.copies.size(i));
            }
            vkCmdCopyBuffer(this.commandBuffer.getHandle(), this.copies.source(),
                    this.copies.destination(), regions);
        }
        this.copies.clear();
        if (QA) qaCopyCalls++;
    }

    private void writeBarrier(int destinationAccess) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkMemoryBarrier.Buffer barrier = VkMemoryBarrier.calloc(1, stack);
            barrier.sType$Default();
            barrier.srcAccessMask(VK_ACCESS_TRANSFER_WRITE_BIT);
            barrier.dstAccessMask(destinationAccess);
            vkCmdPipelineBarrier(this.commandBuffer.getHandle(), VK_PIPELINE_STAGE_TRANSFER_BIT,
                    VK_PIPELINE_STAGE_TRANSFER_BIT, 0, barrier, null, null);
        }
        if (QA) qaWriteBarriers++;
    }

    public void copyBuffer(Buffer src, Buffer dst) {
        copyBuffer(src, 0, dst, 0, src.getBufferSize());
    }

    public void copyBuffer(Buffer src, int srcOffset, Buffer dst, int dstOffset, int size) {
        if (size == 0) return;
        if (size < 0 || srcOffset < 0 || dstOffset < 0
                || srcOffset > src.getBufferSize() - size || dstOffset > dst.getBufferSize() - size)
            throw new IllegalArgumentException("Copy exceeds source or destination buffer");
        beginCommands();
        flushCopies();
        // Reallocation/compaction reads earlier uploads and can overwrite an earlier range.
        writeBarrier(VK_ACCESS_TRANSFER_READ_BIT | VK_ACCESS_TRANSFER_WRITE_BIT);
        this.writes.clear();
        TransferQueue.uploadBufferCmd(this.commandBuffer.getHandle(), src.getId(), srcOffset,
                dst.getId(), dstOffset, size);
        this.writes.add(dst.getId(), dstOffset, size);
        if (QA) { qaRegions++; qaCopyCalls++; }
    }

    public void syncUploads() {
        submitUploads();
        Synchronization.INSTANCE.waitFences();
    }

    private void beginCommands() {
        if (this.commandBuffer == null) {
            this.commandBuffer = this.queue.beginCommands();
            // Finish earlier terrain vertex/index/indirect reads before recycling
            // any arena range. One execution dependency per batch retains the
            // grouped copies and allows later graphics stages to keep running.
            try (MemoryStack stack = MemoryStack.stackPush()) {
                VkMemoryBarrier.Buffer barrier = VkMemoryBarrier.calloc(1, stack);
                barrier.sType$Default().srcAccessMask(VK_ACCESS_TRANSFER_WRITE_BIT)
                        .dstAccessMask(VK_ACCESS_TRANSFER_READ_BIT | VK_ACCESS_TRANSFER_WRITE_BIT);
                vkCmdPipelineBarrier(this.commandBuffer.getHandle(),
                        VK_PIPELINE_STAGE_VERTEX_INPUT_BIT | VK_PIPELINE_STAGE_DRAW_INDIRECT_BIT
                                | VK_PIPELINE_STAGE_TRANSFER_BIT,
                        VK_PIPELINE_STAGE_TRANSFER_BIT, 0, barrier, null, null);
            }
        }
    }
}
