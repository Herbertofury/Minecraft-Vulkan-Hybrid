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
 * Preserve vanilla SpawnState behavior while making its mutable counters and
 * potential calculator safe when chunk spawn work is executed in parallel.
 * This replaces HMT's hand-reimplemented afterSpawn hook.
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

    @WrapMethod(method = "canSpawnForCategoryGlobal")
    private boolean harimt$canSpawnForCategoryGlobal(MobCategory category, Operation<Boolean> original) {
        synchronized (this) {
            return original.call(category);
        }
    }

    @WrapMethod(method = "canSpawnForCategoryLocal")
    private boolean harimt$canSpawnForCategoryLocal(MobCategory category, ChunkPos pos,
                                                     Operation<Boolean> original) {
        synchronized (this) {
            return original.call(category, pos);
        }
    }
}
