package com.axalotl.async.forge.client;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ColorResolver;
import net.minecraft.world.level.biome.Biome;
import java.util.function.Function;

/** Vanilla RGB box average, evaluated against the supplied biome snapshot. */
public final class ModdedBiomeTint {
    private ModdedBiomeTint() {}
    public static int blend(BlockPos position, ColorResolver resolver, int radius,
                            Function<BlockPos, Biome> biomes) {
        if (radius < 0 || radius > 7) throw new IllegalArgumentException("Biome blend radius: " + radius);
        BlockPos.MutableBlockPos sample = new BlockPos.MutableBlockPos();
        int red = 0, green = 0, blue = 0;
        for (int z = position.getZ() - radius; z <= position.getZ() + radius; z++) {
            for (int x = position.getX() - radius; x <= position.getX() + radius; x++) {
                int color = resolver.getColor(biomes.apply(sample.set(x, position.getY(), z)), x, z);
                red += color >> 16 & 255;
                green += color >> 8 & 255;
                blue += color & 255;
            }
        }
        int count = (radius * 2 + 1) * (radius * 2 + 1);
        return (red / count << 16) | (green / count << 8) | blue / count;
    }
}
