package com.axalotl.async.common.mixin.spawn;

import com.axalotl.async.common.spawn.SpawnChunkContext;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Reuses the exact chunk already assigned to an async spawn worker.
 *
 * This is the Forge 1.20.1 adaptation of Async's July 28, 2026 spawn rollback:
 * broad worker-thread chunk lookups are avoided for NaturalSpawner's current
 * spawn chunk, while genuinely different chunk requests still fall through to
 * ServerLevel#getChunk and HMT's guarded off-thread chunk path.
 */
@Mixin(NaturalSpawner.class)
public abstract class NaturalSpawnerScheduledChunkMixin {
    @Shadow
    private static boolean isRightDistanceToPlayerAndSpawnPoint(
            ServerLevel level,
            ChunkAccess chunk,
            BlockPos.MutableBlockPos pos,
            double distance) {
        throw new AssertionError();
    }

    @Redirect(
            method = "spawnCategoryForPosition",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/level/ServerLevel;getChunk(II)Lnet/minecraft/world/level/chunk/LevelChunk;"))
    private static LevelChunk harimt$reuseScheduledChunk(ServerLevel level, int chunkX, int chunkZ) {
        LevelChunk scheduled = SpawnChunkContext.current();
        if (scheduled != null
                && scheduled.getPos().x == chunkX
                && scheduled.getPos().z == chunkZ) {
            return scheduled;
        }
        return level.getChunk(chunkX, chunkZ);
    }

    @Redirect(
            method = "spawnCategoryForPosition",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/NaturalSpawner;isRightDistanceToPlayerAndSpawnPoint(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/level/chunk/ChunkAccess;Lnet/minecraft/core/BlockPos$MutableBlockPos;D)Z"))
    private static boolean harimt$reuseScheduledChunkForDistance(
            ServerLevel level,
            ChunkAccess chunk,
            BlockPos.MutableBlockPos pos,
            double distance) {
        LevelChunk scheduled = SpawnChunkContext.current();
        return isRightDistanceToPlayerAndSpawnPoint(
                level,
                scheduled != null ? scheduled : chunk,
                pos,
                distance);
    }
}
