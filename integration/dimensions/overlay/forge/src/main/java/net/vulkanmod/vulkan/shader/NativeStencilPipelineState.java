package net.vulkanmod.vulkan.shader;

import net.vulkanmod.vulkan.texture.VulkanImage;
import org.lwjgl.vulkan.VkPipelineDepthStencilStateCreateInfo;
import org.lwjgl.vulkan.VkStencilOpState;
import static org.lwjgl.vulkan.VK10.VK_FORMAT_UNDEFINED;

/** Apply encoded GL stencil operations to the native Vulkan pipeline without dropping clipping. */
public final class NativeStencilPipelineState {
    private NativeStencilPipelineState() {}

    public static void apply(VkPipelineDepthStencilStateCreateInfo target, int encoded, int depthFormat) {
        boolean present = VulkanImage.hasStencilComponent(depthFormat);
        target.stencilTestEnable(present && PipelineState.StencilState.stencilTest(encoded));
        configure(target.front(), encoded);
        configure(target.back(), encoded);
    }

    private static void configure(VkStencilOpState face, int encoded) {
        face.failOp(PipelineState.StencilState.decodeFailOp(encoded));
        face.passOp(PipelineState.StencilState.decodePassOp(encoded));
        face.depthFailOp(PipelineState.StencilState.decodeDepthFailOp(encoded));
        face.compareOp(PipelineState.StencilState.decodeCompareOp(encoded));
        // The existing renderer supplies these values before each stencil draw as dynamic state.
        face.compareMask(0xff);
        face.writeMask(0xff);
        face.reference(0);
    }

    public static int attachmentFormat(int depthFormat) {
        return VulkanImage.hasStencilComponent(depthFormat) ? depthFormat : VK_FORMAT_UNDEFINED;
    }
}
