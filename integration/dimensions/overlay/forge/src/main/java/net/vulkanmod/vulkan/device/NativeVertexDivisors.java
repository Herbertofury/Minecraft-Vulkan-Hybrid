package net.vulkanmod.vulkan.device;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;
import java.util.Set;
import static org.lwjgl.vulkan.VK11.*;
import static org.lwjgl.vulkan.EXTVertexAttributeDivisor.*;

/** Optional real device feature chain; absence never fabricates a divisor capability. */
public final class NativeVertexDivisors {
    private static boolean enabled;private static long maximum=1;
    private NativeVertexDivisors(){}
    static void enable(MemoryStack stack,VkPhysicalDevice physical,VkPhysicalDeviceVulkan11Features chain,Set<String> extensions){
        enabled=false;maximum=1;if(!Device.getAvailableExtensions(physical).contains(VK_EXT_VERTEX_ATTRIBUTE_DIVISOR_EXTENSION_NAME))return;
        var features=VkPhysicalDeviceVertexAttributeDivisorFeaturesEXT.calloc(stack).sType$Default();
        var query=VkPhysicalDeviceFeatures2.calloc(stack).sType$Default().pNext(features.address());vkGetPhysicalDeviceFeatures2(physical,query);
        if(!features.vertexAttributeInstanceRateDivisor())return;
        var props=VkPhysicalDeviceVertexAttributeDivisorPropertiesEXT.calloc(stack).sType$Default();var properties=VkPhysicalDeviceProperties2.calloc(stack).sType$Default().pNext(props.address());vkGetPhysicalDeviceProperties2(physical,properties);
        maximum=Integer.toUnsignedLong(props.maxVertexAttribDivisor());features.vertexAttributeInstanceRateZeroDivisor(false);
        features.pNext(chain.pNext());chain.pNext(features.address());extensions.add(VK_EXT_VERTEX_ATTRIBUTE_DIVISOR_EXTENSION_NAME);enabled=true;
    }
    public static boolean enabled(){return enabled&&DeviceManager.vkDevice!=null;}
    public static long maximum(){return maximum;}
    public static void validate(int divisor){long d=Integer.toUnsignedLong(divisor);if(d>1&&(!enabled()||d>maximum))throw new UnsupportedOperationException("Native device divisor unavailable: "+d);}
}
