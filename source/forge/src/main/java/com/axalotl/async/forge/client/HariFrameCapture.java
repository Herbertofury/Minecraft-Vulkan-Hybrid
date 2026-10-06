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
    private static final boolean LIFECYCLE = Boolean.getBoolean("harimt.qa.lifecycle");
    private static final boolean OBSERVE_STATE = Boolean.getBoolean("harimt.qa.observeClientState");
    private static long lastStateObservation;
    private static boolean lastJumpDown, lastJumping, lastFlying;
    private static int commandTicks;
    private static int stableFrames;
    private static int lastWidth;
    private static int lastHeight;
    private static boolean reloadCompleted;
    private static boolean reloadReported;
    private static final int WARMUP = 120;
    private static final int SAMPLES = 300;
    private static final double[] FRAMES = ENABLED ? new double[SAMPLES] : null;
    private static int warmup;
    private static int count;
    private static long previous;
    private static boolean finished;

    private HariFrameCapture() {}

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (!LIFECYCLE || event.phase != TickEvent.Phase.END || ++commandTicks % 20 != 0) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        java.nio.file.Path command = mc.gameDirectory.toPath().resolve("harimt-qa-command.txt");
        if (!Files.isRegularFile(command)) return;
        String action;
        try {
            action = Files.readString(command, StandardCharsets.UTF_8).trim();
            Files.delete(command);
        } catch (java.io.IOException failure) {
            throw new java.io.UncheckedIOException("Cannot read QA lifecycle command", failure);
        }
        if ("reload".equals(action)) {
            reloadCompleted = false;
            reloadReported = false;
            mc.reloadResourcePacks().thenRun(() -> {
                reloadCompleted = true;
                stableFrames = 0;
            });
        } else if ("tick-fault".equals(action)) {
            RuntimeException failure = new RuntimeException("HARI_QA_INTENTIONAL_CLIENT_TICK_FAULT");
            failure.setStackTrace(new StackTraceElement[] {
                    new StackTraceElement("org.orecruncher.dsurround.HariAuditFixture", "tick", "HariAuditFixture.java", 1)
            });
            throw failure;
        } else {
            throw new IllegalArgumentException("Unknown QA lifecycle command: " + action);
        }
    }

    @SubscribeEvent
    public static void render(TickEvent.RenderTickEvent event) {
        if ((!ENABLED && !LIFECYCLE && !OBSERVE_STATE) || event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (OBSERVE_STATE) observeState(mc);
        if (LIFECYCLE && mc.level != null && mc.player != null && mc.getOverlay() == null) {
            int width = mc.getWindow().getWidth(), height = mc.getWindow().getHeight();
            if (width != lastWidth || height != lastHeight) {
                lastWidth = width;
                lastHeight = height;
                stableFrames = 0;
            }
            if (++stableFrames == 20) {
                AsyncForge.LOGGER.info("[Hari/QA] rendered world width={} height={}", width, height);
            }
            if (reloadCompleted && !reloadReported && stableFrames >= 20) {
                reloadReported = true;
                AsyncForge.LOGGER.info("[Hari/QA] resource reload completed and world rendered");
            }
        }
        if (!ENABLED || finished) return;
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
        if (Boolean.getBoolean("harimt.qa.performance")
                && net.vulkanmod.compat.UniversalRendererGate.vulkanRendererEnabled()) {
            net.vulkanmod.render.chunk.buffer.UploadManager upload = net.vulkanmod.render.chunk.buffer.UploadManager.INSTANCE;
            if (upload != null) {
                report.put("upload_regions", upload.qaRegions);
                report.put("upload_copy_commands", upload.qaCopyCalls);
                report.put("upload_write_barriers", upload.qaWriteBarriers);
            }
        }
        try {
            Files.writeString(mc.gameDirectory.toPath().resolve("harimt-frame-sample.json"),
                    new GsonBuilder().setPrettyPrinting().create().toJson(report) + "\n", StandardCharsets.UTF_8);
            AsyncForge.LOGGER.info("[Hari/QA] captured {} world frames after {} warmup frames", count, WARMUP);
        } catch (Exception e) {
            AsyncForge.LOGGER.error("[Hari/QA] frame capture failed", e);
        }
    }

    /** Read-only native input synchronization; enabled only in the expanded QA JVM. */
    private static void observeState(Minecraft mc) {
        long now = System.nanoTime();
        boolean jumpDown = mc.options.keyJump.isDown();
        boolean jumping = mc.player != null && mc.player.input.jumping;
        boolean flying = mc.player != null && mc.player.getAbilities().flying;
        if (now - lastStateObservation < 250_000_000L && jumpDown == lastJumpDown
                && jumping == lastJumping && flying == lastFlying) return;
        lastStateObservation = now;
        lastJumpDown = jumpDown; lastJumping = jumping; lastFlying = flying;
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("observed_at_epoch_ms", System.currentTimeMillis());
        state.put("jump_key_down", jumpDown);
        state.put("jumping", jumping);
        state.put("player_tick", mc.player == null ? null : mc.player.tickCount);
        state.put("dimension", mc.level == null ? null : mc.level.dimension().location().toString());
        state.put("screen", mc.screen == null ? null : mc.screen.getClass().getName());
        state.put("chat_screen", mc.screen instanceof net.minecraft.client.gui.screens.ChatScreen);
        state.put("screen_pauses", mc.screen != null && mc.screen.isPauseScreen());
        state.put("overlay", mc.getOverlay() == null ? null : mc.getOverlay().getClass().getName());
        state.put("paused", mc.isPaused());
        state.put("window_active", mc.isWindowActive());
        state.put("mouse_grabbed", mc.mouseHandler.isMouseGrabbed());
        if (mc.player != null) {
            state.put("position", new double[] {mc.player.getX(), mc.player.getY(), mc.player.getZ()});
            state.put("may_fly", mc.player.getAbilities().mayfly);
            state.put("flying", mc.player.getAbilities().flying);
        }
        java.nio.file.Path target = mc.gameDirectory.toPath().resolve("harimt-qa-client-state.json");
        java.nio.file.Path temporary = target.resolveSibling("harimt-qa-client-state.tmp");
        try {
            Files.writeString(temporary, new GsonBuilder().serializeNulls().create().toJson(state) + "\n",
                    StandardCharsets.UTF_8);
            Files.move(temporary, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                    java.nio.file.StandardCopyOption.ATOMIC_MOVE);
        } catch (java.io.IOException failure) {
            throw new java.io.UncheckedIOException("Cannot write QA client state observation", failure);
        }
    }
}
