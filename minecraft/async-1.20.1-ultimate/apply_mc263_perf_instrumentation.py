#!/usr/bin/env python3
from pathlib import Path
import sys

if len(sys.argv) != 2:
    raise SystemExit('usage: apply_mc263_perf_instrumentation.py <merged-upstream-root>')

root = Path(sys.argv[1]).resolve()
mc263 = root / 'common/src/main/java/com/axalotl/async/common/commands/Mc263Command.java'
if not mc263.is_file():
    raise SystemExit(f'missing Mc263Command.java: {mc263}; apply_mc263_diagnostics.py must run first')


def replace_once(old: str, new: str, label: str) -> None:
    text = mc263.read_text(encoding='utf-8')
    if new in text:
        return
    count = text.count(old)
    if count != 1:
        raise SystemExit(f'source drift in {label}: expected exactly one marker, found {count}')
    mc263.write_text(text.replace(old, new, 1), encoding='utf-8')


replace_once(
    'import net.minecraft.ChatFormatting;\n',
    'import net.minecraft.ChatFormatting;\n'
    'import net.minecraft.core.BlockPos;\n',
    'chunk benchmark BlockPos import',
)
replace_once(
    'import net.minecraft.world.entity.Mob;\n',
    'import net.minecraft.world.entity.Mob;\n'
    'import net.minecraft.world.level.block.Block;\n'
    'import net.minecraft.world.level.chunk.LevelChunk;\n\n'
    'import java.util.ArrayList;\n'
    'import java.util.List;\n'
    'import java.util.Locale;\n',
    'chunk benchmark imports',
)

old_registration = '''                .then(Commands.literal("test")\n                        .requires(Permission.require("command.async", 2))\n                        .executes(ctx -> runLiveIdleVerification(ctx.getSource()))));\n'''
new_registration = '''                .then(Commands.literal("test")\n                        .requires(Permission.require("command.async", 2))\n                        .executes(ctx -> runLiveIdleVerification(ctx.getSource())))\n                .then(Commands.literal("chunkbench")\n                        .requires(Permission.require("command.async", 2))\n                        .executes(ctx -> runChunkBenchmark(ctx.getSource()))));\n'''
replace_once(old_registration, new_registration, 'chunk benchmark registration')

marker = '''    private static Component enabled(boolean value) {\n'''
method = r'''    /**
     * Performance-lab-only synchronous fresh-chunk benchmark. The workflow runs
     * identical coordinates in cloned same-seed worlds with Cache2D fill OFF vs
     * ON. Timing excludes terrain hashing; the hash is a semantic parity guard.
     */
    private static int runChunkBenchmark(CommandSourceStack source) {
        final int sampleCount = 7;
        final int side = 8;
        final int chunksPerSample = side * side;
        ServerLevel level = source.getServer().overworld();
        boolean enabled = AsyncConfig.enableMc263DensityCacheFill.getValue();

        for (int sample = 0; sample < sampleCount; sample++) {
            // Keep samples far apart so feature-generation neighborhoods do not
            // overlap, while baseline/candidate worlds use identical coordinates.
            int baseChunkX = 1024 + sample * 32;
            int baseChunkZ = 1536 + sample * 40;
            List<LevelChunk> generated = new ArrayList<>(chunksPerSample);

            long start = System.nanoTime();
            for (int dz = 0; dz < side; dz++) {
                for (int dx = 0; dx < side; dx++) {
                    generated.add(level.getChunk(baseChunkX + dx, baseChunkZ + dz));
                }
            }
            long elapsedNanos = System.nanoTime() - start;

            long terrainHash = 0xcbf29ce484222325L;
            BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
            for (LevelChunk chunk : generated) {
                int minX = chunk.getPos().getMinBlockX();
                int minZ = chunk.getPos().getMinBlockZ();
                int minY = chunk.getMinBuildHeight();
                int maxY = chunk.getMaxBuildHeight();
                for (int localX = 0; localX < 16; localX += 4) {
                    for (int localZ = 0; localZ < 16; localZ += 4) {
                        for (int y = minY; y < maxY; y++) {
                            pos.set(minX + localX, y, minZ + localZ);
                            terrainHash ^= Block.getId(chunk.getBlockState(pos));
                            terrainHash *= 0x100000001b3L;
                        }
                    }
                }
            }

            final int index = sample + 1;
            final double elapsedMs = elapsedNanos / 1_000_000.0D;
            final long sampleHash = terrainHash;
            source.sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
                    "HMT_MC263_CHUNK_SAMPLE enabled=%s index=%d chunks=%d ms=%.6f hash=%016x",
                    enabled, index, chunksPerSample, elapsedMs, sampleHash)), false);
        }

        source.sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
                "HMT_MC263_CHUNK_BENCH_DONE enabled=%s samples=%d", enabled, sampleCount)), false);
        return 1;
    }

'''
replace_once(marker, method + marker, 'chunk benchmark implementation')

required = [
    'Commands.literal("chunkbench")',
    'HMT_MC263_CHUNK_SAMPLE',
    'HMT_MC263_CHUNK_BENCH_DONE',
    'enableMc263DensityCacheFill',
    'Block.getId(chunk.getBlockState(pos))',
    'sampleCount = 7',
    'side = 8',
]
text = mc263.read_text(encoding='utf-8')
missing = [token for token in required if token not in text]
if missing:
    raise SystemExit(f'26.3 performance instrumentation invariant failed: missing {missing}')

print('HariMultiThread Minecraft 26.3 chunk-generation performance instrumentation applied successfully')
