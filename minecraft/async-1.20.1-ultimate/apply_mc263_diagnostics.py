#!/usr/bin/env python3
from pathlib import Path
import sys

if len(sys.argv) != 2:
    raise SystemExit('usage: apply_mc263_diagnostics.py <merged-upstream-root>')

root = Path(sys.argv[1]).resolve()
async_command = root / 'common/src/main/java/com/axalotl/async/common/commands/AsyncCommand.java'
if not async_command.is_file():
    raise SystemExit(f'missing AsyncCommand.java: {async_command}')


def replace_once(path: Path, old: str, new: str, label: str) -> None:
    text = path.read_text(encoding='utf-8')
    if new in text:
        return
    count = text.count(old)
    if count != 1:
        raise SystemExit(f'source drift in {label}: expected exactly one marker, found {count}')
    path.write_text(text.replace(old, new, 1), encoding='utf-8')


replace_once(
    async_command,
    'import com.axalotl.async.common.commands.StatsCommand;\n',
    'import com.axalotl.async.common.commands.StatsCommand;\n'
    'import com.axalotl.async.common.commands.Mc263Command;\n',
    'AsyncCommand mc263 import',
)
replace_once(
    async_command,
    '        main = GpuCommand.registerGpu(main);\n',
    '        main = GpuCommand.registerGpu(main);\n'
    '        main = Mc263Command.registerMc263(main);\n',
    'AsyncCommand mc263 registration',
)

mc263 = root / 'common/src/main/java/com/axalotl/async/common/commands/Mc263Command.java'
mc263.write_text(r'''package com.axalotl.async.common.commands;

import com.axalotl.async.common.AsyncCommon;
import com.axalotl.async.common.config.AsyncConfig;
import com.axalotl.async.common.platform.Permission;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;

/** Operator status and live behavioral proof for the Minecraft 26.3 backport lanes. */
public final class Mc263Command {
    private static final String QA_TAG = "harimt_mc263_idle";

    private Mc263Command() {}

    public static LiteralArgumentBuilder<CommandSourceStack> registerMc263(
            LiteralArgumentBuilder<CommandSourceStack> root) {
        return root.then(Commands.literal("mc263")
                .requires(Permission.require("command.async", 2))
                .executes(ctx -> showStatus(ctx.getSource()))
                .then(Commands.literal("test")
                        .requires(Permission.require("command.async", 2))
                        .executes(ctx -> runLiveIdleVerification(ctx.getSource()))));
    }

    private static int showStatus(CommandSourceStack source) {
        MutableComponent message = AsyncCommand.prefix.copy()
                .append(Component.literal("Minecraft 26.3 Backports").withStyle(ChatFormatting.GOLD))
                .append(Component.literal("\nPersistent-mob idle: ").withStyle(ChatFormatting.WHITE))
                .append(enabled(AsyncConfig.enableMc263PersistentMobIdle.getValue()))
                .append(Component.literal("\nStructure locate cache: ").withStyle(ChatFormatting.WHITE))
                .append(enabled(AsyncConfig.enableMc263StructureLocateCache.getValue()))
                .append(Component.literal("\nDensity Cache2D fill: ").withStyle(ChatFormatting.WHITE))
                .append(enabled(AsyncConfig.enableMc263DensityCacheFill.getValue()))
                .append(Component.literal("\nChunk scheduler owner: ").withStyle(ChatFormatting.WHITE))
                .append(Component.literal(AsyncCommon.HARICHUNK ? "HariChunk/C2ME (Hari yields)" : "Hari/vanilla")
                        .withStyle(AsyncCommon.HARICHUNK ? ChatFormatting.AQUA : ChatFormatting.GREEN))
                .append(Component.literal("\nNoisium: ").withStyle(ChatFormatting.WHITE))
                .append(Component.literal(AsyncCommon.NOISIUM ? "Detected (additive)" : "Not detected")
                        .withStyle(AsyncCommon.NOISIUM ? ChatFormatting.AQUA : ChatFormatting.GRAY))
                .append(Component.literal("\nTerrain renderer owner: ").withStyle(ChatFormatting.WHITE))
                .append(Component.literal(AsyncCommon.EXTERNAL_TERRAIN_RENDERER
                        ? "Embeddium/Rubidium/Sodium (26.3 MDI delegated)" : "Vanilla 1.20.1")
                        .withStyle(AsyncCommon.EXTERNAL_TERRAIN_RENDERER ? ChatFormatting.AQUA : ChatFormatting.GREEN))
                .append(Component.literal("\nShader pipeline owner: ").withStyle(ChatFormatting.WHITE))
                .append(Component.literal(AsyncCommon.EXTERNAL_SHADER_PIPELINE
                        ? "Oculus/Iris (26.3 OIT/ShaderC delegated)" : "Vanilla 1.20.1")
                        .withStyle(AsyncCommon.EXTERNAL_SHADER_PIPELINE ? ChatFormatting.AQUA : ChatFormatting.GREEN));
        source.sendSuccess(() -> message, false);
        return 1;
    }

    /**
     * Runtime proof for the exact 26.3 persistent-mob behavior. A dedicated QA
     * fixture tags a persistent mob with {@code harimt_mc263_idle}; without this
     * backport vanilla 1.20.1 pins that mob's noActionTime to zero every tick.
     */
    private static int runLiveIdleVerification(CommandSourceStack source) {
        if (!AsyncConfig.enableMc263PersistentMobIdle.getValue()) {
            source.sendFailure(AsyncCommand.prefix.copy().append(
                    Component.literal("HMT_MC263_IDLE_SKIP: persistent-mob idle backport is disabled")
                            .withStyle(ChatFormatting.YELLOW)));
            return 0;
        }

        int tagged = 0;
        int persistent = 0;
        int maxNoActionTime = 0;
        for (ServerLevel level : source.getServer().getAllLevels()) {
            for (Entity entity : level.getAllEntities()) {
                if (!(entity instanceof Mob mob) || !mob.getTags().contains(QA_TAG)) {
                    continue;
                }
                tagged++;
                if (mob.isPersistenceRequired() || mob.requiresCustomPersistence()) {
                    persistent++;
                }
                maxNoActionTime = Math.max(maxNoActionTime, mob.getNoActionTime());
            }
        }

        if (tagged == 0) {
            source.sendFailure(AsyncCommand.prefix.copy().append(
                    Component.literal("HMT_MC263_IDLE_NO_FIXTURE: no mob tagged " + QA_TAG)
                            .withStyle(ChatFormatting.YELLOW)));
            return 0;
        }
        if (persistent == 0) {
            source.sendFailure(AsyncCommand.prefix.copy().append(
                    Component.literal("HMT_MC263_IDLE_FAIL: tagged fixture is not persistent")
                            .withStyle(ChatFormatting.RED)));
            return 0;
        }
        if (maxNoActionTime <= 0) {
            source.sendFailure(AsyncCommand.prefix.copy().append(
                    Component.literal("HMT_MC263_IDLE_FAIL: persistent mob noActionTime is still pinned to zero")
                            .withStyle(ChatFormatting.RED)));
            return 0;
        }

        final int fixtureCount = tagged;
        final int persistentCount = persistent;
        final int observed = maxNoActionTime;
        source.sendSuccess(() -> AsyncCommand.prefix.copy().append(
                Component.literal("HMT_MC263_IDLE_PASS")
                        .withStyle(ChatFormatting.GREEN))
                .append(Component.literal(" fixtures=" + fixtureCount
                        + " persistent=" + persistentCount
                        + " maxNoActionTime=" + observed)
                        .withStyle(ChatFormatting.AQUA)), false);
        return 1;
    }

    private static Component enabled(boolean value) {
        return Component.literal(value ? "Enabled" : "Disabled")
                .withStyle(value ? ChatFormatting.GREEN : ChatFormatting.GRAY);
    }
}
''', encoding='utf-8')

required = {
    async_command: ['Mc263Command.registerMc263(main)', 'import com.axalotl.async.common.commands.Mc263Command;'],
    mc263: ['HMT_MC263_IDLE_PASS', 'enableMc263StructureLocateCache', 'EXTERNAL_TERRAIN_RENDERER', 'getNoActionTime()'],
}
for path, tokens in required.items():
    text = path.read_text(encoding='utf-8')
    missing = [token for token in tokens if token not in text]
    if missing:
        raise SystemExit(f'26.3 diagnostics invariant failed in {path}: missing {missing}')

print('HariMultiThread Minecraft 26.3 diagnostics applied successfully')
