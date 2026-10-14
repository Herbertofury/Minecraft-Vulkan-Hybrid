package net.vulkanmod.vulkan.texture;

import it.unimi.dsi.fastutil.shorts.Short2LongMap;
import it.unimi.dsi.fastutil.shorts.Short2LongOpenHashMap;
import net.vulkanmod.vulkan.device.DeviceManager;
import org.apache.commons.lang3.Validate;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkSamplerCreateInfo;
import org.lwjgl.vulkan.VkSamplerReductionModeCreateInfo;

import java.nio.LongBuffer;

import static net.vulkanmod.vulkan.Vulkan.getVkDevice;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.vulkan.VK10.*;
import static org.lwjgl.vulkan.VK12.VK_SAMPLER_REDUCTION_MODE_MAX;
import static org.lwjgl.vulkan.VK12.VK_SAMPLER_REDUCTION_MODE_MIN;

public abstract class SamplerManager {
    static final float MIP_BIAS = -0.5f;

    static final Short2LongMap SAMPLERS = new Short2LongOpenHashMap();

    public static long getTextureSampler(byte maxLod, byte flags) {
        short key = (short) (flags | (maxLod << 8));
        long sampler = SAMPLERS.getOrDefault(key, 0L);

        if (sampler == 0L) {
            sampler = createTextureSampler(maxLod, flags);
            SAMPLERS.put(key, sampler);
        }

        return sampler;
    }

    private static long createTextureSampler(byte maxLod, byte flags) {
        Validate.isTrue(
                (flags & (REDUCTION_MIN_BIT | REDUCTION_MAX_BIT)) != (REDUCTION_MIN_BIT | REDUCTION_MAX_BIT)
        );

        try (MemoryStack stack = stackPush()) {

            VkSamplerCreateInfo samplerInfo = VkSamplerCreateInfo.calloc(stack);
            samplerInfo.sType(VK_STRUCTURE_TYPE_SAMPLER_CREATE_INFO);

            if ((flags & LINEAR_FILTERING_BIT) != 0) {
                samplerInfo.magFilter(VK_FILTER_LINEAR);
                samplerInfo.minFilter(VK_FILTER_LINEAR);
            } else {
                samplerInfo.magFilter(VK_FILTER_NEAREST);
                samplerInfo.minFilter(VK_FILTER_NEAREST);
            }

            if ((flags & CLAMP_BIT) != 0) {
                samplerInfo.addressModeU(VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE);
                samplerInfo.addressModeV(VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE);
                samplerInfo.addressModeW(VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE);
            } else {
                samplerInfo.addressModeU(VK_SAMPLER_ADDRESS_MODE_REPEAT);
                samplerInfo.addressModeV(VK_SAMPLER_ADDRESS_MODE_REPEAT);
                samplerInfo.addressModeW(VK_SAMPLER_ADDRESS_MODE_REPEAT);
            }

            samplerInfo.anisotropyEnable(false);
            //samplerInfo.maxAnisotropy(16.0f);
            samplerInfo.borderColor(VK_BORDER_COLOR_INT_OPAQUE_WHITE);
            samplerInfo.unnormalizedCoordinates(false);
            samplerInfo.compareEnable(false);
            samplerInfo.compareOp(VK_COMPARE_OP_ALWAYS);

            if ((flags & USE_MIPMAPS_BIT) == 0) {
                samplerInfo.mipmapMode(VK_SAMPLER_MIPMAP_MODE_NEAREST);
                samplerInfo.maxLod(0.0F);
                samplerInfo.minLod(0.0F);
            } else {
                if ((flags & MIPMAP_LINEAR_FILTERING_BIT) != 0) {
                    samplerInfo.mipmapMode(VK_SAMPLER_MIPMAP_MODE_LINEAR);
                } else {
                    samplerInfo.mipmapMode(VK_SAMPLER_MIPMAP_MODE_NEAREST);
                }
                samplerInfo.maxLod(maxLod);
                samplerInfo.minLod(0.0F);
                samplerInfo.mipLodBias((flags & UNBIASED_LOD_BIT) != 0 ? 0.0F : MIP_BIAS);
            }

            //Reduction Mode
            if ((flags & (REDUCTION_MAX_BIT | REDUCTION_MIN_BIT)) != 0) {
                VkSamplerReductionModeCreateInfo reductionModeInfo = VkSamplerReductionModeCreateInfo.calloc(stack);
                reductionModeInfo.sType$Default();
                reductionModeInfo.reductionMode((flags & REDUCTION_MAX_BIT) != 0 ? VK_SAMPLER_REDUCTION_MODE_MAX : VK_SAMPLER_REDUCTION_MODE_MIN);
                samplerInfo.pNext(reductionModeInfo.address());
            }

            LongBuffer pTextureSampler = stack.mallocLong(1);

            if (vkCreateSampler(getVkDevice(), samplerInfo, null, pTextureSampler) != VK_SUCCESS) {
                throw new RuntimeException("Failed to create texture sampler");
            }

            return pTextureSampler.get(0);
        }
    }

    /** Immutable sampler identity. Existing flag-based samplers retain their previous semantics. */
    public record Parameters(int minFilter,int magFilter,int addressU,int addressV,int addressW,
                             int mipmapMode,float minLod,float maxLod,float lodBias) {
        public Parameters {
            if((minFilter!=VK_FILTER_NEAREST&&minFilter!=VK_FILTER_LINEAR)
                    ||(magFilter!=VK_FILTER_NEAREST&&magFilter!=VK_FILTER_LINEAR)
                    ||!address(addressU)||!address(addressV)||!address(addressW)
                    ||(mipmapMode!=VK_SAMPLER_MIPMAP_MODE_NEAREST&&mipmapMode!=VK_SAMPLER_MIPMAP_MODE_LINEAR)
                    ||!Float.isFinite(minLod)||!Float.isFinite(maxLod)||!Float.isFinite(lodBias)
                    ||minLod<0||maxLod<minLod)throw new IllegalArgumentException("Invalid native sampler parameters");
        }
        private static boolean address(int value){return value==VK_SAMPLER_ADDRESS_MODE_REPEAT||value==VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE;}
    }
    private static final java.util.Map<Parameters,Long> INDEPENDENT_SAMPLERS=new java.util.HashMap<>();
    public static long getTextureSampler(Parameters parameters) {
        com.mojang.blaze3d.systems.RenderSystem.assertOnRenderThread();
        if(Math.abs(parameters.lodBias())>DeviceManager.deviceProperties.limits().maxSamplerLodBias())
            throw new IllegalArgumentException("Sampler LOD bias exceeds actual device limit");
        return INDEPENDENT_SAMPLERS.computeIfAbsent(parameters,SamplerManager::createTextureSampler);
    }
    private static long createTextureSampler(Parameters p) {
        try(MemoryStack stack=stackPush()) {
            var info=VkSamplerCreateInfo.calloc(stack).sType$Default().minFilter(p.minFilter()).magFilter(p.magFilter())
                    .addressModeU(p.addressU()).addressModeV(p.addressV()).addressModeW(p.addressW())
                    .mipmapMode(p.mipmapMode()).minLod(p.minLod()).maxLod(p.maxLod()).mipLodBias(p.lodBias())
                    .anisotropyEnable(false).compareEnable(false).compareOp(VK_COMPARE_OP_ALWAYS)
                    .borderColor(VK_BORDER_COLOR_INT_OPAQUE_WHITE).unnormalizedCoordinates(false);
            var handle=stack.mallocLong(1);int result=vkCreateSampler(getVkDevice(),info,null,handle);
            if(result!=VK_SUCCESS)throw new IllegalStateException("Native independent sampler creation failed: "+result);
            return handle.get(0);
        }
    }

    public static void cleanUp() {
        for (long id : SAMPLERS.values()) {
            vkDestroySampler(DeviceManager.vkDevice, id, null);
        }
        // Descriptor snapshots may still reference earlier immutable sampler handles during frames.
        // Retire these with the existing device-shutdown cleanup, never on parameter replacement.
        for(long id:INDEPENDENT_SAMPLERS.values())vkDestroySampler(DeviceManager.vkDevice,id,null);
        INDEPENDENT_SAMPLERS.clear();
    }

    public static final byte UNBIASED_LOD_BIT = 0b1000000;



    public static final byte LINEAR_FILTERING_BIT = 0b1;
    public static final byte CLAMP_BIT = 0b10;
    public static final byte USE_MIPMAPS_BIT = 0b100;
    public static final byte MIPMAP_LINEAR_FILTERING_BIT = 0b1000;
    public static final byte REDUCTION_MIN_BIT = 0b10000;
    public static final byte REDUCTION_MAX_BIT = 0b100000;
}
