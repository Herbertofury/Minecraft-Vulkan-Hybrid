package com.axalotl.async.common.mixin.world;

import com.axalotl.async.common.config.AsyncConfig;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.StreamTagVisitor;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.storage.RegionFileStorage;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Minecraft 1.20.1 RegionFileStorage#getRegionFile creates an .mca file even
 * for read/scan misses. Structure locating calls scanChunk across candidate
 * chunks, so a locate in ungenerated terrain can litter the region directory
 * with empty files. 26.3 switched missing-region reads to an existence fast
 * path and cached negative regions. This mixin backports that behavior without
 * changing the on-disk format or any successful read/write path.
 */
@Mixin(RegionFileStorage.class)
public abstract class RegionFileStorageMixin {
    @Unique
    private static final int HARIMT$MC263_MAX_MISSING_REGIONS = 4_096;

    @Shadow @Final
    private Path folder;

    @Unique
    private final Set<Long> harimt$mc263MissingRegions = ConcurrentHashMap.newKeySet();

    @Unique
    private long harimt$mc263RegionKey(ChunkPos pos) {
        return ChunkPos.asLong(pos.getRegionX(), pos.getRegionZ());
    }

    @Unique
    private Path harimt$mc263RegionPath(ChunkPos pos) {
        return this.folder.resolve("r." + pos.getRegionX() + "." + pos.getRegionZ() + ".mca");
    }

    @Unique
    private boolean harimt$mc263RegionMissing(ChunkPos pos) {
        long key = this.harimt$mc263RegionKey(pos);
        if (this.harimt$mc263MissingRegions.contains(key)) {
            return true;
        }

        // Files.notExists is conservative: false means either the file exists or
        // existence could not be determined, in which case vanilla handles it.
        if (!Files.notExists(this.harimt$mc263RegionPath(pos))) {
            return false;
        }

        if (this.harimt$mc263MissingRegions.size() >= HARIMT$MC263_MAX_MISSING_REGIONS) {
            this.harimt$mc263MissingRegions.clear();
        }
        this.harimt$mc263MissingRegions.add(key);
        return true;
    }

    @Inject(method = "read", at = @At("HEAD"), cancellable = true)
    private void harimt$mc263SkipMissingRegionRead(ChunkPos pos, CallbackInfoReturnable<CompoundTag> cir) {
        if (AsyncConfig.enableMc263NonCreatingRegionReads.getValue() && this.harimt$mc263RegionMissing(pos)) {
            cir.setReturnValue(null);
        }
    }

    @Inject(method = "scanChunk", at = @At("HEAD"), cancellable = true)
    private void harimt$mc263SkipMissingRegionScan(ChunkPos pos, StreamTagVisitor visitor, CallbackInfo ci) {
        if (AsyncConfig.enableMc263NonCreatingRegionReads.getValue() && this.harimt$mc263RegionMissing(pos)) {
            ci.cancel();
        }
    }

    @Inject(method = "write", at = @At("HEAD"))
    private void harimt$mc263InvalidateMissingRegionBeforeWrite(ChunkPos pos, CompoundTag tag, CallbackInfo ci) {
        if (AsyncConfig.enableMc263NonCreatingRegionReads.getValue()) {
            this.harimt$mc263MissingRegions.remove(this.harimt$mc263RegionKey(pos));
        }
    }
}
