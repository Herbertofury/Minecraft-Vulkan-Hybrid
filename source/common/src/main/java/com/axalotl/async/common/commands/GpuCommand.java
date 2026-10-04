package com.axalotl.async.common.commands;

import com.axalotl.async.common.config.AsyncConfig;
import com.axalotl.async.common.gpu.GpuCollisionDispatcher;
import com.axalotl.async.common.gpu.GpuEntityModule;
import com.axalotl.async.common.gpu.GpuPushBatch;
import com.axalotl.async.common.gpu.safety.CrashGuard;
import com.axalotl.async.common.gpu.vulkan.VkRuntime;
import com.axalotl.async.common.platform.Permission;
import com.axalotl.async.common.platform.PlatformUtils;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Operator diagnostics for the real live-world Vulkan collision path. */
public final class GpuCommand {
    private static final int VERIFY_LIMIT = 512;

    private GpuCommand() {}

    public static LiteralArgumentBuilder<CommandSourceStack> registerGpu(
            LiteralArgumentBuilder<CommandSourceStack> root) {
        return root.then(Commands.literal("gpu")
                .requires(Permission.require("command.gpu", 2))
                .executes(ctx -> { showStatus(ctx.getSource()); return 1; })
                .then(Commands.literal("test")
                        .requires(Permission.require("command.gpu", 2))
                        .executes(ctx -> { runLiveVerification(ctx.getSource()); return 1; }))
                .then(Commands.literal("toggle")
                        .requires(Permission.require("command.gpu", 2))
                        .executes(ctx -> { toggleGpu(ctx.getSource()); return 1; }))
        );
    }

    private static void showStatus(CommandSourceStack source) {
        GpuCollisionDispatcher d = GpuEntityModule.getCollisionDispatcher();
        boolean available = GpuEntityModule.isGpuAvailable();
        boolean enabled = AsyncConfig.enableGpuCollision.getValue();

        MutableComponent message = AsyncCommand.prefix.copy()
                .append(Component.literal("GPU Diagnostics").withStyle(ChatFormatting.GOLD))
                .append(Component.literal("\nGPU: ").withStyle(ChatFormatting.WHITE))
                .append(Component.literal(available ? "Available" : "Unavailable")
                        .withStyle(available ? ChatFormatting.GREEN : ChatFormatting.RED))
                .append(Component.literal("\nGPU Collision: ").withStyle(ChatFormatting.WHITE))
                .append(Component.literal(enabled ? "Enabled" : "Disabled")
                        .withStyle(enabled ? ChatFormatting.GREEN : ChatFormatting.GRAY))
                .append(Component.literal("\nDevice: ").withStyle(ChatFormatting.WHITE))
                .append(Component.literal(d.getDeviceName()).withStyle(ChatFormatting.YELLOW))
                .append(Component.literal("\nBackend: ").withStyle(ChatFormatting.WHITE))
                .append(Component.literal("compile-checked isolated LWJGL 3.3.1")
                        .withStyle(ChatFormatting.AQUA))
                .append(Component.literal("\nEmbedded runtime active: ").withStyle(ChatFormatting.WHITE))
                .append(Component.literal(String.valueOf(VkRuntime.isUsingEmbeddedRuntime()))
                        .withStyle(ChatFormatting.AQUA))
                .append(Component.literal("\nBackend dispatches: ").withStyle(ChatFormatting.WHITE))
                .append(Component.literal(String.valueOf(d.getBackendDispatchCount())).withStyle(ChatFormatting.AQUA))
                .append(Component.literal("\nLast dispatch: ").withStyle(ChatFormatting.WHITE))
                .append(Component.literal(String.format("%.3f ms", d.getLastDispatchMillis())).withStyle(ChatFormatting.AQUA))
                .append(Component.literal("\nDeferred push GPU batches: ").withStyle(ChatFormatting.WHITE))
                .append(Component.literal(String.valueOf(GpuPushBatch.getGpuBatches())).withStyle(ChatFormatting.GREEN))
                .append(Component.literal("\nDeferred push vanilla fallbacks: ").withStyle(ChatFormatting.WHITE))
                .append(Component.literal(String.valueOf(GpuPushBatch.getVanillaFallbackBatches())).withStyle(ChatFormatting.YELLOW))
                .append(Component.literal("\nCandidate pairs: ").withStyle(ChatFormatting.WHITE))
                .append(Component.literal(String.valueOf(GpuPushBatch.getGpuPairs())).withStyle(ChatFormatting.AQUA));

        CrashGuard.CrashGuardStats guard = d.getCrashGuardStats();
        message.append(Component.literal("\nCrashGuard: ").withStyle(ChatFormatting.WHITE))
                .append(Component.literal(guard.circuitOpen() ? "tripped" : "healthy")
                        .withStyle(guard.circuitOpen() ? ChatFormatting.RED : ChatFormatting.GREEN));
        if (guard.totalFailures() > 0) {
            message.append(Component.literal(" failures=" + guard.totalFailures() + " trips=" + guard.totalCircuitTrips())
                    .withStyle(ChatFormatting.GRAY));
        }
        String error = d.getBackendError();
        if (error != null && !error.isBlank()) {
            message.append(Component.literal("\nLast backend error: " + error).withStyle(ChatFormatting.RED));
        }
        source.sendSuccess(() -> message, false);
    }

    /**
     * Compares the live GPU candidate set with exact vanilla double-precision
     * AABB overlap for up to 512 current-world entities. Conservative extras are
     * acceptable; a single missing exact pair disables GPU acceleration.
     */
    private static void runLiveVerification(CommandSourceStack source) {
        if (!GpuEntityModule.isGpuAvailable()) {
            source.sendFailure(AsyncCommand.prefix.copy().append(
                    Component.literal("Vulkan backend is not operational; vanilla fallback remains active.")
                            .withStyle(ChatFormatting.RED)));
            return;
        }

        ServerLevel level = source.getLevel();
        List<Entity> entities = new ArrayList<>(VERIFY_LIMIT);
        for (Entity entity : level.getAllEntities()) {
            if (entity != null && !entity.isRemoved() && entity.isAlive()) {
                entities.add(entity);
                if (entities.size() >= VERIFY_LIMIT) break;
            }
        }
        if (entities.size() < 16) {
            source.sendFailure(AsyncCommand.prefix.copy().append(
                    Component.literal("Need at least 16 live entities in this dimension for GPU verification (found "
                            + entities.size() + ").").withStyle(ChatFormatting.YELLOW)));
            return;
        }

        source.sendSuccess(() -> AsyncCommand.prefix.copy().append(
                Component.literal("Live GPU verification: " + entities.size() + " entities...")
                        .withStyle(ChatFormatting.YELLOW)), false);

        long cpuStart = System.nanoTime();
        Set<Long> exact = exactPairs(entities);
        long cpuNanos = System.nanoTime() - cpuStart;

        GpuCollisionDispatcher d = GpuEntityModule.getCollisionDispatcher();
        Optional<List<GpuCollisionDispatcher.CollisionPair>> gpuResult = d.computeGpuOnly(entities);
        if (gpuResult.isEmpty()) {
            source.sendFailure(AsyncCommand.prefix.copy().append(
                    Component.literal("GPU verification could not obtain a complete result; vanilla fallback is active.")
                            .withStyle(ChatFormatting.RED)));
            return;
        }

        IdentityHashMap<Entity, Integer> indexes = new IdentityHashMap<>();
        for (int i = 0; i < entities.size(); i++) indexes.put(entities.get(i), i);
        Set<Long> gpu = new HashSet<>();
        for (GpuCollisionDispatcher.CollisionPair pair : gpuResult.get()) {
            Integer ia = indexes.get(pair.a());
            Integer ib = indexes.get(pair.b());
            if (ia == null || ib == null || ia.equals(ib)) continue;
            int lo = Math.min(ia, ib);
            int hi = Math.max(ia, ib);
            gpu.add(key(lo, hi));
        }

        Set<Long> missing = new HashSet<>(exact);
        missing.removeAll(gpu);
        Set<Long> extras = new HashSet<>(gpu);
        extras.removeAll(exact);

        if (!missing.isEmpty()) {
            AsyncConfig.enableGpuCollision.setValue(false);
            PlatformUtils.saveConfig();
            source.sendFailure(AsyncCommand.prefix.copy().append(
                    Component.literal("GPU VERIFICATION FAILED: " + missing.size()
                            + " exact collision pair(s) were missing. GPU collision was automatically disabled; vanilla fallback is active.")
                            .withStyle(ChatFormatting.RED)));
            return;
        }

        double gpuMs = d.getLastDispatchMillis();
        double cpuMs = cpuNanos / 1_000_000.0;
        source.sendSuccess(() -> AsyncCommand.prefix.copy()
                .append(Component.literal("Live GPU Verification PASS").withStyle(ChatFormatting.GREEN))
                .append(Component.literal("\nExact pairs: " + exact.size()).withStyle(ChatFormatting.WHITE))
                .append(Component.literal("\nGPU candidates: " + gpu.size()).withStyle(ChatFormatting.WHITE))
                .append(Component.literal("\nConservative extras: " + extras.size()).withStyle(ChatFormatting.GRAY))
                .append(Component.literal(String.format("\nGPU dispatch: %.3f ms | exact CPU reference: %.3f ms", gpuMs, cpuMs))
                        .withStyle(ChatFormatting.AQUA)), false);
    }

    private static Set<Long> exactPairs(List<Entity> entities) {
        Set<Long> result = new HashSet<>();
        for (int i = 0; i < entities.size(); i++) {
            Entity a = entities.get(i);
            for (int j = i + 1; j < entities.size(); j++) {
                if (a.getBoundingBox().intersects(entities.get(j).getBoundingBox())) result.add(key(i, j));
            }
        }
        return result;
    }

    private static long key(int a, int b) {
        return ((long) a << 32) | (b & 0xffff_ffffL);
    }

    private static void toggleGpu(CommandSourceStack source) {
        boolean next = !AsyncConfig.enableGpuCollision.getValue();
        AsyncConfig.enableGpuCollision.setValue(next);
        PlatformUtils.saveConfig();

        MutableComponent message = AsyncCommand.prefix.copy()
                .append(Component.literal("GPU Collision: ").withStyle(ChatFormatting.WHITE))
                .append(Component.literal(next ? "Enabled" : "Disabled")
                        .withStyle(next ? ChatFormatting.GREEN : ChatFormatting.RED));
        if (next && !GpuEntityModule.isGpuAvailable()) {
            message.append(Component.literal("\nVulkan backend is unavailable; vanilla fallback remains active.")
                    .withStyle(ChatFormatting.YELLOW));
        } else if (!next) {
            message.append(Component.literal("\nVanilla deferred-push fallback is active.")
                    .withStyle(ChatFormatting.GRAY));
        }
        source.sendSuccess(() -> message, true);
    }
}
