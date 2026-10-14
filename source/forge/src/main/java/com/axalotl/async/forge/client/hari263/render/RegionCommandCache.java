package com.axalotl.async.forge.client.hari263.render;

import com.axalotl.async.common.config.AsyncConfig;
import com.axalotl.async.forge.client.hari263.gl.GpuBuffer;
import me.jellysquid.mods.sodium.client.render.chunk.data.SectionRenderDataStorage;
import org.lwjgl.opengl.GL15C;

import java.nio.ByteBuffer;
import java.util.IdentityHashMap;
import java.util.Map;

/** Cached CPU-built indirect commands keyed by Embeddium's pass-specific storage identity. */
public final class RegionCommandCache implements AutoCloseable {
    public static boolean enabled() { return AsyncConfig.enableGpuTerrainRegionCache.getValue(); }

    public static final class Entry {
        public long visibilitySignature;
        public int storageGeneration, drawCount, meshletIn, maxElements, glVbo;
        public final GpuBuffer commands = new GpuBuffer();

        public void upload(ByteBuffer data, int draws, int meshlets, int maxElem, int vbo,
                           int generation, long signature) {
            long bytes = (long) draws * 20L;
            commands.ensureCapacity(Math.max(bytes, 256), GL15C.GL_DYNAMIC_DRAW);
            commands.uploadSub(0, data);
            drawCount = draws;
            meshletIn = meshlets;
            maxElements = maxElem;
            glVbo = vbo;
            storageGeneration = generation;
            visibilitySignature = signature;
        }

        public boolean hit(int vbo, int generation, long signature) {
            return drawCount > 0 && glVbo == vbo && storageGeneration == generation
                    && visibilitySignature == signature;
        }
    }

    private final Map<SectionRenderDataStorage, Entry> entries = new IdentityHashMap<>();

    public Entry get(SectionRenderDataStorage storage) { return entries.get(storage); }
    public Entry getOrCreate(SectionRenderDataStorage storage) {
        return entries.computeIfAbsent(storage, key -> new Entry());
    }
    public void invalidate(SectionRenderDataStorage storage) {
        Entry entry = entries.remove(storage);
        if (entry != null) entry.commands.close();
    }

    public static long fnv(long h, int v) {
        h ^= (v & 0xffffffffL);
        return h * 1099511628211L;
    }

    @Override
    public void close() {
        for (Entry entry : entries.values()) entry.commands.close();
        entries.clear();
    }
}
