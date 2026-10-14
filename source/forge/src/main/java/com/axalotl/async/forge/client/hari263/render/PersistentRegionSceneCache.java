package com.axalotl.async.forge.client.hari263.render;

import com.axalotl.async.forge.client.hari263.gl.GpuBuffer;
import com.axalotl.async.forge.client.hari263.meshlet.MeshletHeader;
import me.jellysquid.mods.sodium.client.model.quad.properties.ModelQuadFacing;
import me.jellysquid.mods.sodium.client.render.chunk.LocalSectionIndex;
import me.jellysquid.mods.sodium.client.render.chunk.data.SectionRenderDataStorage;
import me.jellysquid.mods.sodium.client.render.chunk.data.SectionRenderDataUnsafe;
import me.jellysquid.mods.sodium.client.render.chunk.region.RenderRegion;
import org.lwjgl.opengl.GL15C;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Persistent GPU copy of draw-boundary metadata. Geometry remains owned by Embeddium.
 * Rebuilt only when the owning SectionRenderDataStorage mutates or its VBO changes.
 */
public final class PersistentRegionSceneCache implements AutoCloseable {
    public static final class Entry implements AutoCloseable {
        public final GpuBuffer meshlets = new GpuBuffer();
        public int storageGeneration;
        public int glVbo;
        public int meshletCount;
        public int maxElements;

        @Override
        public void close() { meshlets.close(); }
    }

    private static final int MAX_REGION_MESHLETS = RenderRegion.REGION_SIZE * ModelQuadFacing.COUNT;
    private final Map<SectionRenderDataStorage, Entry> entries = new IdentityHashMap<>();
    private final MeshletHeader scratch = new MeshletHeader();
    private ByteBuffer staging = ByteBuffer.allocateDirect(MAX_REGION_MESHLETS * MeshletHeader.BYTES)
            .order(ByteOrder.nativeOrder());

    public Entry getOrRebuild(RenderRegion region, SectionRenderDataStorage storage, int glVbo) {
        int generation = ((SectionStorageGenerationAccess) (Object) storage).harimt$getGeneration();
        Entry entry = entries.computeIfAbsent(storage, ignored -> new Entry());
        if (entry.glVbo == glVbo && entry.storageGeneration == generation) return entry;

        staging.clear();
        int meshletCount = 0;
        int maxElements = 0;
        int originX = region.getChunkX();
        int originY = region.getChunkY();
        int originZ = region.getChunkZ();

        for (int section = 0; section < RenderRegion.REGION_SIZE; section++) {
            long ptr = storage.getDataPointer(section);
            int slices = SectionRenderDataUnsafe.getSliceMask(ptr);
            if (slices == 0) continue;

            int chunkX = originX + LocalSectionIndex.unpackX(section);
            int chunkY = originY + LocalSectionIndex.unpackY(section);
            int chunkZ = originZ + LocalSectionIndex.unpackZ(section);
            float sx = (chunkX << 4) + 8.0f;
            float sy = (chunkY << 4) + 8.0f;
            float sz = (chunkZ << 4) + 8.0f;

            for (int facing = 0; facing < ModelQuadFacing.COUNT; facing++) {
                int facingMask = 1 << facing;
                if ((slices & facingMask) == 0) continue;
                int elements = SectionRenderDataUnsafe.getElementCount(ptr, facing);
                if (elements <= 0) continue;

                scratch.vertexOffset = SectionRenderDataUnsafe.getVertexOffset(ptr, facing);
                scratch.primitiveOffset = 0;
                scratch.primitiveCount = elements / 3;
                scratch.sectionIndex = section;
                scratch.facingMask = facingMask;
                scratch.sphereX = sx;
                scratch.sphereY = sy;
                scratch.sphereZ = sz;
                scratch.sphereRadius = 14.0f;
                scratch.coneX = 0.0f;
                scratch.coneY = 0.0f;
                scratch.coneZ = 0.0f;
                scratch.coneW = 2.0f;
                scratch.write(staging);
                meshletCount++;
                maxElements = Math.max(maxElements, elements);
            }
        }

        staging.flip();
        if (meshletCount > 0) {
            entry.meshlets.ensureCapacity((long) meshletCount * MeshletHeader.BYTES, GL15C.GL_DYNAMIC_DRAW);
            entry.meshlets.uploadSub(0, staging);
        }
        entry.meshletCount = meshletCount;
        entry.maxElements = maxElements;
        entry.glVbo = glVbo;
        entry.storageGeneration = generation;
        return entry;
    }

    public void invalidate(SectionRenderDataStorage storage) {
        Entry entry = entries.remove(storage);
        if (entry != null) entry.close();
    }

    @Override
    public void close() {
        for (Entry entry : entries.values()) entry.close();
        entries.clear();
    }
}
