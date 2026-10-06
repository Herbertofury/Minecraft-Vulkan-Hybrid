package com.axalotl.async.forge.client.hari263.render;

import com.axalotl.async.common.config.AsyncConfig;
import com.axalotl.async.forge.client.hari263.compute.ComputePipeline;
import com.axalotl.async.forge.client.hari263.compute.CpuIndirectBuilder;
import com.axalotl.async.forge.client.hari263.gl.GpuBuffer;
import com.axalotl.async.forge.client.hari263.meshlet.MeshletFrameList;
import com.axalotl.async.forge.client.hari263.meshlet.MeshletHeader;
import com.axalotl.async.forge.mixin.client.embeddium.ShaderChunkRendererAccessor;
import com.mojang.logging.LogUtils;
import me.jellysquid.mods.sodium.client.SodiumClientMod;
import me.jellysquid.mods.sodium.client.gl.attribute.GlVertexAttributeBinding;
import me.jellysquid.mods.sodium.client.gl.device.CommandList;
import me.jellysquid.mods.sodium.client.gl.device.RenderDevice;
import me.jellysquid.mods.sodium.client.model.quad.properties.ModelQuadFacing;
import me.jellysquid.mods.sodium.client.render.chunk.ChunkRenderMatrices;
import me.jellysquid.mods.sodium.client.render.chunk.LocalSectionIndex;
import me.jellysquid.mods.sodium.client.render.chunk.SharedQuadIndexBuffer;
import me.jellysquid.mods.sodium.client.render.chunk.data.SectionRenderDataStorage;
import me.jellysquid.mods.sodium.client.render.chunk.data.SectionRenderDataUnsafe;
import me.jellysquid.mods.sodium.client.render.chunk.lists.ChunkRenderList;
import me.jellysquid.mods.sodium.client.render.chunk.lists.ChunkRenderListIterable;
import me.jellysquid.mods.sodium.client.render.chunk.region.RenderRegion;
import me.jellysquid.mods.sodium.client.render.chunk.shader.ChunkShaderBindingPoints;
import me.jellysquid.mods.sodium.client.render.chunk.shader.ChunkShaderInterface;
import me.jellysquid.mods.sodium.client.render.chunk.terrain.TerrainRenderPass;
import me.jellysquid.mods.sodium.client.render.chunk.vertex.format.ChunkMeshAttribute;
import me.jellysquid.mods.sodium.client.render.chunk.vertex.format.ChunkVertexType;
import me.jellysquid.mods.sodium.client.render.viewport.CameraTransform;
import org.joml.Matrix4f;
import org.lwjgl.opengl.ARBIndirectParameters;
import org.lwjgl.opengl.GL11C;
import org.lwjgl.opengl.GL15C;
import org.lwjgl.opengl.GL20C;
import org.lwjgl.opengl.GL30C;
import org.lwjgl.opengl.GL43C;
import org.lwjgl.opengl.GL46C;
import org.slf4j.Logger;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Iterator;

/**
 * 1.20.1 client terrain bridge inspired by Minecraft 26.3 MDI and portable GPU-driven rendering.
 * Embeddium remains authoritative for terrain mesh generation and storage. Hari keeps only draw-boundary
 * metadata persistently on the GPU and updates it when Embeddium mutates the corresponding region storage.
 */
public final class HariTerrainRenderer implements AutoCloseable {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int SECTION_MASK_BYTES = RenderRegion.REGION_SIZE * Integer.BYTES;

    private final ComputePipeline pipeline = new ComputePipeline();
    private final RegionCommandCache regionCache = new RegionCommandCache();
    private final PersistentRegionSceneCache persistentScenes = new PersistentRegionSceneCache();
    private final MeshletFrameList meshlets = new MeshletFrameList();
    private final float[] frustumPlanes = new float[24];
    private final SharedQuadIndexBuffer sharedIndexBuffer;
    private final GlVertexAttributeBinding[] bindings;
    private final ByteBuffer sectionMasks = ByteBuffer.allocateDirect(SECTION_MASK_BYTES).order(ByteOrder.nativeOrder());
    private ByteBuffer commandStaging;
    private int vao;
    private boolean persistentPathLogged;

    public HariTerrainRenderer(ChunkVertexType vertexType) {
        CommandList cmd = RenderDevice.INSTANCE.createCommandList();
        this.sharedIndexBuffer = new SharedQuadIndexBuffer(cmd, SharedQuadIndexBuffer.IndexType.INTEGER);
        cmd.flush();
        this.bindings = createStandardBindings(vertexType);
        this.vao = GL30C.glGenVertexArrays();
        LOGGER.info("Hari GPU terrain initialized: GPU threshold={} regionCache={} persistentScene={}",
                ComputePipeline.cpuThreshold(), RegionCommandCache.enabled(),
                AsyncConfig.enableGpuTerrainPersistentScene.getValue());
    }

    public boolean tryRender(ShaderChunkRendererAccessor shaders,
                             ChunkRenderMatrices matrices,
                             CommandList commandList,
                             ChunkRenderListIterable renderLists,
                             TerrainRenderPass renderPass,
                             CameraTransform camera) {
        shaders.harimt$begin(renderPass);
        var active = shaders.harimt$getActiveProgram();
        if (active == null) {
            shaders.harimt$end(renderPass);
            return false;
        }
        ChunkShaderInterface shader = active.getInterface();
        if (shader == null) {
            shaders.harimt$end(renderPass);
            return false;
        }
        try {
            active.bind();
            renderPassBody(shader, matrices, commandList, renderLists, renderPass, camera);
            return true;
        } catch (Throwable t) {
            LOGGER.error("Hari GPU terrain failed; falling back to Embeddium for this session", t);
            return false;
        } finally {
            shaders.harimt$end(renderPass);
        }
    }

    private void renderPassBody(ChunkShaderInterface shader,
                                ChunkRenderMatrices matrices,
                                CommandList commandList,
                                ChunkRenderListIterable renderLists,
                                TerrainRenderPass renderPass,
                                CameraTransform camera) {
        shader.setProjectionMatrix(matrices.projection());
        shader.setModelViewMatrix(matrices.modelView());

        Matrix4f clip = new Matrix4f(matrices.projection()).mul(matrices.modelView());
        FrustumUtil.extractPlanes(clip, frustumPlanes);
        float camX = camera.intX + camera.fracX;
        float camY = camera.intY + camera.fracY;
        float camZ = camera.intZ + camera.fracZ;
        boolean useBlockFaceCulling = SodiumClientMod.options().performance.useBlockFaceCulling;
        boolean persistentScene = AsyncConfig.enableGpuTerrainPersistentScene.getValue();

        GL30C.glBindVertexArray(vao);
        int passMaxElements = 0;
        try {
            Iterator<ChunkRenderList> iterator = renderLists.iterator(renderPass.isReverseOrder());
            while (iterator.hasNext()) {
                ChunkRenderList renderList = iterator.next();
                RenderRegion region = renderList.getRegion();
                SectionRenderDataStorage storage = region.getStorage(renderPass);
                RenderRegion.DeviceResources resources = region.getResources();
                if (storage == null || resources == null) continue;
                int glVbo = resources.getVertexBuffer().handle();
                int generation = ((SectionStorageGenerationAccess) (Object) storage).harimt$getGeneration();

                long visibilitySignature = buildSectionMasks(
                        region, renderList, renderPass, camera, useBlockFaceCulling, sectionMasks);
                int estimatedDraws = renderList.getSectionsWithGeometryCount() * ModelQuadFacing.COUNT;

                if (persistentScene && estimatedDraws > ComputePipeline.cpuThreshold()) {
                    PersistentRegionSceneCache.Entry scene = persistentScenes.getOrRebuild(region, storage, glVbo);
                    if (scene.meshletCount <= 0 || scene.maxElements <= 0) continue;
                    if (scene.maxElements > passMaxElements) {
                        sharedIndexBuffer.ensureCapacity(commandList, scene.maxElements);
                        passMaxElements = scene.maxElements;
                    }
                    ComputePipeline.DrawPrep prep = pipeline.preparePersistent(
                            scene.meshlets, scene.meshletCount, sectionMasks,
                            frustumPlanes, camX, camY, camZ);
                    if (prep.maxDrawCount() <= 0) continue;
                    if (!persistentPathLogged) {
                        persistentPathLogged = true;
                        LOGGER.info("Hari persistent GPU scene active: meshlets={} storageGeneration={} indirectCount={}",
                                scene.meshletCount, scene.storageGeneration, prep.indirectCount());
                    }
                    setRegionOffset(shader, region, camera);
                    drawIndirect(glVbo, pipeline.commands(), prep.maxDrawCount(), prep.indirectCount());
                    continue;
                }

                if (RegionCommandCache.enabled()) {
                    RegionCommandCache.Entry cached = regionCache.get(storage);
                    if (cached != null && cached.hit(glVbo, generation, visibilitySignature)) {
                        if (cached.maxElements > passMaxElements) {
                            sharedIndexBuffer.ensureCapacity(commandList, cached.maxElements);
                            passMaxElements = cached.maxElements;
                        }
                        setRegionOffset(shader, region, camera);
                        drawIndirect(glVbo, cached.commands, cached.drawCount, false);
                        continue;
                    }
                }

                meshlets.clear();
                int maxElements = collectVisibleMeshlets(region, storage, renderList, renderPass, sectionMasks);
                if (meshlets.size() == 0 || maxElements <= 0) continue;
                if (maxElements > passMaxElements) {
                    sharedIndexBuffer.ensureCapacity(commandList, maxElements);
                    passMaxElements = maxElements;
                }

                if (meshlets.size() <= ComputePipeline.cpuThreshold()) {
                    commandStaging = CpuIndirectBuilder.ensureCapacity(commandStaging, meshlets.size());
                    int draws = CpuIndirectBuilder.buildAll(meshlets, commandStaging);
                    GpuBuffer buffer;
                    if (RegionCommandCache.enabled()) {
                        RegionCommandCache.Entry entry = regionCache.getOrCreate(storage);
                        entry.upload(commandStaging, draws, meshlets.size(), maxElements, glVbo,
                                generation, visibilitySignature);
                        buffer = entry.commands;
                    } else {
                        pipeline.commands().ensureCapacity((long) draws * ComputePipeline.COMMAND_BYTES, GL15C.GL_DYNAMIC_DRAW);
                        pipeline.commands().uploadSub(0, commandStaging);
                        buffer = pipeline.commands();
                    }
                    setRegionOffset(shader, region, camera);
                    drawIndirect(glVbo, buffer, draws, false);
                } else {
                    ComputePipeline.DrawPrep prep = pipeline.prepareTransient(
                            meshlets, frustumPlanes, camX, camY, camZ, clip);
                    if (prep.maxDrawCount() <= 0) continue;
                    setRegionOffset(shader, region, camera);
                    drawIndirect(glVbo, pipeline.commands(), prep.maxDrawCount(), prep.indirectCount());
                }
            }
        } finally {
            GL15C.glBindBuffer(GL43C.GL_DRAW_INDIRECT_BUFFER, 0);
            GL30C.glBindVertexArray(0);
        }
    }

    private static long buildSectionMasks(RenderRegion region, ChunkRenderList list, TerrainRenderPass pass,
                                          CameraTransform camera, boolean useBlockFaceCulling, ByteBuffer masks) {
        masks.clear();
        for (int i = 0; i < RenderRegion.REGION_SIZE; i++) masks.putInt(0);

        var it = list.sectionsWithGeometryIterator(pass.isReverseOrder());
        long signature = 0xcbf29ce484222325L;
        if (it != null) {
            int originX = region.getChunkX();
            int originY = region.getChunkY();
            int originZ = region.getChunkZ();
            while (it.hasNext()) {
                int section = it.nextByteAsInt();
                int chunkX = originX + LocalSectionIndex.unpackX(section);
                int chunkY = originY + LocalSectionIndex.unpackY(section);
                int chunkZ = originZ + LocalSectionIndex.unpackZ(section);
                int faces = useBlockFaceCulling
                        ? visibleFaces(camera.intX, camera.intY, camera.intZ, chunkX, chunkY, chunkZ)
                        : ModelQuadFacing.ALL;
                masks.putInt(section * Integer.BYTES, faces);
                signature = RegionCommandCache.fnv(signature, section);
                signature = RegionCommandCache.fnv(signature, faces);
            }
        }
        masks.position(0);
        masks.limit(SECTION_MASK_BYTES);
        return signature;
    }

    private int collectVisibleMeshlets(RenderRegion region, SectionRenderDataStorage storage,
                                       ChunkRenderList list, TerrainRenderPass pass, ByteBuffer masks) {
        var it = list.sectionsWithGeometryIterator(pass.isReverseOrder());
        if (it == null) return 0;
        int originX = region.getChunkX();
        int originY = region.getChunkY();
        int originZ = region.getChunkZ();
        int maxElements = 0;

        while (it.hasNext()) {
            int section = it.nextByteAsInt();
            int allowedFaces = masks.getInt(section * Integer.BYTES);
            if (allowedFaces == 0) continue;
            long ptr = storage.getDataPointer(section);
            int slices = SectionRenderDataUnsafe.getSliceMask(ptr) & allowedFaces;
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
                int vertexOffset = SectionRenderDataUnsafe.getVertexOffset(ptr, facing);

                MeshletHeader meshlet = meshlets.acquire();
                meshlet.vertexOffset = vertexOffset;
                meshlet.primitiveOffset = 0;
                meshlet.primitiveCount = elements / 3;
                meshlet.sectionIndex = section;
                meshlet.facingMask = facingMask;
                meshlet.sphereX = sx;
                meshlet.sphereY = sy;
                meshlet.sphereZ = sz;
                meshlet.sphereRadius = 14.0f;
                meshlet.coneW = 2.0f;
                maxElements = Math.max(maxElements, elements);
            }
        }
        return maxElements;
    }

    private static int visibleFaces(int x, int y, int z, int chunkX, int chunkY, int chunkZ) {
        int minX = chunkX << 4, maxX = minX + 16;
        int minY = chunkY << 4, maxY = minY + 16;
        int minZ = chunkZ << 4, maxZ = minZ + 16;
        int planes = 1 << ModelQuadFacing.UNASSIGNED.ordinal();
        planes |= greater(x, minX - 3) << ModelQuadFacing.POS_X.ordinal();
        planes |= greater(y, minY - 3) << ModelQuadFacing.POS_Y.ordinal();
        planes |= greater(z, minZ - 3) << ModelQuadFacing.POS_Z.ordinal();
        planes |= less(x, maxX + 3) << ModelQuadFacing.NEG_X.ordinal();
        planes |= less(y, maxY + 3) << ModelQuadFacing.NEG_Y.ordinal();
        planes |= less(z, maxZ + 3) << ModelQuadFacing.NEG_Z.ordinal();
        return planes;
    }

    private static int greater(int a, int b) { return (b - a) >>> 31; }
    private static int less(int a, int b) { return (a - b) >>> 31; }

    private static void setRegionOffset(ChunkShaderInterface shader, RenderRegion region, CameraTransform camera) {
        shader.setRegionOffset((region.getOriginX() - camera.intX) - camera.fracX,
                (region.getOriginY() - camera.intY) - camera.fracY,
                (region.getOriginZ() - camera.intZ) - camera.fracZ);
    }

    private void drawIndirect(int glVbo, GpuBuffer commands, int drawCount, boolean indirectCount) {
        GL15C.glBindBuffer(GL15C.GL_ARRAY_BUFFER, glVbo);
        enableAttributes();
        GL15C.glBindBuffer(GL15C.GL_ELEMENT_ARRAY_BUFFER, sharedIndexBuffer.getBufferObject().handle());
        commands.bindDrawIndirect();
        if (indirectCount) {
            pipeline.counters().bindParameterBuffer();
            if (org.lwjgl.opengl.GL.getCapabilities().OpenGL46) {
                GL46C.glMultiDrawElementsIndirectCount(GL11C.GL_TRIANGLES, GL11C.GL_UNSIGNED_INT,
                        0L, 0L, drawCount, 0);
            } else {
                ARBIndirectParameters.glMultiDrawElementsIndirectCountARB(GL11C.GL_TRIANGLES, GL11C.GL_UNSIGNED_INT,
                        0L, 0L, drawCount, 0);
            }
            GL15C.glBindBuffer(ARBIndirectParameters.GL_PARAMETER_BUFFER_ARB, 0);
        } else {
            GL43C.glMultiDrawElementsIndirect(GL11C.GL_TRIANGLES, GL11C.GL_UNSIGNED_INT, 0L, drawCount, 0);
        }
    }

    private void enableAttributes() {
        for (GlVertexAttributeBinding binding : bindings) {
            int index = binding.getIndex();
            GL20C.glEnableVertexAttribArray(index);
            if (binding.isIntType()) {
                GL30C.glVertexAttribIPointer(index, binding.getCount(), binding.getFormat(),
                        binding.getStride(), binding.getPointer());
            } else {
                GL20C.glVertexAttribPointer(index, binding.getCount(), binding.getFormat(),
                        binding.isNormalized(), binding.getStride(), binding.getPointer());
            }
        }
    }

    private static GlVertexAttributeBinding[] createStandardBindings(ChunkVertexType type) {
        var format = type.getVertexFormat();
        return new GlVertexAttributeBinding[]{
                new GlVertexAttributeBinding(ChunkShaderBindingPoints.ATTRIBUTE_POSITION_ID,
                        format.getAttribute(ChunkMeshAttribute.POSITION_MATERIAL_MESH)),
                new GlVertexAttributeBinding(ChunkShaderBindingPoints.ATTRIBUTE_COLOR,
                        format.getAttribute(ChunkMeshAttribute.COLOR_SHADE)),
                new GlVertexAttributeBinding(ChunkShaderBindingPoints.ATTRIBUTE_BLOCK_TEXTURE,
                        format.getAttribute(ChunkMeshAttribute.BLOCK_TEXTURE)),
                new GlVertexAttributeBinding(ChunkShaderBindingPoints.ATTRIBUTE_LIGHT_TEXTURE,
                        format.getAttribute(ChunkMeshAttribute.LIGHT_TEXTURE))};
    }

    public void invalidateStorage(SectionRenderDataStorage storage) {
        regionCache.invalidate(storage);
        persistentScenes.invalidate(storage);
    }

    @Override
    public void close() {
        pipeline.close();
        regionCache.close();
        persistentScenes.close();
        if (vao != 0) {
            GL30C.glDeleteVertexArrays(vao);
            vao = 0;
        }
        CommandList cmd = RenderDevice.INSTANCE.createCommandList();
        sharedIndexBuffer.delete(cmd);
        cmd.flush();
    }
}
