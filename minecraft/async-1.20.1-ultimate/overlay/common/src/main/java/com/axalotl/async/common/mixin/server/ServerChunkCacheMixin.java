package com.axalotl.async.common.mixin.server;

import com.axalotl.async.common.ParallelProcessor;
import com.axalotl.async.common.config.AsyncConfig;
import com.mojang.datafixers.util.Either;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkSource;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.chunk.ImposterProtoChunk;
import net.minecraft.world.level.chunk.LevelChunk;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Forge 1.20.1 chunk-tick hardening.
 *
 * HMT originally fire-and-forgot async random ticks and spawnForChunk tasks.
 * Those jobs could bleed into later server ticks and continue mutating chunks
 * and SpawnState after vanilla had moved on. This implementation still runs
 * the expensive per-chunk work in parallel, but joins each phase before
 * tickChunks returns. Chunk futures are also bounded instead of joining forever.
 */
@Mixin(value = ServerChunkCache.class, priority = 1500)
public abstract class ServerChunkCacheMixin extends ChunkSource {
    @Final @Shadow public ServerLevel level;
    @Shadow @Final Thread mainThread;
    @Shadow @Final public ChunkMap chunkMap;
    @Shadow @Final private ServerChunkCache.MainThreadExecutor mainThreadProcessor;

    @Unique private final List<LevelChunk> harimt$randomTickChunks = new ArrayList<>();
    @Unique private final List<Runnable> harimt$spawnTasks = new ArrayList<>();

    @Shadow
    @Nullable
    protected abstract ChunkHolder getVisibleChunkIfPresent(long key);

    @Shadow
    protected abstract CompletableFuture<Either<ChunkAccess, ChunkHolder.ChunkLoadingFailure>>
    getChunkFutureMainThread(int x, int z, ChunkStatus status, boolean create);

    @Inject(
            method = "getChunk(IILnet/minecraft/world/level/chunk/ChunkStatus;Z)Lnet/minecraft/world/level/chunk/ChunkAccess;",
            at = @At("HEAD"), cancellable = true)
    private void harimt$offThreadGetChunk(int x, int z, ChunkStatus leastStatus, boolean create,
                                          CallbackInfoReturnable<ChunkAccess> cir) {
        if (Thread.currentThread() == this.mainThread) return;

        ChunkAccess fast = harimt$tryGetChunkFast(x, z, leastStatus);
        if (fast != null) {
            cir.setReturnValue(fast);
            return;
        }

        CompletableFuture<Either<ChunkAccess, ChunkHolder.ChunkLoadingFailure>> future =
                CompletableFuture.supplyAsync(
                        () -> this.getChunkFutureMainThread(x, z, leastStatus, create),
                        this.mainThreadProcessor)
                        .thenCompose(f -> f);

        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(60);
        while (!future.isDone()) {
            if (System.nanoTime() >= deadline) {
                future.cancel(false);
                ParallelProcessor.LOGGER.error(
                        "Timed out waiting for chunk {},{} at {} from async worker; returning null instead of deadlocking",
                        x, z, leastStatus);
                cir.setReturnValue(null);
                return;
            }

            ChunkAccess cached = harimt$tryGetChunkFast(x, z, leastStatus);
            if (cached != null) {
                future.cancel(false);
                cir.setReturnValue(cached);
                return;
            }
            LockSupport.parkNanos(10_000L);
        }

        Either<ChunkAccess, ChunkHolder.ChunkLoadingFailure> result = future.join();
        if (result != null) {
            result.ifLeft(chunk -> {
                ChunkAccess value = chunk;
                if (value instanceof ImposterProtoChunk imposter) value = imposter.getWrapped();
                cir.setReturnValue(value);
            });
        }
    }

    @Unique
    @Nullable
    private ChunkAccess harimt$tryGetChunkFast(int x, int z, ChunkStatus leastStatus) {
        ChunkHolder holder = this.getVisibleChunkIfPresent(ChunkPos.asLong(x, z));
        if (holder == null) return null;

        ChunkAccess chunk = holder.getLastAvailable();
        if (chunk != null && chunk.getStatus().isOrAfter(leastStatus)) {
            if (chunk instanceof ImposterProtoChunk imposter) return imposter.getWrapped();
            return chunk;
        }

        CompletableFuture<Either<ChunkAccess, ChunkHolder.ChunkLoadingFailure>> future =
                holder.getOrScheduleFuture(leastStatus, this.chunkMap);
        if (!future.isDone()) return null;

        ChunkAccess result = future.join().left().orElse(null);
        if (result instanceof ImposterProtoChunk imposter) return imposter.getWrapped();
        return result;
    }

    @Inject(method = "getChunkNow", at = @At("HEAD"), cancellable = true)
    private void harimt$getChunkNow(int chunkX, int chunkZ, CallbackInfoReturnable<LevelChunk> cir) {
        if (Thread.currentThread() == this.mainThread) return;

        ChunkHolder holder = this.getVisibleChunkIfPresent(ChunkPos.asLong(chunkX, chunkZ));
        if (holder != null) {
            ChunkAccess chunk = holder.getLastAvailable();
            if (chunk instanceof LevelChunk levelChunk && chunk.getStatus().isOrAfter(ChunkStatus.FULL)) {
                cir.setReturnValue(levelChunk);
                return;
            }
        }
        cir.setReturnValue(null);
    }

    @Redirect(
            method = "tickChunks",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/server/level/ServerLevel;tickChunk(Lnet/minecraft/world/level/chunk/LevelChunk;I)V"))
    private void harimt$collectRandomTick(ServerLevel level, LevelChunk chunk, int randomTickSpeed) {
        if (!AsyncConfig.disabled.getValue() && AsyncConfig.enableAsyncRandomTicks.getValue()) {
            harimt$randomTickChunks.add(chunk);
        } else {
            level.tickChunk(chunk, randomTickSpeed);
        }
    }

    @Redirect(
            method = "tickChunks",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/level/NaturalSpawner;spawnForChunk(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/level/chunk/LevelChunk;Lnet/minecraft/world/level/NaturalSpawner$SpawnState;ZZZ)V"))
    private void harimt$collectSpawn(ServerLevel level, LevelChunk chunk, NaturalSpawner.SpawnState spawnState,
                                     boolean spawnAnimals, boolean spawnMonsters, boolean rareSpawn) {
        if (AsyncConfig.disabled.getValue() || !AsyncConfig.enableAsyncSpawn.getValue()) {
            NaturalSpawner.spawnForChunk(level, chunk, spawnState, spawnAnimals, spawnMonsters, rareSpawn);
            return;
        }
        if (!(spawnAnimals || spawnMonsters || rareSpawn)) return;
        harimt$spawnTasks.add(() -> NaturalSpawner.spawnForChunk(
                level, chunk, spawnState, spawnAnimals, spawnMonsters, rareSpawn));
    }

    @Inject(method = "tickChunks", at = @At("TAIL"))
    private void harimt$flushParallelChunkWork(CallbackInfo ci) {
        // Preserve phase ordering: vanilla random/chunk ticking precedes spawning.
        if (!harimt$randomTickChunks.isEmpty()) {
            int randomTickSpeed = this.level.getGameRules().getInt(GameRules.RULE_RANDOMTICKING);
            List<LevelChunk> chunks = new ArrayList<>(harimt$randomTickChunks);
            harimt$randomTickChunks.clear();
            ParallelProcessor.forEachParallel(chunks, chunk -> {
                if (chunk.getLevel() != null
                        && chunk.getLevel().getChunkSource().hasChunk(chunk.getPos().x, chunk.getPos().z)) {
                    this.level.tickChunk(chunk, randomTickSpeed);
                }
            });
        }

        if (!harimt$spawnTasks.isEmpty()) {
            List<Runnable> tasks = new ArrayList<>(harimt$spawnTasks);
            harimt$spawnTasks.clear();
            ParallelProcessor.forEachParallel(tasks, Runnable::run);
        }
    }
}
