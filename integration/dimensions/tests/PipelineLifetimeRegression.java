package net.vulkanmod.vulkan.shader;

import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;
import java.nio.*;
import java.util.*;
import static org.lwjgl.vulkan.VK10.*;

/** Real shaderc results and hardware Vulkan shader modules; no Minecraft rendering claim. */
public final class PipelineLifetimeRegression {
    static VkDevice device;
    static final Set<Long> liveModules = new HashSet<>();
    static boolean failFragment;
    static int moduleCalls;
    static final RuntimeException FAILURE = new RuntimeException("original fragment-module failure");
    static final String VERTEX = "#version 450\nvoid main(){gl_Position=vec4(0,0,0,1);}";
    static final String FRAGMENT = "#version 450\nlayout(location=0) out vec4 c;void main(){c=vec4(1);}";
    static void require(boolean value, String why) { if (!value) throw new AssertionError(why); }
    static long createModule(ByteBuffer code) {
        if (failFragment && ++moduleCalls==2) throw FAILURE;
        try (MemoryStack stack=MemoryStack.stackPush()) {
            VkShaderModuleCreateInfo info=VkShaderModuleCreateInfo.calloc(stack).sType$Default().pCode(code);
            LongBuffer result=stack.mallocLong(1);
            require(vkCreateShaderModule(device,info,null,result)==VK_SUCCESS,"hardware shader module");
            long handle=result.get(0);require(liveModules.add(handle),"unique live module");return handle;
        }
    }
    static void destroyModule(long handle) {
        require(liveModules.remove(handle),"module release once");vkDestroyShaderModule(device,handle,null);
    }
    static void empty(String where) {
        require(SPIRVUtils.liveTestResults()==0,"unreleased compiler result after "+where);
        require(liveModules.isEmpty(),"unreleased Vulkan shader module after "+where);
    }
    public static void main(String[] args) throws Exception {
        VkInstance instance;
        try (MemoryStack stack=MemoryStack.stackPush()) {
            VkApplicationInfo app=VkApplicationInfo.calloc(stack).sType$Default().pApplicationName(stack.UTF8("MVH shader lifetime QA")).apiVersion(VK_MAKE_VERSION(1,2,0));
            VkInstanceCreateInfo info=VkInstanceCreateInfo.calloc(stack).sType$Default().pApplicationInfo(app);
            PointerBuffer pointer=stack.mallocPointer(1);require(vkCreateInstance(info,null,pointer)==VK_SUCCESS,"Vulkan instance");
            instance=new VkInstance(pointer.get(0),info);
        }
        try {
            try (MemoryStack stack=MemoryStack.stackPush()) {
                IntBuffer count=stack.ints(0);require(vkEnumeratePhysicalDevices(instance,count,null)==VK_SUCCESS,"device count");
                PointerBuffer devices=stack.mallocPointer(count.get(0));require(vkEnumeratePhysicalDevices(instance,count,devices)==VK_SUCCESS,"device enumeration");
                VkPhysicalDevice selected=null;
                for(int i=0;i<count.get(0);i++) {
                    VkPhysicalDevice candidate=new VkPhysicalDevice(devices.get(i),instance);
                    VkPhysicalDeviceProperties props=VkPhysicalDeviceProperties.calloc(stack);vkGetPhysicalDeviceProperties(candidate,props);
                    if(props.deviceType()==VK_PHYSICAL_DEVICE_TYPE_DISCRETE_GPU) {selected=candidate;System.out.println("GPU="+props.deviceNameString()+" vendor="+props.vendorID()+" driver="+props.driverVersion());break;}
                }
                require(selected!=null,"physical discrete GPU required; software driver is not evidence");
                IntBuffer families=stack.ints(0);vkGetPhysicalDeviceQueueFamilyProperties(selected,families,null);
                VkQueueFamilyProperties.Buffer props=VkQueueFamilyProperties.calloc(families.get(0),stack);vkGetPhysicalDeviceQueueFamilyProperties(selected,families,props);
                int family=-1;for(int i=0;i<props.capacity();i++)if((props.get(i).queueFlags()&VK_QUEUE_GRAPHICS_BIT)!=0){family=i;break;}
                require(family>=0,"graphics queue");
                VkDeviceQueueCreateInfo.Buffer queue=VkDeviceQueueCreateInfo.calloc(1,stack).sType$Default().queueFamilyIndex(family).pQueuePriorities(stack.floats(1));
                VkDeviceCreateInfo info=VkDeviceCreateInfo.calloc(stack).sType$Default().pQueueCreateInfos(queue);
                PointerBuffer result=stack.mallocPointer(1);require(vkCreateDevice(selected,info,null,result)==VK_SUCCESS,"logical hardware device");device=new VkDevice(result.get(0),selected,info);
            }
            try {
                Pipeline.Builder builder=new Pipeline.Builder();builder.compileShaders("native",VERTEX,FRAGMENT);
                GraphicsPipeline pipeline=builder.createGraphicsPipeline();pipeline.close();empty("successful module creation");
                builder=new Pipeline.Builder();
                try {builder.compileShaders("bad-fragment",VERTEX,"#version 450\ninvalid");throw new AssertionError("bad fragment compiled");}
                catch(RuntimeException expected){require(expected.getMessage().contains("bad-fragment.fsh"),"preserve compile failure");}
                empty("fragment compilation failure");
                builder=new Pipeline.Builder();builder.compileShaders("missing-bindings",VERTEX,FRAGMENT);builder.imageDescriptors=null;
                try {builder.createGraphicsPipeline();throw new AssertionError("missing binding accepted");}catch(IllegalArgumentException expected){}
                empty("binding validation failure");
                builder=new Pipeline.Builder();builder.compileShaders("old",VERTEX,FRAGMENT);builder.compileShaders("replacement",VERTEX,FRAGMENT);
                pipeline=builder.createGraphicsPipeline();pipeline.close();empty("replacing compiled stages");
                builder=new Pipeline.Builder();builder.compileShaders("module-failure",VERTEX,FRAGMENT);failFragment=true;moduleCalls=0;
                try {builder.createGraphicsPipeline();throw new AssertionError("module failure swallowed");}catch(RuntimeException expected){require(expected==FAILURE,"original native failure identity");}
                empty("fragment module failure");
                System.out.println("NATIVE_PIPELINE_LIFETIME_PASS real hardware modules success invalid compile validation replacement fragment-module failure");
            } finally {
                for(long module:new ArrayList<>(liveModules))destroyModule(module);
                vkDestroyDevice(device,null);
            }
        } finally {vkDestroyInstance(instance,null);}
    }
}
