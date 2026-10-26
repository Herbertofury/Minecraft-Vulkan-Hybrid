package net.vulkanmod.gl;

import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import net.vulkanmod.vulkan.Renderer;
import net.vulkanmod.vulkan.Vulkan;
import net.vulkanmod.vulkan.texture.VulkanImage;
import org.lwjgl.opengl.GL11;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.lwjgl.vulkan.VkFormatProperties;
import org.lwjgl.vulkan.VkImageBlit;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicLong;
import static org.lwjgl.vulkan.VK10.*;

/** Same-queue native translation of single-attachment GL framebuffer blits.
 * Unsupported fractional clipping and aliased regions fail before recording;
 * neither is silently dropped or advertised as a completed GL contract.
 */
public final class GlFramebufferBlit {
    private static final Int2IntOpenHashMap FEATURES = new Int2IntOpenHashMap();
    public static final AtomicLong COLOR_BLITS = new AtomicLong();
    public static final AtomicLong DEPTH_STENCIL_BLITS = new AtomicLong();
    private GlFramebufferBlit() {}

    public static void blit(int sx0, int sy0, int sx1, int sy1,
                            int dx0, int dy0, int dx1, int dy1, int mask, int filter) {
        blitNamed(GlFramebuffer.getReadId(), GlFramebuffer.getBoundId(), sx0, sy0, sx1, sy1, dx0, dy0, dx1, dy1, mask, filter);
    }

    public static void blitNamed(int read, int draw, int sx0, int sy0, int sx1, int sy1,
                                 int dx0, int dy0, int dx1, int dy1, int mask, int filter) {
        int allowed = GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT | GL11.GL_STENCIL_BUFFER_BIT;
        if ((mask & ~allowed) != 0 || (filter != GL11.GL_NEAREST && filter != GL11.GL_LINEAR))
            throw new IllegalArgumentException("Invalid framebuffer blit mask/filter");
        if (mask == 0 || sx0 == sx1 || sy0 == sy1 || dx0 == dx1 || dy0 == dy1) return;
        if (!Renderer.isRecording()) throw new IllegalStateException("Framebuffer blit outside native command recording");
        ArrayList<Copy> copies = new ArrayList<>(3);
        if ((mask & GL11.GL_COLOR_BUFFER_BIT) != 0)
            add(copies, read, draw, false, VK_IMAGE_ASPECT_COLOR_BIT, sx0, sy0, sx1, sy1, dx0, dy0, dx1, dy1, filter);
        int depth = mask & (GL11.GL_DEPTH_BUFFER_BIT | GL11.GL_STENCIL_BUFFER_BIT);
        if (depth != 0) {
            if (filter != GL11.GL_NEAREST) throw new IllegalArgumentException("Depth/stencil blits require nearest filtering");
            if ((depth & GL11.GL_DEPTH_BUFFER_BIT) != 0)
                add(copies, read, draw, true, VK_IMAGE_ASPECT_DEPTH_BIT, sx0, sy0, sx1, sy1, dx0, dy0, dx1, dy1, filter);
            if ((depth & GL11.GL_STENCIL_BUFFER_BIT) != 0)
                add(copies, read, draw, true, VK_IMAGE_ASPECT_STENCIL_BIT, sx0, sy0, sx1, sy1, dx0, dy0, dx1, dy1, filter);
        }
        if (copies.isEmpty()) return;
        Renderer.getInstance().runOutsideRenderPass(cmd -> {
            for (Copy copy : copies) record(cmd, copy, filter);
        });
    }

    private static void add(ArrayList<Copy> copies, int read, int draw, boolean depth, int aspect,
                            int sx0, int sy0, int sx1, int sy1, int dx0, int dy0, int dx1, int dy1, int filter) {
        VulkanImage source = GlFramebuffer.attachment(read, depth), target = GlFramebuffer.attachment(draw, depth);
        if (source == null || target == null || (source.aspect & aspect) == 0 || (target.aspect & aspect) == 0)
            throw new IllegalStateException("Framebuffer blit requires complete source/destination attachments for aspect " + aspect);
        if (source.getId() == target.getId())
            throw new UnsupportedOperationException("Aliased framebuffer blit requires a separate native snapshot");
        if (!source.hasUsage(VK_IMAGE_USAGE_TRANSFER_SRC_BIT) || !target.hasUsage(VK_IMAGE_USAGE_TRANSFER_DST_BIT))
            throw new IllegalStateException("Framebuffer image lacks transfer usage");
        if (depth && source.format != target.format)
            throw new IllegalArgumentException("Depth/stencil framebuffer blit formats differ");
        int sourceFeatures = features(source.format), targetFeatures = features(target.format);
        if ((sourceFeatures & VK_FORMAT_FEATURE_BLIT_SRC_BIT) == 0 || (targetFeatures & VK_FORMAT_FEATURE_BLIT_DST_BIT) == 0
                || (filter == GL11.GL_LINEAR && (sourceFeatures & VK_FORMAT_FEATURE_SAMPLED_IMAGE_FILTER_LINEAR_BIT) == 0))
            throw new UnsupportedOperationException("Framebuffer format does not support the requested native blit/filter");
        int left = 0, bottom = 0, right = target.width, top = target.height;
        if (Renderer.isScissorEnabled()) {
            left = Math.max(left, Renderer.getScissorBox(0)); bottom = Math.max(bottom, Renderer.getScissorBox(1));
            right = (int)Math.min(right, (long)Renderer.getScissorBox(0) + Math.max(0, Renderer.getScissorBox(2)));
            top = (int)Math.min(top, (long)Renderer.getScissorBox(1) + Math.max(0, Renderer.getScissorBox(3)));
        }
        int[] x = clippedAxis(sx0, sx1, dx0, dx1, source.width, left, right);
        int[] y = clippedAxis(sy0, sy1, dy0, dy1, source.height, bottom, top);
        if (x == null || y == null) return;
        // The main image uses Vulkan's negative viewport. GL offscreen targets
        // use the original positive viewport so sampling preserves GL texture UVs.
        if (GlFramebuffer.isMainFramebuffer(read)) { y[0] = source.height - y[0]; y[1] = source.height - y[1]; }
        if (GlFramebuffer.isMainFramebuffer(draw)) { y[2] = target.height - y[2]; y[3] = target.height - y[3]; }
        copies.add(new Copy(source, target, aspect, x, y));
    }

    private static int features(int format) {
        if (FEATURES.containsKey(format)) return FEATURES.get(format);
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkFormatProperties properties = VkFormatProperties.malloc(stack);
            vkGetPhysicalDeviceFormatProperties(Vulkan.getVkDevice().getPhysicalDevice(), format, properties);
            int features = properties.optimalTilingFeatures(); FEATURES.put(format, features); return features;
        }
    }

    /** GL clipping keeps the original affine mapping, including reversed endpoints. */
    static int[] clippedAxis(int s0, int s1, int d0, int d1, int sourceSize, int low, int high) {
        if (low >= high) return null;
        double sourceSpan = (double)s1 - s0, targetSpan = (double)d1 - d0;
        double a = (0.0 - s0) / sourceSpan, b = (sourceSize - (double)s0) / sourceSpan;
        double c = (low - (double)d0) / targetSpan, d = (high - (double)d0) / targetSpan;
        double from = Math.max(0.0, Math.max(Math.min(a,b), Math.min(c,d)));
        double to = Math.min(1.0, Math.min(Math.max(a,b), Math.max(c,d)));
        if (from >= to) return null;
        return new int[]{endpoint(s0 + from * sourceSpan), endpoint(s0 + to * sourceSpan),
                endpoint(d0 + from * targetSpan), endpoint(d0 + to * targetSpan)};
    }

    private static int endpoint(double coordinate) {
        double rounded = Math.rint(coordinate);
        if (Math.abs(coordinate - rounded) > 0.000001 || rounded < Integer.MIN_VALUE || rounded > Integer.MAX_VALUE)
            throw new UnsupportedOperationException("Fractional framebuffer clipping requires the native filtered raster path");
        return (int)rounded;
    }

    private static void record(VkCommandBuffer cmd, Copy copy, int filter) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VulkanImage source = copy.source, target = copy.target;
            int sourceLayout = source.getCurrentLayout(), targetLayout = target.getCurrentLayout();
            source.transitionImageLayout(stack, cmd, VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL);
            target.transitionImageLayout(stack, cmd, VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL);
            VkImageBlit.Buffer region = VkImageBlit.calloc(1, stack);
            region.srcSubresource().aspectMask(copy.aspect).mipLevel(0).baseArrayLayer(0).layerCount(1);
            region.dstSubresource().aspectMask(copy.aspect).mipLevel(0).baseArrayLayer(0).layerCount(1);
            region.srcOffsets(0).set(copy.x[0], copy.y[0], 0); region.srcOffsets(1).set(copy.x[1], copy.y[1], 1);
            region.dstOffsets(0).set(copy.x[2], copy.y[2], 0); region.dstOffsets(1).set(copy.x[3], copy.y[3], 1);
            vkCmdBlitImage(cmd, source.getId(), VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL,
                    target.getId(), VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, region,
                    filter == GL11.GL_LINEAR ? VK_FILTER_LINEAR : VK_FILTER_NEAREST);
            source.transitionImageLayout(stack, cmd, usableLayout(sourceLayout, copy.aspect));
            target.transitionImageLayout(stack, cmd, usableLayout(targetLayout, copy.aspect));
            (copy.aspect == VK_IMAGE_ASPECT_COLOR_BIT ? COLOR_BLITS : DEPTH_STENCIL_BLITS).incrementAndGet();
        }
    }

    private static int usableLayout(int previous, int aspect) {
        if (previous != VK_IMAGE_LAYOUT_UNDEFINED) return previous;
        return aspect == VK_IMAGE_ASPECT_COLOR_BIT ? VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL : VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL;
    }
    private record Copy(VulkanImage source, VulkanImage target, int aspect, int[] x, int[] y) {}
}
