package com.axalotl.async.common.mixin.world;

import com.axalotl.async.common.config.AsyncConfig;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureCheck;
import net.minecraft.world.level.levelgen.structure.StructureCheckResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Backports the repeated-query portion of Minecraft 26.3's structure-locate
 * improvements without changing structure placement or generation semantics.
 */
@Mixin(StructureCheck.class)
public abstract class StructureCheckMixin {
    @Unique
    private static final int HARIMT$MC263_MAX_STORAGE_MISSES = 65_536;

    @Unique
    private final Set<Long> harimt$mc263StorageMisses = ConcurrentHashMap.newKeySet();

    @Inject(method = "tryLoadFromStorage", at = @At("HEAD"), cancellable = true)
    private void harimt$mc263SkipRepeatedStorageMiss(ChunkPos chunkPos, Structure structure, boolean skipKnownStructures,
                                                     long chunkKey, CallbackInfoReturnable<StructureCheckResult> cir) {
        if (AsyncConfig.enableMc263StructureLocateCache.getValue()
                && this.harimt$mc263StorageMisses.contains(chunkKey)) {
            // null is vanilla's signal to continue into the cached canCreateStructure path.
            cir.setReturnValue(null);
        }
    }

    @Inject(method = "tryLoadFromStorage", at = @At("RETURN"))
    private void harimt$mc263RememberStorageMiss(ChunkPos chunkPos, Structure structure, boolean skipKnownStructures,
                                                 long chunkKey, CallbackInfoReturnable<StructureCheckResult> cir) {
        if (!AsyncConfig.enableMc263StructureLocateCache.getValue() || cir.getReturnValue() != null) {
            return;
        }
        if (this.harimt$mc263StorageMisses.size() >= HARIMT$MC263_MAX_STORAGE_MISSES) {
            // Hard memory bound. Clearing only loses cache warmth; it cannot change results.
            this.harimt$mc263StorageMisses.clear();
        }
        this.harimt$mc263StorageMisses.add(chunkKey);
    }

    @Inject(method = "storeFullResults", at = @At("HEAD"))
    private void harimt$mc263InvalidateStorageMiss(long chunkKey, Object2IntMap<Structure> structures, CallbackInfo ci) {
        this.harimt$mc263StorageMisses.remove(chunkKey);
    }

    @Inject(method = "incrementReference", at = @At("HEAD"))
    private void harimt$mc263InvalidateStorageMissOnReference(ChunkPos chunkPos, Structure structure, CallbackInfo ci) {
        this.harimt$mc263StorageMisses.remove(chunkPos.toLong());
    }
}
