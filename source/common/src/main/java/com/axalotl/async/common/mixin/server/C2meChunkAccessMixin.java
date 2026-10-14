package com.axalotl.async.common.mixin.server;

import com.axalotl.async.common.C2meThreadBoundary;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/** Preserve loaded reads; owner-side blocking retains C2ME's re-entrant load fixes. */
@Mixin(value = ServerChunkCache.class, priority = 2000)
public abstract class C2meChunkAccessMixin {
    @Shadow @Final public ServerLevel level;

    @WrapMethod(method = "getChunk(IILnet/minecraft/world/level/chunk/ChunkStatus;Z)Lnet/minecraft/world/level/chunk/ChunkAccess;")
    private ChunkAccess harimt$c2meChunkRequest(int x, int z, ChunkStatus status,
                                               boolean create, Operation<ChunkAccess> original) {
        if (!C2meThreadBoundary.mustHandoff()) return original.call(x, z, status, create);
        if (status == ChunkStatus.FULL) {
            // getChunkNow is non-creating; Hari already supplies its off-thread
            // completed-holder lookup. Never join a C2ME off-thread load here.
            LevelChunk loaded = ((ServerChunkCache) (Object) this).getChunkNow(x, z);
            if (loaded != null) return loaded;
        }
        // Wrap the whole method, before C2ME's HEAD injector. On the real owner,
        // its normal getChunk path includes ticket updates, nested task pumping,
        // and the currently-loading chunk bypass instead of a raw future chain.
        return C2meThreadBoundary.call(level, () -> original.call(x, z, status, create));
    }
}
