package com.axalotl.async.vulkanruntime;

import com.axalotl.async.common.gpu.vulkan.VulkanCollisionBackend;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.LongBuffer;
import java.util.Arrays;

import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.system.MemoryUtil.memAlloc;
import static org.lwjgl.system.MemoryUtil.memByteBuffer;
import static org.lwjgl.system.MemoryUtil.memFree;
import static org.lwjgl.vulkan.VK10.*;

/**
 * Compile-checked LWJGL 3.3.1 Vulkan backend.
 *
 * This class is loaded from an isolated child JAR. The parent HMT classloader
 * sees only VulkanCollisionBackend, so Forge/ModLauncher never has to resolve
 * lwjgl-vulkan as a module. All Vulkan calls here use LWJGL's real generated
 * wrapper types and are therefore verified by javac instead of reflection.
 */
public final class LwjglVulkanBackend implements VulkanCollisionBackend {
    private static final int INPUT_BINDINGS = 6;
    private static final int BINDING_COUNT = 9;
    private static final int PUSH_BYTES = 16;
    private static final long FENCE_TIMEOUT_NS = 5_000_000_000L;

    private VkInstance instance;
    private VkPhysicalDevice physicalDevice;
    private VkDevice device;
    private VkQueue queue;
    private VkCommandBuffer commandBuffer;
    private int queueFamily = -1;

    private long shaderModule;
    private long descriptorSetLayout;
    private long pipelineLayout;
    private long pipeline;
    private long descriptorPool;
    private long descriptorSet;
    private long commandPool;
    private long fence;

    private GpuBuffer[] inputs;
    private GpuBuffer pairsA;
    private GpuBuffer pairsB;
    private GpuBuffer pairCounter;
    private int entityCapacity;
    private int pairCapacity;

    private volatile boolean operational;
    private volatile boolean deviceLost;
    private volatile String lastError = "";
    private String deviceName = "Unavailable";
    private int maxWorkGroupInvocations;
    private long maxSharedMemoryBytes;
    private long totalDispatches;
    private long lastDispatchNanos;

    @Override
    public synchronized boolean initialize(byte[] spirv) {
        if (operational) return true;
        if (spirv == null || spirv.length == 0 || (spirv.length & 3) != 0) {
            lastError = "Invalid or empty SPIR-V shader";
            return false;
        }

        try {
            // VK auto-loads the system Vulkan loader unless explicit-init was requested.
            try {
                VK.getFunctionProvider();
            } catch (IllegalStateException notInitialized) {
                VK.create();
            }

            createInstance();
            selectPhysicalDevice();
            createDeviceAndQueue();
            createPipeline(spirv);
            createDescriptorPoolAndSet();
            createCommandState();
            operational = true;
            lastError = "";
            return true;
        } catch (Throwable t) {
            lastError = t.getClass().getSimpleName() + ": " + String.valueOf(t.getMessage());
            shutdown();
            return false;
        }
    }

    private void createInstance() {
        try (MemoryStack stack = stackPush()) {
            VkApplicationInfo app = VkApplicationInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_APPLICATION_INFO)
                    .pApplicationName(stack.UTF8("HariMultiThread Ultimate"))
                    .applicationVersion(1)
                    .pEngineName(stack.UTF8("HariMT"))
                    .engineVersion(1)
                    .apiVersion(VK_API_VERSION_1_1);

            VkInstanceCreateInfo ci = VkInstanceCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO)
                    .pApplicationInfo(app);
            PointerBuffer out = stack.mallocPointer(1);
            check(vkCreateInstance(ci, null, out), "vkCreateInstance");
            instance = new VkInstance(out.get(0), ci);
        }
    }

    private void selectPhysicalDevice() {
        boolean allowCpu = Boolean.getBoolean("harimt.vulkan.allowCpuDevice");
        try (MemoryStack stack = stackPush()) {
            IntBuffer count = stack.ints(0);
            check(vkEnumeratePhysicalDevices(instance, count, null), "vkEnumeratePhysicalDevices(count)");
            if (count.get(0) <= 0) throw new IllegalStateException("No Vulkan physical devices");

            PointerBuffer handles = stack.mallocPointer(count.get(0));
            check(vkEnumeratePhysicalDevices(instance, count, handles), "vkEnumeratePhysicalDevices(list)");

            VkPhysicalDevice best = null;
            int bestQueue = -1;
            int bestScore = Integer.MIN_VALUE;
            String bestName = null;
            int bestMaxWg = 0;
            long bestShared = 0;

            for (int i = 0; i < handles.capacity(); i++) {
                VkPhysicalDevice candidate = new VkPhysicalDevice(handles.get(i), instance);
                VkPhysicalDeviceProperties props = VkPhysicalDeviceProperties.calloc(stack);
                vkGetPhysicalDeviceProperties(candidate, props);
                int family = findComputeQueue(candidate, stack);
                if (family < 0) continue;

                int type = props.deviceType();
                int score;
                if (type == VK_PHYSICAL_DEVICE_TYPE_DISCRETE_GPU) score = 1000;
                else if (type == VK_PHYSICAL_DEVICE_TYPE_INTEGRATED_GPU) score = 700;
                else if (type == VK_PHYSICAL_DEVICE_TYPE_VIRTUAL_GPU) score = 400;
                else if (type == VK_PHYSICAL_DEVICE_TYPE_CPU) score = allowCpu ? 100 : Integer.MIN_VALUE;
                else score = 250;
                if (score == Integer.MIN_VALUE) continue;

                // Prefer a compute-only queue when available to reduce contention.
                VkQueueFamilyProperties.Buffer q = queueFamilies(candidate, stack);
                int flags = q.get(family).queueFlags();
                if ((flags & VK_QUEUE_GRAPHICS_BIT) == 0) score += 25;

                if (score > bestScore) {
                    bestScore = score;
                    best = candidate;
                    bestQueue = family;
                    bestName = props.deviceNameString();
                    bestMaxWg = props.limits().maxComputeWorkGroupInvocations();
                    bestShared = Integer.toUnsignedLong(props.limits().maxComputeSharedMemorySize());
                }
            }

            if (best == null) {
                throw new IllegalStateException(allowCpu
                        ? "No usable Vulkan compute device"
                        : "No usable hardware Vulkan compute device (CPU Vulkan disabled)");
            }
            physicalDevice = best;
            queueFamily = bestQueue;
            deviceName = bestName == null ? "Vulkan device" : bestName;
            maxWorkGroupInvocations = bestMaxWg;
            maxSharedMemoryBytes = bestShared;
        }
    }

    private int findComputeQueue(VkPhysicalDevice candidate, MemoryStack stack) {
        VkQueueFamilyProperties.Buffer props = queueFamilies(candidate, stack);
        int fallback = -1;
        for (int i = 0; i < props.capacity(); i++) {
            int flags = props.get(i).queueFlags();
            if ((flags & VK_QUEUE_COMPUTE_BIT) == 0 || props.get(i).queueCount() <= 0) continue;
            if ((flags & VK_QUEUE_GRAPHICS_BIT) == 0) return i;
            if (fallback < 0) fallback = i;
        }
        return fallback;
    }

    private VkQueueFamilyProperties.Buffer queueFamilies(VkPhysicalDevice candidate, MemoryStack stack) {
        IntBuffer count = stack.ints(0);
        vkGetPhysicalDeviceQueueFamilyProperties(candidate, count, null);
        VkQueueFamilyProperties.Buffer props = VkQueueFamilyProperties.calloc(count.get(0), stack);
        vkGetPhysicalDeviceQueueFamilyProperties(candidate, count, props);
        return props;
    }

    private void createDeviceAndQueue() {
        try (MemoryStack stack = stackPush()) {
            FloatBuffer priorities = stack.floats(1.0f);
            VkDeviceQueueCreateInfo.Buffer queueInfos = VkDeviceQueueCreateInfo.calloc(1, stack);
            queueInfos.get(0)
                    .sType(VK_STRUCTURE_TYPE_DEVICE_QUEUE_CREATE_INFO)
                    .queueFamilyIndex(queueFamily)
                    .pQueuePriorities(priorities);

            VkDeviceCreateInfo ci = VkDeviceCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_DEVICE_CREATE_INFO)
                    .pQueueCreateInfos(queueInfos);
            PointerBuffer out = stack.mallocPointer(1);
            check(vkCreateDevice(physicalDevice, ci, null, out), "vkCreateDevice");
            device = new VkDevice(out.get(0), physicalDevice, ci);

            PointerBuffer q = stack.mallocPointer(1);
            vkGetDeviceQueue(device, queueFamily, 0, q);
            queue = new VkQueue(q.get(0), device);
        }
    }

    private void createPipeline(byte[] spirv) {
        ByteBuffer code = memAlloc(spirv.length).order(ByteOrder.nativeOrder());
        try {
            code.put(spirv).flip();
            try (MemoryStack stack = stackPush()) {
                VkShaderModuleCreateInfo shaderInfo = VkShaderModuleCreateInfo.calloc(stack)
                        .sType(VK_STRUCTURE_TYPE_SHADER_MODULE_CREATE_INFO)
                        .pCode(code);
                LongBuffer outShader = stack.mallocLong(1);
                check(vkCreateShaderModule(device, shaderInfo, null, outShader), "vkCreateShaderModule");
                shaderModule = outShader.get(0);

                VkDescriptorSetLayoutBinding.Buffer bindings = VkDescriptorSetLayoutBinding.calloc(BINDING_COUNT, stack);
                for (int i = 0; i < BINDING_COUNT; i++) {
                    bindings.get(i)
                            .binding(i)
                            .descriptorType(VK_DESCRIPTOR_TYPE_STORAGE_BUFFER)
                            .descriptorCount(1)
                            .stageFlags(VK_SHADER_STAGE_COMPUTE_BIT);
                }
                VkDescriptorSetLayoutCreateInfo layoutInfo = VkDescriptorSetLayoutCreateInfo.calloc(stack)
                        .sType(VK_STRUCTURE_TYPE_DESCRIPTOR_SET_LAYOUT_CREATE_INFO)
                        .pBindings(bindings);
                LongBuffer outLayout = stack.mallocLong(1);
                check(vkCreateDescriptorSetLayout(device, layoutInfo, null, outLayout), "vkCreateDescriptorSetLayout");
                descriptorSetLayout = outLayout.get(0);

                VkPushConstantRange.Buffer push = VkPushConstantRange.calloc(1, stack);
                push.get(0).stageFlags(VK_SHADER_STAGE_COMPUTE_BIT).offset(0).size(PUSH_BYTES);
                VkPipelineLayoutCreateInfo pipelineLayoutInfo = VkPipelineLayoutCreateInfo.calloc(stack)
                        .sType(VK_STRUCTURE_TYPE_PIPELINE_LAYOUT_CREATE_INFO)
                        .pSetLayouts(stack.longs(descriptorSetLayout))
                        .pPushConstantRanges(push);
                LongBuffer outPipelineLayout = stack.mallocLong(1);
                check(vkCreatePipelineLayout(device, pipelineLayoutInfo, null, outPipelineLayout), "vkCreatePipelineLayout");
                pipelineLayout = outPipelineLayout.get(0);

                VkPipelineShaderStageCreateInfo stage = VkPipelineShaderStageCreateInfo.calloc(stack)
                        .sType(VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO)
                        .stage(VK_SHADER_STAGE_COMPUTE_BIT)
                        .module(shaderModule)
                        .pName(stack.UTF8("main"));
                VkComputePipelineCreateInfo.Buffer pipelineInfo = VkComputePipelineCreateInfo.calloc(1, stack);
                pipelineInfo.get(0)
                        .sType(VK_STRUCTURE_TYPE_COMPUTE_PIPELINE_CREATE_INFO)
                        .stage(stage)
                        .layout(pipelineLayout);
                LongBuffer outPipeline = stack.mallocLong(1);
                check(vkCreateComputePipelines(device, VK_NULL_HANDLE, pipelineInfo, null, outPipeline), "vkCreateComputePipelines");
                pipeline = outPipeline.get(0);
            }
        } finally {
            memFree(code);
        }
    }

    private void createDescriptorPoolAndSet() {
        try (MemoryStack stack = stackPush()) {
            VkDescriptorPoolSize.Buffer sizes = VkDescriptorPoolSize.calloc(1, stack);
            sizes.get(0).type(VK_DESCRIPTOR_TYPE_STORAGE_BUFFER).descriptorCount(BINDING_COUNT);
            VkDescriptorPoolCreateInfo poolInfo = VkDescriptorPoolCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_DESCRIPTOR_POOL_CREATE_INFO)
                    .maxSets(1)
                    .pPoolSizes(sizes);
            LongBuffer outPool = stack.mallocLong(1);
            check(vkCreateDescriptorPool(device, poolInfo, null, outPool), "vkCreateDescriptorPool");
            descriptorPool = outPool.get(0);

            VkDescriptorSetAllocateInfo alloc = VkDescriptorSetAllocateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_DESCRIPTOR_SET_ALLOCATE_INFO)
                    .descriptorPool(descriptorPool)
                    .pSetLayouts(stack.longs(descriptorSetLayout));
            LongBuffer outSet = stack.mallocLong(1);
            check(vkAllocateDescriptorSets(device, alloc, outSet), "vkAllocateDescriptorSets");
            descriptorSet = outSet.get(0);
        }
    }

    private void createCommandState() {
        try (MemoryStack stack = stackPush()) {
            VkCommandPoolCreateInfo poolInfo = VkCommandPoolCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_COMMAND_POOL_CREATE_INFO)
                    .queueFamilyIndex(queueFamily)
                    .flags(VK_COMMAND_POOL_CREATE_RESET_COMMAND_BUFFER_BIT);
            LongBuffer outPool = stack.mallocLong(1);
            check(vkCreateCommandPool(device, poolInfo, null, outPool), "vkCreateCommandPool");
            commandPool = outPool.get(0);

            VkCommandBufferAllocateInfo alloc = VkCommandBufferAllocateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_COMMAND_BUFFER_ALLOCATE_INFO)
                    .commandPool(commandPool)
                    .level(VK_COMMAND_BUFFER_LEVEL_PRIMARY)
                    .commandBufferCount(1);
            PointerBuffer outCmd = stack.mallocPointer(1);
            check(vkAllocateCommandBuffers(device, alloc, outCmd), "vkAllocateCommandBuffers");
            commandBuffer = new VkCommandBuffer(outCmd.get(0), device);

            VkFenceCreateInfo fenceInfo = VkFenceCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_FENCE_CREATE_INFO);
            LongBuffer outFence = stack.mallocLong(1);
            check(vkCreateFence(device, fenceInfo, null, outFence), "vkCreateFence");
            fence = outFence.get(0);
        }
    }

    @Override
    public synchronized Result compute(
            float[] minX, float[] minY, float[] minZ,
            float[] maxX, float[] maxY, float[] maxZ,
            int count, int maxPairs) {
        if (!operational || deviceLost) throw new IllegalStateException("Vulkan backend unavailable");
        if (count < 0 || maxPairs <= 0) throw new IllegalArgumentException("Invalid compute sizes");
        if (minX.length < count || minY.length < count || minZ.length < count
                || maxX.length < count || maxY.length < count || maxZ.length < count) {
            throw new IllegalArgumentException("AABB input arrays shorter than entity count");
        }
        if (count < 2) return new Result(new int[0], new int[0], 0, false, 0L);

        try {
            ensureBuffers(count, maxPairs);
            upload(inputs[0], minX, count);
            upload(inputs[1], minY, count);
            upload(inputs[2], minZ, count);
            upload(inputs[3], maxX, count);
            upload(inputs[4], maxY, count);
            upload(inputs[5], maxZ, count);
            pairCounter.mapped.putInt(0, 0);

            long start = System.nanoTime();
            dispatch(count, maxPairs);
            long elapsed = System.nanoTime() - start;
            lastDispatchNanos = elapsed;
            totalDispatches++;

            int pairCount = pairCounter.mapped.getInt(0);
            if (pairCount < 0) throw new IllegalStateException("Negative GPU pair counter: " + pairCount);
            if (pairCount > maxPairs) {
                return new Result(new int[0], new int[0], pairCount, true, elapsed);
            }

            int[] outA = new int[pairCount];
            int[] outB = new int[pairCount];
            IntBuffer a = pairsA.mapped.duplicate().order(ByteOrder.nativeOrder()).asIntBuffer();
            IntBuffer b = pairsB.mapped.duplicate().order(ByteOrder.nativeOrder()).asIntBuffer();
            a.get(outA, 0, pairCount);
            b.get(outB, 0, pairCount);
            return new Result(outA, outB, pairCount, false, elapsed);
        } catch (RuntimeException e) {
            lastError = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            throw e;
        }
    }

    private void upload(GpuBuffer buffer, float[] values, int count) {
        FloatBuffer view = buffer.mapped.duplicate().order(ByteOrder.nativeOrder()).asFloatBuffer();
        view.put(values, 0, count);
    }

    private void ensureBuffers(int count, int maxPairs) {
        if (inputs != null && count <= entityCapacity && maxPairs <= pairCapacity) return;
        int newEntityCapacity = Math.max(64, entityCapacity == 0 ? 512 : entityCapacity);
        while (newEntityCapacity < count) newEntityCapacity = Math.multiplyExact(newEntityCapacity, 2);
        int newPairCapacity = Math.max(1024, pairCapacity == 0 ? 16384 : pairCapacity);
        while (newPairCapacity < maxPairs) newPairCapacity = Math.multiplyExact(newPairCapacity, 2);

        destroyBuffers();
        inputs = new GpuBuffer[INPUT_BINDINGS];
        long inputBytes = Math.multiplyExact((long) newEntityCapacity, Float.BYTES);
        for (int i = 0; i < INPUT_BINDINGS; i++) inputs[i] = createHostBuffer(inputBytes);
        long pairBytes = Math.multiplyExact((long) newPairCapacity, Integer.BYTES);
        pairsA = createHostBuffer(pairBytes);
        pairsB = createHostBuffer(pairBytes);
        pairCounter = createHostBuffer(Integer.BYTES);
        entityCapacity = newEntityCapacity;
        pairCapacity = newPairCapacity;
        updateDescriptors();
    }

    private GpuBuffer createHostBuffer(long size) {
        if (size <= 0 || size > Integer.MAX_VALUE) throw new IllegalArgumentException("Unsupported buffer size: " + size);
        try (MemoryStack stack = stackPush()) {
            VkBufferCreateInfo ci = VkBufferCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_BUFFER_CREATE_INFO)
                    .size(size)
                    .usage(VK_BUFFER_USAGE_STORAGE_BUFFER_BIT)
                    .sharingMode(VK_SHARING_MODE_EXCLUSIVE);
            LongBuffer outBuffer = stack.mallocLong(1);
            check(vkCreateBuffer(device, ci, null, outBuffer), "vkCreateBuffer");
            long buffer = outBuffer.get(0);

            VkMemoryRequirements req = VkMemoryRequirements.calloc(stack);
            vkGetBufferMemoryRequirements(device, buffer, req);
            int memoryType = findHostMemoryType(req.memoryTypeBits(), stack);
            VkMemoryAllocateInfo ai = VkMemoryAllocateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO)
                    .allocationSize(req.size())
                    .memoryTypeIndex(memoryType);
            LongBuffer outMemory = stack.mallocLong(1);
            int allocResult = vkAllocateMemory(device, ai, null, outMemory);
            if (allocResult != VK_SUCCESS) {
                vkDestroyBuffer(device, buffer, null);
                check(allocResult, "vkAllocateMemory");
            }
            long memory = outMemory.get(0);
            int bindResult = vkBindBufferMemory(device, buffer, memory, 0L);
            if (bindResult != VK_SUCCESS) {
                vkFreeMemory(device, memory, null);
                vkDestroyBuffer(device, buffer, null);
                check(bindResult, "vkBindBufferMemory");
            }

            PointerBuffer mapped = stack.mallocPointer(1);
            int mapResult = vkMapMemory(device, memory, 0L, size, 0, mapped);
            if (mapResult != VK_SUCCESS) {
                vkFreeMemory(device, memory, null);
                vkDestroyBuffer(device, buffer, null);
                check(mapResult, "vkMapMemory");
            }
            ByteBuffer view = memByteBuffer(mapped.get(0), (int) size).order(ByteOrder.nativeOrder());
            return new GpuBuffer(buffer, memory, size, view);
        }
    }

    private int findHostMemoryType(int typeBits, MemoryStack stack) {
        VkPhysicalDeviceMemoryProperties props = VkPhysicalDeviceMemoryProperties.calloc(stack);
        vkGetPhysicalDeviceMemoryProperties(physicalDevice, props);
        int required = VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT;
        for (int i = 0; i < props.memoryTypeCount(); i++) {
            if ((typeBits & (1 << i)) != 0
                    && (props.memoryTypes(i).propertyFlags() & required) == required) {
                return i;
            }
        }
        throw new IllegalStateException("No host-visible coherent Vulkan memory type");
    }

    private void updateDescriptors() {
        GpuBuffer[] all = {
                inputs[0], inputs[1], inputs[2], inputs[3], inputs[4], inputs[5],
                pairsA, pairsB, pairCounter
        };
        try (MemoryStack stack = stackPush()) {
            VkDescriptorBufferInfo.Buffer infos = VkDescriptorBufferInfo.calloc(BINDING_COUNT, stack);
            VkWriteDescriptorSet.Buffer writes = VkWriteDescriptorSet.calloc(BINDING_COUNT, stack);
            for (int i = 0; i < BINDING_COUNT; i++) {
                infos.get(i).buffer(all[i].buffer).offset(0L).range(all[i].size);
                writes.get(i)
                        .sType(VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET)
                        .dstSet(descriptorSet)
                        .dstBinding(i)
                        .descriptorType(VK_DESCRIPTOR_TYPE_STORAGE_BUFFER)
                        .pBufferInfo(VkDescriptorBufferInfo.create(infos.get(i).address(), 1));
            }
            vkUpdateDescriptorSets(device, writes, null);
        }
    }

    private void dispatch(int count, int maxPairs) {
        try (MemoryStack stack = stackPush()) {
            check(vkResetCommandBuffer(commandBuffer, 0), "vkResetCommandBuffer");
            VkCommandBufferBeginInfo begin = VkCommandBufferBeginInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO)
                    .flags(VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT);
            check(vkBeginCommandBuffer(commandBuffer, begin), "vkBeginCommandBuffer");

            vkCmdBindPipeline(commandBuffer, VK_PIPELINE_BIND_POINT_COMPUTE, pipeline);
            vkCmdBindDescriptorSets(
                    commandBuffer,
                    VK_PIPELINE_BIND_POINT_COMPUTE,
                    pipelineLayout,
                    0,
                    stack.longs(descriptorSet),
                    null);

            ByteBuffer push = stack.malloc(PUSH_BYTES).order(ByteOrder.nativeOrder());
            push.putInt(count).putInt(maxPairs).putFloat(0.0f).putInt(0).flip();
            vkCmdPushConstants(commandBuffer, pipelineLayout, VK_SHADER_STAGE_COMPUTE_BIT, 0, push);
            vkCmdDispatch(commandBuffer, (count + 63) / 64, 1, 1);
            check(vkEndCommandBuffer(commandBuffer), "vkEndCommandBuffer");

            LongBuffer fences = stack.longs(fence);
            check(vkResetFences(device, fences), "vkResetFences");
            VkSubmitInfo submit = VkSubmitInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_SUBMIT_INFO)
                    .pCommandBuffers(stack.pointers(commandBuffer.address()));
            check(vkQueueSubmit(queue, submit, fence), "vkQueueSubmit");
            check(vkWaitForFences(device, fences, true, FENCE_TIMEOUT_NS), "vkWaitForFences");
        }
    }

    private void destroyBuffers() {
        if (device == null) {
            inputs = null;
            pairsA = pairsB = pairCounter = null;
            entityCapacity = pairCapacity = 0;
            return;
        }
        if (inputs != null) {
            Arrays.stream(inputs).forEach(this::destroyBuffer);
        }
        destroyBuffer(pairsA);
        destroyBuffer(pairsB);
        destroyBuffer(pairCounter);
        inputs = null;
        pairsA = pairsB = pairCounter = null;
        entityCapacity = pairCapacity = 0;
    }

    private void destroyBuffer(GpuBuffer buffer) {
        if (buffer == null) return;
        vkUnmapMemory(device, buffer.memory);
        vkDestroyBuffer(device, buffer.buffer, null);
        vkFreeMemory(device, buffer.memory, null);
    }

    private void check(int result, String op) {
        if (result == VK_SUCCESS) return;
        if (result == VK_ERROR_DEVICE_LOST) deviceLost = true;
        throw new IllegalStateException(op + " failed with VkResult " + result);
    }

    @Override
    public synchronized void shutdown() {
        operational = false;
        if (device != null) {
            try { vkDeviceWaitIdle(device); } catch (Throwable ignored) { }
            destroyBuffers();
            if (fence != VK_NULL_HANDLE) vkDestroyFence(device, fence, null);
            if (commandPool != VK_NULL_HANDLE) vkDestroyCommandPool(device, commandPool, null);
            if (descriptorPool != VK_NULL_HANDLE) vkDestroyDescriptorPool(device, descriptorPool, null);
            if (pipeline != VK_NULL_HANDLE) vkDestroyPipeline(device, pipeline, null);
            if (pipelineLayout != VK_NULL_HANDLE) vkDestroyPipelineLayout(device, pipelineLayout, null);
            if (descriptorSetLayout != VK_NULL_HANDLE) vkDestroyDescriptorSetLayout(device, descriptorSetLayout, null);
            if (shaderModule != VK_NULL_HANDLE) vkDestroyShaderModule(device, shaderModule, null);
            try { vkDestroyDevice(device, null); } catch (Throwable ignored) { }
        }
        if (instance != null) {
            try { vkDestroyInstance(instance, null); } catch (Throwable ignored) { }
        }

        instance = null;
        physicalDevice = null;
        device = null;
        queue = null;
        commandBuffer = null;
        queueFamily = -1;
        shaderModule = descriptorSetLayout = pipelineLayout = pipeline = 0L;
        descriptorPool = descriptorSet = commandPool = fence = 0L;
        deviceName = "Unavailable";
        maxWorkGroupInvocations = 0;
        maxSharedMemoryBytes = 0;

        try { VK.destroy(); } catch (Throwable ignored) { }
    }

    @Override public boolean isOperational() { return operational && !deviceLost; }
    @Override public boolean isDeviceLost() { return deviceLost; }
    @Override public String lastError() { return lastError == null ? "" : lastError; }
    @Override public String deviceName() { return deviceName; }
    @Override public int maxWorkGroupInvocations() { return maxWorkGroupInvocations; }
    @Override public long maxSharedMemoryBytes() { return maxSharedMemoryBytes; }
    @Override public long totalDispatches() { return totalDispatches; }
    @Override public long lastDispatchNanos() { return lastDispatchNanos; }

    private record GpuBuffer(long buffer, long memory, long size, ByteBuffer mapped) {}
}
