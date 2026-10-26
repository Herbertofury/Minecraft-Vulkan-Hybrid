package com.axalotl.async.forge.client;

import com.axalotl.async.forge.AsyncForge;
import com.google.gson.GsonBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

/** Ctrl+F9 captures actual world frame intervals without changing game settings. */
@Mod.EventBusSubscriber(modid = "harimt", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class HariFpsCapture {
    private static final boolean AUTO = Boolean.getBoolean("harimt.fps.auto");
    private static final int WARMUP_SECONDS = Integer.getInteger("harimt.fps.warmupSeconds", 5);
    private static final long WARMUP_NS = Math.multiplyExact((long) WARMUP_SECONDS, 1_000_000_000L);
    private static final long SAMPLE_NS = 30_000_000_000L;
    private static boolean autoStarted;
    private static boolean active;
    private static long began, firstSample, previous, epochStart;
    private static double[] frames;
    private static int count;
    private static Map<String, Object> scene;
    private static Object dimension;
    private static long gcCount, gcMs, cpuNs;

    private HariFpsCapture() {}

    @SubscribeEvent
    public static void key(InputEvent.Key event) {
        if (event.getKey() == GLFW.GLFW_KEY_F9 && event.getAction() == GLFW.GLFW_PRESS
                && (event.getModifiers() & GLFW.GLFW_MOD_CONTROL) != 0 && !active) start(false);
    }

    private static Map<String, Object> scene(Minecraft mc) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("dimension", mc.level.dimension().location().toString());
        value.put("width", mc.getWindow().getWidth());
        value.put("height", mc.getWindow().getHeight());
        value.put("render_distance", mc.options.renderDistance().get());
        value.put("simulation_distance", mc.options.simulationDistance().get());
        value.put("vsync", mc.options.enableVsync().get());
        value.put("fps_limit", mc.options.framerateLimit().get());
        return value;
    }

    private static void start(boolean automatic) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.screen != null || mc.getOverlay() != null) return;
        active = true;
        count = 0;
        frames = new double[4096];
        began = System.nanoTime(); firstSample = 0; previous = 0;
        scene = scene(mc); dimension = mc.level.dimension();
        if (!automatic) mc.gui.getChat().addMessage(Component.literal("[Hari FPS] 5s warmup, then 30s capture. Keep this scene and settings steady."));
        AsyncForge.LOGGER.info("[Hari/FPS] capture started; 5s warmup and 30s full-frame sample");
    }

    private static long gc(boolean duration) {
        long sum = 0;
        for (var bean : ManagementFactory.getGarbageCollectorMXBeans()) {
            long value = duration ? bean.getCollectionTime() : bean.getCollectionCount();
            if (value >= 0) sum += value;
        }
        return sum;
    }

    private static long processCpu() {
        var bean = ManagementFactory.getOperatingSystemMXBean();
        return bean instanceof com.sun.management.OperatingSystemMXBean os ? os.getProcessCpuTime() : -1;
    }

    @SubscribeEvent
    public static void render(TickEvent.RenderTickEvent event) {
        if ((!active && (!AUTO || autoStarted)) || event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (!active) { start(true); autoStarted = active; return; }
        if (mc.level == null || mc.player == null || mc.screen != null || mc.getOverlay() != null) {
            finish(mc, "World/menu/resource reload interrupted capture"); return;
        }
        // Compare only primitive settings each frame. No allocations in the measured loop.
        if (mc.level.dimension() != dimension || mc.getWindow().getWidth() != (int) scene.get("width")
                || mc.getWindow().getHeight() != (int) scene.get("height")
                || mc.options.renderDistance().get() != (int) scene.get("render_distance")
                || mc.options.simulationDistance().get() != (int) scene.get("simulation_distance")
                || mc.options.enableVsync().get() != (boolean) scene.get("vsync")
                || mc.options.framerateLimit().get() != (int) scene.get("fps_limit")) {
            finish(mc, "Graphics, simulation or pacing settings changed during capture"); return;
        }
        long now = System.nanoTime();
        if (now - began < WARMUP_NS) return;
        if (firstSample == 0) {
            firstSample = previous = now;
            epochStart = System.currentTimeMillis();
            gcCount = gc(false); gcMs = gc(true); cpuNs = processCpu();
            return;
        }
        if (count == frames.length) frames = Arrays.copyOf(frames, Math.multiplyExact(frames.length, 2));
        frames[count++] = (now - previous) / 1_000_000.0;
        previous = now;
        if (now - firstSample >= SAMPLE_NS) finish(mc, null);
    }

    private static void finish(Minecraft mc, String invalid) {
        active = false;
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("schema", 1);
        report.put("status", invalid == null ? "complete" : "invalid");
        report.put("invalid_reason", invalid);
        report.put("scope", "Render-end wall-clock intervals including pacing; not GPU execution time");
        report.put("renderer", net.vulkanmod.compat.UniversalRendererGate.vulkanRendererEnabled() ? "VULKAN" : "OPENGL_FALLBACK");
        report.put("scene", scene);
        report.put("warmup_seconds", WARMUP_SECONDS);
        report.put("requested_sample_seconds", 30);
        report.put("sample_start_epoch_ms", epochStart);
        report.put("sample_end_epoch_ms", System.currentTimeMillis());
        report.put("sample_count", count);
        double[] sample = Arrays.copyOf(frames, count);
        report.put("frame_ms", sample);
        if (invalid == null) {
            double[] sorted = sample.clone(); Arrays.sort(sorted);
            double mean = Arrays.stream(sample).average().orElseThrow();
            int slow = Math.max(1, (int) Math.ceil(count / 100.0));
            double slowMean = Arrays.stream(sorted, count - slow, count).average().orElseThrow();
            report.put("mean_ms", mean);
            report.put("average_fps", 1000.0 / mean);
            report.put("one_percent_low_fps", 1000.0 / slowMean);
            report.put("one_percent_low_definition", "1000 / arithmetic mean of slowest ceil(1% * sample_count) frame times");
            report.put("p50_ms", sorted[(int) Math.ceil(count * .50) - 1]);
            report.put("p95_ms", sorted[(int) Math.ceil(count * .95) - 1]);
            report.put("p99_ms", sorted[(int) Math.ceil(count * .99) - 1]);
            report.put("max_ms", sorted[count - 1]);
            report.put("gc_collections", gc(false) - gcCount);
            report.put("gc_time_ms", gc(true) - gcMs);
            report.put("process_cpu_ns", processCpu() - cpuNs);
            report.put("heap_used_bytes", Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory());
            report.put("java", System.getProperty("java.version"));
            report.put("os", System.getProperty("os.name"));
            report.put("position", new double[]{mc.player.getX(), mc.player.getY(), mc.player.getZ()});
            report.put("yaw", mc.gameRenderer.getMainCamera().getYRot());
            report.put("pitch", mc.gameRenderer.getMainCamera().getXRot());
        }
        try {
            Files.writeString(mc.gameDirectory.toPath().resolve("harimt-fps-last.json"),
                    new GsonBuilder().serializeNulls().setPrettyPrinting().create().toJson(report) + "\n", StandardCharsets.UTF_8);
        } catch (java.io.IOException failure) {
            throw new java.io.UncheckedIOException("Cannot save FPS capture", failure);
        }
        frames = null;
        if (invalid != null) {
            AsyncForge.LOGGER.warn("[Hari/FPS] capture invalid: {}", invalid);
        } else {
            AsyncForge.LOGGER.info("[Hari/FPS] capture complete; frames={} averageFPS={} onePercentLow={}", count,
                    report.get("average_fps"), report.get("one_percent_low_fps"));
            if (!AUTO) mc.gui.getChat().addMessage(Component.literal(String.format(java.util.Locale.ROOT,
                    "[Hari FPS] %.1f FPS, 1%% low %.1f. Saved harimt-fps-last.json.",
                    report.get("average_fps"), report.get("one_percent_low_fps"))));
        }
    }
}
