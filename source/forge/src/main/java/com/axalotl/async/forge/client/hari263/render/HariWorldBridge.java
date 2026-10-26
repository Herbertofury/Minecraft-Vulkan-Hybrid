package com.axalotl.async.forge.client.hari263.render;

import com.axalotl.async.forge.client.hari263.HariRenderState;
import me.jellysquid.mods.sodium.client.render.chunk.data.SectionRenderDataStorage;
import me.jellysquid.mods.sodium.client.render.chunk.vertex.format.ChunkVertexType;

public final class HariWorldBridge {
    private static HariTerrainRenderer renderer;
    private static boolean sessionFailed;

    private HariWorldBridge() {}

    public static void create(ChunkVertexType type) {
        destroy();
        if (HariRenderState.shouldUseInternalRenderer() && !sessionFailed) renderer = new HariTerrainRenderer(type);
    }

    public static void destroy() {
        if (renderer != null) {
            renderer.close();
            renderer = null;
        }
    }

    public static HariTerrainRenderer get() { return renderer; }
    public static boolean active() {
        return renderer != null && HariRenderState.shouldUseInternalRenderer() && !sessionFailed;
    }

    public static void invalidateStorage(SectionRenderDataStorage storage) {
        if (renderer != null) renderer.invalidateStorage(storage);
    }

    public static void failClosed() {
        sessionFailed = true;
        destroy();
    }
}
