package net.vulkanmod.vulkan;

import net.vulkanmod.vulkan.queue.CommandPool;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.*;
import java.lang.reflect.Field;
import java.nio.*;
import java.util.*;
import static org.lwjgl.vulkan.VK10.*;

/** Real discrete GPU transfers/fences, with explicit injected native error results. */
public final class NativeFenceRegression {
    public static VkDevice device;
    static VkQueue queue;
    static VkInstance instance;
    static long pool, buffer, memory, address;
    static int familyIndex;
    static final List<Long> ownedFences = new ArrayList<>();
    static Integer waitError, statusError;
    static final int PATTERN = 0x4D564846;
    public static int resets;

    static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
    static void success(int result, String operation) {
        require(result == VK_SUCCESS, operation + " VkResult=" + result);
    }
    // Only error controls intercept a result. All ordinary waits/status calls reach the driver.
    public static int waitNative(VkDevice d, LongBuffer fences, boolean all, long timeout) {
        if (waitError != null) { int result = waitError; waitError = null; return result; }
        return vkWaitForFences(d, fences, all, timeout);
    }
    public static int waitNative(VkDevice d, long fence, boolean all, long timeout) {
        if (waitError != null) { int result = waitError; waitError = null; return result; }
        return vkWaitForFences(d, fence, all, timeout);
    }
    public static int statusNative(VkDevice d, long fence) {
        if (statusError != null) { int result = statusError; statusError = null; return result; }
        return vkGetFenceStatus(d, fence);
    }
    static void fails(Runnable action, String error) {
        try { action.run(); throw new AssertionError("native failure masked: " + error); }
        catch (IllegalStateException expected) {
            require(expected.getMessage().contains(error), "original native error result preserved");
        }
    }
    static Object field(Synchronization sync, String name) throws Exception {
        Field f = Synchronization.class.getDeclaredField(name); f.setAccessible(true); return f.get(sync);
    }
    static CommandPool.CommandBuffer wrapper(VkCommandBuffer cmd, long fence) throws Exception {
        // The narrow test wrapper has this constructor; production only invokes its existing methods.
        return CommandPool.CommandBuffer.class.getConstructor(VkCommandBuffer.class, long.class).newInstance(cmd, fence);
    }
    static long fence() {
        try (MemoryStack s = MemoryStack.stackPush()) {
            LongBuffer out = s.mallocLong(1);
            success(vkCreateFence(device, VkFenceCreateInfo.calloc(s).sType$Default(), null, out), "create fence");
            long handle = out.get(0); ownedFences.add(handle); return handle;
        }
    }
    static VkCommandBuffer commands() {
        try (MemoryStack s = MemoryStack.stackPush()) {
            PointerBuffer out = s.mallocPointer(1);
            VkCommandBufferAllocateInfo info = VkCommandBufferAllocateInfo.calloc(s).sType$Default()
                    .commandPool(pool).level(VK_COMMAND_BUFFER_LEVEL_PRIMARY).commandBufferCount(1);
            success(vkAllocateCommandBuffers(device, info, out), "allocate native commands");
            VkCommandBuffer cmd = new VkCommandBuffer(out.get(0), device);
            success(vkBeginCommandBuffer(cmd, VkCommandBufferBeginInfo.calloc(s).sType$Default()
                    .flags(VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT)), "begin native commands");
            vkCmdFillBuffer(cmd, buffer, 0, Integer.BYTES, PATTERN);
            success(vkEndCommandBuffer(cmd), "end native commands"); return cmd;
        }
    }
    static void submit(VkCommandBuffer cmd, long fence) {
        try (MemoryStack s = MemoryStack.stackPush()) {
            VkSubmitInfo info = VkSubmitInfo.calloc(s).sType$Default().pCommandBuffers(s.pointers(cmd));
            success(vkQueueSubmit(queue, info, fence), "submit native transfer");
        }
    }
    public static void reset(VkCommandBuffer cmd) {
        success(vkResetCommandBuffer(cmd, 0), "reset completed native command buffer"); resets++;
    }
    static void initialize() {
        try (MemoryStack s = MemoryStack.stackPush()) {
            VkApplicationInfo app = VkApplicationInfo.calloc(s).sType$Default().pApplicationName(s.UTF8("MVH submitted fence QA"))
                    .apiVersion(VK_MAKE_VERSION(1, 2, 0));
            VkInstanceCreateInfo create = VkInstanceCreateInfo.calloc(s).sType$Default().pApplicationInfo(app);
            PointerBuffer out = s.mallocPointer(1); success(vkCreateInstance(create, null, out), "instance");
            instance = new VkInstance(out.get(0), create);
            IntBuffer count = s.ints(0); success(vkEnumeratePhysicalDevices(instance, count, null), "device count");
            PointerBuffer devices = s.mallocPointer(count.get(0)); success(vkEnumeratePhysicalDevices(instance, count, devices), "devices");
            VkPhysicalDevice gpu = null;
            for (int i = 0; i < devices.capacity(); i++) {
                VkPhysicalDevice candidate = new VkPhysicalDevice(devices.get(i), instance);
                VkPhysicalDeviceProperties props = VkPhysicalDeviceProperties.calloc(s); vkGetPhysicalDeviceProperties(candidate, props);
                if (props.deviceType() == VK_PHYSICAL_DEVICE_TYPE_DISCRETE_GPU) {
                    gpu = candidate; System.out.println("GPU=" + props.deviceNameString() + " driver=" + props.driverVersion()); break;
                }
            }
            require(gpu != null, "real discrete GPU required");
            vkGetPhysicalDeviceQueueFamilyProperties(gpu, count, null);
            VkQueueFamilyProperties.Buffer families = VkQueueFamilyProperties.calloc(count.get(0), s);
            vkGetPhysicalDeviceQueueFamilyProperties(gpu, count, families);
            int family = -1;
            for (int i = 0; i < families.capacity(); i++) if ((families.get(i).queueFlags() & VK_QUEUE_GRAPHICS_BIT) != 0) { family = i; break; }
            require(family >= 0, "graphics queue family");
            familyIndex = family;
            VkDeviceQueueCreateInfo.Buffer queues = VkDeviceQueueCreateInfo.calloc(1, s).sType$Default().queueFamilyIndex(family).pQueuePriorities(s.floats(1));
            VkDeviceCreateInfo deviceInfo = VkDeviceCreateInfo.calloc(s).sType$Default().pQueueCreateInfos(queues);
            success(vkCreateDevice(gpu, deviceInfo, null, out), "device"); device = new VkDevice(out.get(0), gpu, deviceInfo);
            vkGetDeviceQueue(device, family, 0, out); queue = new VkQueue(out.get(0), device);
            LongBuffer handle = s.mallocLong(1);
            success(vkCreateCommandPool(device, VkCommandPoolCreateInfo.calloc(s).sType$Default().queueFamilyIndex(family)
                    .flags(VK_COMMAND_POOL_CREATE_RESET_COMMAND_BUFFER_BIT), null, handle), "command pool"); pool = handle.get(0);
            success(vkCreateBuffer(device, VkBufferCreateInfo.calloc(s).sType$Default().size(Integer.BYTES)
                    .usage(VK_BUFFER_USAGE_TRANSFER_DST_BIT).sharingMode(VK_SHARING_MODE_EXCLUSIVE), null, handle), "transfer buffer"); buffer = handle.get(0);
            VkMemoryRequirements requirements = VkMemoryRequirements.calloc(s); vkGetBufferMemoryRequirements(device, buffer, requirements);
            VkPhysicalDeviceMemoryProperties memoryProps = VkPhysicalDeviceMemoryProperties.calloc(s); vkGetPhysicalDeviceMemoryProperties(gpu, memoryProps);
            int memoryType = -1, wanted = VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT;
            for (int i = 0; i < memoryProps.memoryTypeCount(); i++) if ((requirements.memoryTypeBits() & (1 << i)) != 0
                    && (memoryProps.memoryTypes(i).propertyFlags() & wanted) == wanted) { memoryType = i; break; }
            require(memoryType >= 0, "host coherent test transfer memory");
            success(vkAllocateMemory(device, VkMemoryAllocateInfo.calloc(s).sType$Default().allocationSize(requirements.size())
                    .memoryTypeIndex(memoryType), null, handle), "transfer memory"); memory = handle.get(0);
            success(vkBindBufferMemory(device, buffer, memory, 0), "bind transfer memory");
            success(vkMapMemory(device, memory, 0, Integer.BYTES, 0, out), "map transfer memory"); address = out.get(0);
        }
    }
    public static void main(String[] args) throws Exception {
        try {
            initialize();
            Synchronization sync = new Synchronization(50);
            long first = fence(); require(!Synchronization.checkFenceStatus(first), "actual unsubmitted fence is unsignaled");
            MemoryUtil.memPutInt(address, 0); VkCommandBuffer cmd = commands(); submit(cmd, first);
            sync.addCommandBuffer(wrapper(cmd, first)); sync.waitFences();
            require(MemoryUtil.memGetInt(address) == PATTERN, "real GPU transfer visible after native fence");
            require(Synchronization.checkFenceStatus(first), "submitted native fence signaled");
            require(resets == 1, "completed command buffer recycled exactly once"); sync.waitFences(); require(resets == 1, "empty wait does not recycle again");

            if (args.length == 1 && args[0].equals("packaged-success")) {
                long unsignaled = fence(); require(!Synchronization.checkFenceStatus(unsignaled), "packaged pending status stays nonblocking");
                Synchronization.waitFence(first);
                System.out.println("PACKAGED_NATIVE_FENCE_PASS actual production Synchronization bytecode, hardware transfer and submitted fence");
                return;
            }

            long pending = fence(); VkCommandBuffer pendingCmd = commands();
            sync.addCommandBuffer(wrapper(pendingCmd, pending)); waitError = VK_ERROR_DEVICE_LOST;
            fails(sync::waitFences, "VK_ERROR_DEVICE_LOST");
            require(resets == 1, "failed wait does not recycle command buffers");
            require((Integer)field(sync, "idx") == 1, "failed wait retains pending fence");
            require(((java.util.List<?>)field(sync, "commandBuffers")).size() == 1, "failed wait retains pending command buffer");
            require(((LongBuffer)field(sync, "fences")).limit() == 50, "failure restores fence buffer bounds");
            // The device was not actually lost: this control injected only its documented result.
            submit(pendingCmd, pending); sync.waitFences(); require(resets == 2, "retry after injected error retires actual submitted command once");
            waitError = VK_ERROR_OUT_OF_DEVICE_MEMORY; fails(() -> Synchronization.waitFence(first), "VK_ERROR_OUT_OF_DEVICE_MEMORY");
            Synchronization.waitFence(first);
            statusError = VK_ERROR_DEVICE_LOST; fails(() -> Synchronization.checkFenceStatus(first), "VK_ERROR_DEVICE_LOST");
            long unsignaled = fence(); require(!Synchronization.checkFenceStatus(unsignaled), "not-ready stays nonblocking");
            System.out.println("NATIVE_FENCE_PASS real GPU transfer, pending/signaled status, exactly-once reset, retained ownership after injected errors");
        } finally {
            if (device != null) {
                vkDeviceWaitIdle(device);
                for (long fence : ownedFences) vkDestroyFence(device, fence, null);
                if (pool != 0) vkDestroyCommandPool(device, pool, null);
                if (address != 0) vkUnmapMemory(device, memory);
                if (buffer != 0) vkDestroyBuffer(device, buffer, null);
                if (memory != 0) vkFreeMemory(device, memory, null);
                vkDestroyDevice(device, null);
            }
            if (instance != null) vkDestroyInstance(instance, null);
        }
    }
}
