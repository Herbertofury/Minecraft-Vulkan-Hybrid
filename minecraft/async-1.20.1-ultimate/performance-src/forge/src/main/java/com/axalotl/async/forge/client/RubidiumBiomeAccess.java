package com.axalotl.async.forge.client;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;
import java.util.function.Function;

/** Resolve the optional renderer API once; never read the live client world. */
public final class RubidiumBiomeAccess {
    private RubidiumBiomeAccess() {}
    public static Function<BlockPos, Biome> snapshot(Object worldSlice) {
        try {
            Field field = worldSlice.getClass().getDeclaredField("biomeSlice");
            field.setAccessible(true);
            Object snapshot = field.get(worldSlice);
            MethodHandle sample = MethodHandles.publicLookup().findVirtual(snapshot.getClass(), "getBiome",
                    MethodType.methodType(Holder.class, int.class, int.class, int.class)).bindTo(snapshot);
            return position -> {
                try {
                    return (Biome) ((Holder<?>) sample.invoke(position.getX(), position.getY(), position.getZ())).value();
                } catch (RuntimeException | Error failure) {
                    throw failure;
                } catch (Throwable failure) {
                    throw new IllegalStateException("Rubidium biome snapshot lookup failed", failure);
                }
            };
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Unsupported Rubidium biome snapshot API", failure);
        }
    }
}
