package com.axalotl.async.forge.client;

import com.axalotl.async.forge.AsyncForge;
import com.google.gson.GsonBuilder;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

/** Opt-in QA telemetry; no sampling, allocation or clock reads in normal play. */
@Mod.EventBusSubscriber(modid = "harimt", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class HariFrameCapture {
    private static final boolean ENABLED = Boolean.getBoolean("harimt.qa.captureFrames");
    private static final int WARMUP = 120;
    private static final int SAMPLES = 300;
    private static final double[] FRAMES = ENABLED ? new double[SAMPLES] : null;
    private static int warmup;
    private static int count;
    private static long previous;
    private static boolean finished;

    private HariFrameCapture() {}

    @SubscribeEvent
    public static void render(TickEvent.RenderTickEvent event) {
        if (!ENABLED || finished || event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.screen != null) {
            warmup = 0;
            count = 0;
            previous = 0;
            return;
        }
        long now = System.nanoTime();
        if (warmup++ < WARMUP || previous == 0) {
            previous = now;
            return;
        }
        FRAMES[count++] = (now - previous) / 1_000_000.0;
        previous = now;
        if (count < SAMPLES) return;
        finished = true;
        double[] sorted = FRAMES.clone();
        Arrays.sort(sorted);
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("schema", 1);
        report.put("scope", "render-end wall-clock intervals including pacing; not GPU execution time");
        report.put("renderer", net.vulkanmod.compat.UniversalRendererGate.vulkanRendererEnabled() ? "VULKAN" : "OPENGL_FALLBACK");
        report.put("warmup_frames", WARMUP);
        report.put("sample_count", count);
        report.put("frame_ms", FRAMES);
        report.put("mean_ms", Arrays.stream(FRAMES).average().orElseThrow());
        report.put("p50_ms", sorted[149]);
        report.put("p95_ms", sorted[284]);
        report.put("p99_ms", sorted[296]);
        report.put("max_ms", sorted[299]);
        report.put("width", mc.getWindow().getWidth());
        report.put("height", mc.getWindow().getHeight());
        report.put("render_distance", mc.options.renderDistance().get());
        report.put("java", System.getProperty("java.version"));
        try {
            Files.writeString(mc.gameDirectory.toPath().resolve("harimt-frame-sample.json"),
                    new GsonBuilder().setPrettyPrinting().create().toJson(report) + "\n", StandardCharsets.UTF_8);
            AsyncForge.LOGGER.info("[Hari/QA] captured {} world frames after {} warmup frames", count, WARMUP);
        } catch (Exception e) {
            AsyncForge.LOGGER.error("[Hari/QA] frame capture failed", e);
        }
    }
}
