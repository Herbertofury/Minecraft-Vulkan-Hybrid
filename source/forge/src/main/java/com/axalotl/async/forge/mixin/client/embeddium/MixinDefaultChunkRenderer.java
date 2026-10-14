package com.axalotl.async.forge.mixin.client.embeddium;

import com.axalotl.async.forge.client.hari263.HariRenderState;
import com.axalotl.async.forge.client.hari263.render.HariTerrainRenderer;
import com.axalotl.async.forge.client.hari263.render.HariWorldBridge;
import me.jellysquid.mods.sodium.client.gl.device.CommandList;
import me.jellysquid.mods.sodium.client.gl.device.RenderDevice;
import me.jellysquid.mods.sodium.client.render.chunk.ChunkRenderMatrices;
import me.jellysquid.mods.sodium.client.render.chunk.DefaultChunkRenderer;
import me.jellysquid.mods.sodium.client.render.chunk.lists.ChunkRenderListIterable;
import me.jellysquid.mods.sodium.client.render.chunk.terrain.TerrainRenderPass;
import me.jellysquid.mods.sodium.client.render.chunk.vertex.format.ChunkVertexType;
import me.jellysquid.mods.sodium.client.render.viewport.CameraTransform;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value=DefaultChunkRenderer.class,remap=false)
public abstract class MixinDefaultChunkRenderer {
    @Inject(method="<init>",at=@At("RETURN"))
    private void harimt$init(RenderDevice device, ChunkVertexType vertexType, CallbackInfo ci){
        HariRenderState.refreshProvider();
        if(HariRenderState.shouldUseInternalRenderer()) HariWorldBridge.create(vertexType);
    }

    @Inject(method="delete",at=@At("HEAD"))
    private void harimt$delete(CommandList commandList, CallbackInfo ci){HariWorldBridge.destroy();}

    @Inject(method="render",at=@At("HEAD"),cancellable=true)
    private void harimt$render(ChunkRenderMatrices matrices, CommandList commandList, ChunkRenderListIterable renderLists,
                               TerrainRenderPass renderPass, CameraTransform camera, CallbackInfo ci){
        if(!HariWorldBridge.active() || renderPass.isSorted()) return;
        HariTerrainRenderer renderer=HariWorldBridge.get(); if(renderer==null)return;
        boolean drew=renderer.tryRender((ShaderChunkRendererAccessor)this,matrices,commandList,renderLists,renderPass,camera);
        if(drew) ci.cancel(); else HariWorldBridge.failClosed();
    }
}
