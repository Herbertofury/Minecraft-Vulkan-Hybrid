package com.axalotl.async.forge.client;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ColorResolver;
import net.minecraft.world.level.biome.Biome;
import java.util.function.Function;

/** Vanilla RGB box average, evaluated against the supplied biome snapshot. */
public final class ModdedBiomeTint {
    private ModdedBiomeTint() {}
    /** Exact RGB box averages in linear work, with one rounding after both axes. */
    public static void blur(int[] colors, int width, int radius) {
        int stride = width + 1, diameter = radius * 2 + 1, count = diameter * diameter;
        int[] sums = new int[stride * stride];
        for (int shift : new int[]{16, 8, 0}) {
            java.util.Arrays.fill(sums, 0);
            for (int z = 0; z < width; z++) {
                int row = 0;
                for (int x = 0; x < width; x++) {
                    row += colors[x + z * width] >> shift & 255;
                    sums[x + 1 + (z + 1) * stride] = row + sums[x + 1 + z * stride];
                }
            }
            for (int z = radius; z < width - radius; z++) {
                for (int x = radius; x < width - radius; x++) {
                    int x0 = x - radius, z0 = z - radius, x1 = x + radius + 1, z1 = z + radius + 1;
                    int sum = sums[x1 + z1 * stride] - sums[x0 + z1 * stride]
                            - sums[x1 + z0 * stride] + sums[x0 + z0 * stride];
                    int index = x + z * width;
                    colors[index] = (colors[index] & ~(255 << shift)) | (sum / count << shift);
                }
            }
        }
    }
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
