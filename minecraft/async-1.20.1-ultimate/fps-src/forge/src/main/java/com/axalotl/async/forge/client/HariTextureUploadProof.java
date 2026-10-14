package com.axalotl.async.forge.client;

import com.axalotl.async.forge.AsyncForge;
import com.google.gson.GsonBuilder;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.vulkanmod.vulkan.device.DeviceManager;
import net.vulkanmod.vulkan.memory.MemoryManager;
import net.vulkanmod.vulkan.texture.ImageUtil;
import net.vulkanmod.vulkan.texture.VulkanImage;
import net.vulkanmod.vulkan.util.VUtil;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.LinkedHashMap;

import static org.lwjgl.vulkan.VK10.*;

/** Opt-in actual Vulkan copy/readback proof. Never executes in ordinary gameplay. */
@Mod.EventBusSubscriber(modid = "harimt", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class HariTextureUploadProof {
    private static final boolean ENABLED = Boolean.getBoolean("harimt.qa.verifyTextureUploads");
    private static boolean done;

    @SubscribeEvent
    public static void render(TickEvent.RenderTickEvent event) {
        if (!ENABLED || done || event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.getOverlay() != null) return;
        if (!net.vulkanmod.compat.UniversalRendererGate.vulkanRendererEnabled()) return;
        done = true;
        try {
            var rows = new ArrayList<Object>();
            for (int format : new int[]{VK_FORMAT_R8G8B8A8_UNORM, VK_FORMAT_R8_UNORM}) {
                int bytes = format == VK_FORMAT_R8_UNORM ? 1 : 4;
                VulkanImage image = VulkanImage.builder(32, 32).setFormat(format).setMipLevels(3)
                        .setUsage(VK_IMAGE_USAGE_TRANSFER_SRC_BIT | VK_IMAGE_USAGE_TRANSFER_DST_BIT | VK_IMAGE_USAGE_SAMPLED_BIT)
                        .createVulkanImage();
                try {
                    for (int mip = 0; mip < 3; mip++) {
                        int size = 32 >> mip;
                        byte[] expected = new byte[size * size * bytes];
                        ByteBuffer initial = MemoryUtil.memCalloc(expected.length);
                        image.uploadSubTextureAsync(mip, size, size, 0, 0, 0, 0, size, initial);
                        MemoryUtil.memFree(initial);
                        // Two overlapping updates exercise exact skips, padding, position and write ordering.
                        for (int update = 0; update < 2; update++) {
                            int width = size / 2, height = size / 2;
                            int stride = size + 5, skipRows = 2, skipPixels = 3, position = 7;
                            ByteBuffer source = MemoryUtil.memAlloc(position + (skipRows + height + 2) * stride * bytes);
                            for (int i = 0; i < source.capacity(); i++) source.put(i, (byte) (i * 37 + update * 53));
                            source.position(position);
                            int xOffset = update + 1, yOffset = update + 1;
                            image.uploadSubTextureAsync(mip, width, height, xOffset, yOffset, skipRows, skipPixels, stride, source);
                            for (int y = 0; y < height; y++) for (int x = 0; x < width * bytes; x++)
                                expected[((yOffset + y) * size + xOffset) * bytes + x] =
                                        source.get(position + ((skipRows + y) * stride + skipPixels) * bytes + x);
                            if (source.position() != position) throw new AssertionError("Upload changed source position");
                            MemoryUtil.memFree(source);
                        }
                        byte[] actual = readMip(image, mip, size, expected.length);
                        if (!Arrays.equals(expected, actual)) throw new AssertionError("Actual Vulkan texture bytes differ: format=" + format + " mip=" + mip);
                        var row = new LinkedHashMap<String, Object>();
                        row.put("format", format); row.put("mip", mip); row.put("bytes", actual.length);
                        row.put("expected_and_actual_sha256", HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(actual)));
                        rows.add(row);
                    }
                } finally { MemoryManager.getInstance().addToFreeable(image); }
            }
            var report = new LinkedHashMap<String, Object>();
            report.put("passed", true); report.put("scope", "Actual Vulkan buffer-to-image uploads and GPU readback in the original packaged Minecraft JVM");
            report.put("overlapping_writes", true); report.put("padded_rows_and_nonzero_buffer_position", true);
            report.put("results", rows);
            Files.writeString(mc.gameDirectory.toPath().resolve("harimt-texture-upload-proof.json"),
                    new GsonBuilder().setPrettyPrinting().create().toJson(report) + "\n");
            AsyncForge.LOGGER.info("[Hari/QA] exact Vulkan texture readback passed: RGBA and R8, three mip levels");
        } catch (Throwable failure) {
            throw new IllegalStateException("Actual Vulkan texture upload proof failed", failure);
        }
    }

    private static byte[] readMip(VulkanImage image, int mip, int width, int size) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            var buffer = stack.mallocLong(1);
            var allocation = stack.mallocPointer(1);
            MemoryManager.getInstance().createBuffer(size, VK_BUFFER_USAGE_TRANSFER_DST_BIT,
                    VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT, buffer, allocation);
            var commands = DeviceManager.getGraphicsQueue().beginCommands();
            int previous = image.getCurrentLayout();
            image.transitionImageLayout(stack, commands.getHandle(), VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL);
            ImageUtil.copyImageToBuffer(commands.getHandle(), buffer.get(0), image.getId(), mip, width, width, 0, 0, 0, 0, 0);
            image.transitionImageLayout(stack, commands.getHandle(), previous);
            long fence = DeviceManager.getGraphicsQueue().submitCommands(commands);
            int result = vkWaitForFences(DeviceManager.vkDevice, fence, true, VUtil.UINT64_MAX);
            if (result != VK_SUCCESS) throw new IllegalStateException("Texture readback fence failed: " + result);
            byte[] data = new byte[size];
            MemoryManager.MapAndCopy(allocation.get(0), ptr -> ptr.getByteBuffer(0, size).get(data));
            MemoryManager.freeBuffer(buffer.get(0), allocation.get(0));
            return data;
        }
    }
}
