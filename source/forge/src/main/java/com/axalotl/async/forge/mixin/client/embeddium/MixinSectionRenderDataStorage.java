package com.axalotl.async.forge.mixin.client.embeddium;

import com.axalotl.async.forge.client.hari263.render.HariWorldBridge;
import com.axalotl.async.forge.client.hari263.render.SectionStorageGenerationAccess;
import me.jellysquid.mods.sodium.client.gl.arena.GlBufferSegment;
import me.jellysquid.mods.sodium.client.gl.util.VertexRange;
import me.jellysquid.mods.sodium.client.render.chunk.data.SectionRenderDataStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = SectionRenderDataStorage.class, remap = false)
public class MixinSectionRenderDataStorage implements SectionStorageGenerationAccess {
    @Unique
    private int harimt$generation = 1;

    @Override
    public int harimt$getGeneration() {
        return this.harimt$generation;
    }

    @Override
    public void harimt$bumpGeneration() {
        this.harimt$generation = this.harimt$generation == Integer.MAX_VALUE ? 1 : this.harimt$generation + 1;
    }

    @Inject(method = "setMeshes", at = @At("TAIL"))
    private void harimt$set(int sectionIndex, GlBufferSegment vertex, GlBufferSegment index,
                            VertexRange[] ranges, CallbackInfo ci) {
        harimt$bumpGeneration();
    }

    @Inject(method = "removeMeshes", at = @At("TAIL"))
    private void harimt$remove(int sectionIndex, CallbackInfo ci) {
        harimt$bumpGeneration();
    }

    @Inject(method = "replaceIndexBuffer", at = @At("TAIL"))
    private void harimt$replaceIndex(int sectionIndex, GlBufferSegment index, CallbackInfo ci) {
        harimt$bumpGeneration();
    }

    @Inject(method = "onBufferResized", at = @At("TAIL"))
    private void harimt$resize(CallbackInfo ci) {
        harimt$bumpGeneration();
    }

    @Inject(method = "delete", at = @At("HEAD"))
    private void harimt$delete(CallbackInfo ci) {
        harimt$bumpGeneration();
        HariWorldBridge.invalidateStorage((SectionRenderDataStorage) (Object) this);
    }
}
