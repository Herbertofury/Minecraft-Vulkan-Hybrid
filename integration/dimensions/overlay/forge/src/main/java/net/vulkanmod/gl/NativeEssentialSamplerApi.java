package net.vulkanmod.gl;
import com.mojang.blaze3d.systems.RenderSystem;
import net.vulkanmod.vulkan.device.DeviceManager;
import net.vulkanmod.vulkan.texture.*;
import org.lwjgl.system.MemoryStack;import org.lwjgl.vulkan.VkFormatProperties;
import java.util.*;import static org.lwjgl.vulkan.VK10.*;
/** Original typed sampler calls configure actual immutable VkSamplers; image identity prevents recycled-name aliasing. */
public final class NativeEssentialSamplerApi {
    private record State(int wrapU,int wrapV,int minFilter,int magFilter,float maxLod) {}
    private static final Map<VulkanImage,State> STATES=new WeakHashMap<>();
    private NativeEssentialSamplerApi(){}
    private static VulkanImage image(){RenderSystem.assertOnRenderThread();var image=NativeEssentialImageApi.image(GlTexture.getBoundTextureId(0x0DE1));if(!image.hasUsage(VK_IMAGE_USAGE_SAMPLED_BIT))throw new IllegalArgumentException("Typed sampler image is not sampleable");return image;}
    private static State initial(VulkanImage image){return new State(0x2901,0x2901,image.mipLevels>1?0x2702:0x2600,0x2600,image.mipLevels-1);}
    private static int address(int value){return switch(value){case 0x2901->VK_SAMPLER_ADDRESS_MODE_REPEAT;case 0x812F->VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE;default->throw new IllegalArgumentException("Unimplemented typed sampler address mode: "+value);};}
    private static int filter(int value){return switch(value){case 0x2600,0x2700,0x2702->VK_FILTER_NEAREST;case 0x2601,0x2701,0x2703->VK_FILTER_LINEAR;default->throw new IllegalArgumentException("Unimplemented typed min filter: "+value);};}
    private static void apply(VulkanImage image,State state){
        int min=filter(state.minFilter),mag=filter(state.magFilter);address(state.wrapU);address(state.wrapV);
        if(min==VK_FILTER_LINEAR||mag==VK_FILTER_LINEAR){try(MemoryStack stack=MemoryStack.stackPush()){var p=VkFormatProperties.calloc(stack);vkGetPhysicalDeviceFormatProperties(DeviceManager.physicalDevice,image.format,p);if((p.optimalTilingFeatures()&VK_FORMAT_FEATURE_SAMPLED_IMAGE_FILTER_LINEAR_BIT)==0)throw new UnsupportedOperationException("Actual image format cannot use requested linear sampling");}}
        boolean mips=state.minFilter>=0x2700&&state.minFilter<=0x2703;
        var p=new SamplerManager.Parameters(min,mag,address(state.wrapU),address(state.wrapV),VK_SAMPLER_ADDRESS_MODE_REPEAT,
                state.minFilter==0x2702||state.minFilter==0x2703?VK_SAMPLER_MIPMAP_MODE_LINEAR:VK_SAMPLER_MIPMAP_MODE_NEAREST,
                // Khronos' GL_NEAREST/GL_LINEAR mapping keeps lambda positive for minification
                // while nearest mip selection still rounds to level zero.
                0,mips?Math.min(state.maxLod,image.mipLevels-1):0.25f,0);
        image.updateTextureSampler(p);STATES.put(image,state);
    }
    public static void configureTexture(int name,Runnable action){
        RenderSystem.assertOnRenderThread();if(action==null)throw new IllegalArgumentException("Original sampler action required");var target=NativeEssentialImageApi.image(name);if(!target.hasUsage(VK_IMAGE_USAGE_SAMPLED_BIT))throw new IllegalArgumentException("Typed sampler image usage");int oldName=GlTexture.getBoundTextureId(0x0DE1),oldUnit=GlTexture.getActiveTexture()-0x84C0;var oldImage=VTextureSelector.getImage(oldUnit);GlTexture.bindTexture(name);
        try{action.run();}finally{GlTexture.bindTexture(oldName);VTextureSelector.bindTexture(oldUnit,oldImage);}
    }
    public static void glTexParameteri(int target,int parameter,int value){
        if(target!=0x0DE1)throw new IllegalArgumentException("Typed sampler target");VulkanImage image=image();State s=STATES.getOrDefault(image,initial(image));
        State next=switch(parameter){case 0x2802->{address(value);yield new State(value,s.wrapV,s.minFilter,s.magFilter,s.maxLod);}case 0x2803->{address(value);yield new State(s.wrapU,value,s.minFilter,s.magFilter,s.maxLod);}case 0x2801->{filter(value);yield new State(s.wrapU,s.wrapV,value,s.magFilter,s.maxLod);}case 0x2800->{if(value!=0x2600&&value!=0x2601)throw new IllegalArgumentException("Typed mag filter");yield new State(s.wrapU,s.wrapV,s.minFilter,value,s.maxLod);}default->throw new IllegalArgumentException("Unimplemented typed sampler integer parameter: "+parameter);};apply(image,next);
    }
    public static void glTexParameterf(int target,int parameter,float value){if(target!=0x0DE1||parameter!=0x813B||!Float.isFinite(value)||value<0)throw new IllegalArgumentException("Typed sampler maximum LOD");VulkanImage image=image();State s=STATES.getOrDefault(image,initial(image));apply(image,new State(s.wrapU,s.wrapV,s.minFilter,s.magFilter,value));}
    public static Map<String,Number> snapshot(int name){var image=NativeEssentialImageApi.image(name);var s=STATES.getOrDefault(image,initial(image));return Map.of("sampler",image.getSampler(),"wrapU",s.wrapU,"wrapV",s.wrapV,"minFilter",s.minFilter,"magFilter",s.magFilter,"maxLod",s.maxLod);}
}
