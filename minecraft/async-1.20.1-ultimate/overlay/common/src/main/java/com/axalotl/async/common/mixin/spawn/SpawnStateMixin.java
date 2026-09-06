package com.axalotl.async.common.mixin.spawn;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.chunk.ChunkAccess;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Preserve vanilla Forge 1.20.1 SpawnState behavior while serializing access to
 * its mutable mob counters, PotentialCalculator, LocalMobCapCalculator, and
 * last-check cache during parallel chunk spawning.
 */
@Mixin(NaturalSpawner.SpawnState.class)
public class SpawnStateMixin {
    @WrapMethod(method = "canSpawn")
    private boolean harimt$canSpawn(EntityType<?> type, BlockPos testPos, ChunkAccess chunk,
                                    Operation<Boolean> original) {
        synchronized (this) {
            return original.call(type, testPos, chunk);
        }
    }

    @WrapMethod(method = "afterSpawn")
    private void harimt$afterSpawn(Mob mob, ChunkAccess chunk, Operation<Void> original) {
        synchronized (this) {
            original.call(mob, chunk);
        }
    }

    @WrapMethod(method = "canSpawnForCategory")
    private boolean harimt$canSpawnForCategory(MobCategory category, ChunkPos pos,
                                                Operation<Boolean> original) {
        synchronized (this) {
            return original.call(category, pos);
        }
    }
}
