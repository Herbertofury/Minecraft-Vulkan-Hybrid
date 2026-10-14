package com.axalotl.async.forge.client.hari263.compute;

import com.axalotl.async.common.config.AsyncConfig;
import com.axalotl.async.forge.client.hari263.HariRenderState;
import com.axalotl.async.forge.client.hari263.gl.GpuBuffer;
import com.axalotl.async.forge.client.hari263.gl.ShaderProgram;
import com.axalotl.async.forge.client.hari263.meshlet.MeshletFrameList;
import com.axalotl.async.forge.client.hari263.meshlet.MeshletHeader;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL15C;
import org.lwjgl.opengl.GL20C;
import org.lwjgl.opengl.GL30C;
import org.lwjgl.opengl.GL42C;
import org.lwjgl.opengl.GL43C;
import org.lwjgl.system.MemoryStack;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

/** GPU frustum/section compaction + DrawElementsIndirect generation. Never writes vertex attributes. */
public final class ComputePipeline implements AutoCloseable {
    public static final int MAX_MESHLETS = 65536;
    public static final int COMMAND_BYTES = 20;
    public static final int SECTION_MASK_BYTES = 256 * Integer.BYTES;

    private final ShaderProgram compute = ShaderProgram.compileCompute("/assets/harimt/shaders/hari_terrain_indirect.glsl");
    private final GpuBuffer transientMeshlets = new GpuBuffer();
    private final GpuBuffer sectionMasks = new GpuBuffer();
    private final GpuBuffer counters = new GpuBuffer();
    private final GpuBuffer commands = new GpuBuffer();
    private final int uPlanes = compute.uniformLocation("uFrustumPlanes");
    private final int uCamera = compute.uniformLocation("uCameraPos");
    private final int uCount = compute.uniformLocation("uMeshletCount");
    private final int uUseSectionMasks = compute.uniformLocation("uUseSectionMasks");
    private ByteBuffer staging;

    public ComputePipeline() {
        transientMeshlets.allocate((long) MAX_MESHLETS * MeshletHeader.BYTES, GL15C.GL_DYNAMIC_DRAW);
        sectionMasks.allocate(SECTION_MASK_BYTES, GL15C.GL_STREAM_DRAW);
        counters.allocate(16, GL15C.GL_DYNAMIC_DRAW);
        commands.allocate((long) MAX_MESHLETS * COMMAND_BYTES, GL15C.GL_DYNAMIC_DRAW);
    }

    public record DrawPrep(int maxDrawCount, boolean indirectCount) {}

    public DrawPrep prepareTransient(MeshletFrameList list, float[] planes, float camX, float camY, float camZ,
                                     Matrix4f unused) {
        int count = Math.min(list.size(), MAX_MESHLETS);
        if (count <= 0) return new DrawPrep(0, false);
        int bytes = count * MeshletHeader.BYTES;
        staging = ensure(staging, bytes);
        staging.clear();
        for (int i = 0; i < count; i++) list.headers().get(i).write(staging);
        staging.flip();
        transientMeshlets.uploadSub(0, staging);
        return dispatch(transientMeshlets, count, null, false, planes, camX, camY, camZ);
    }

    public DrawPrep preparePersistent(GpuBuffer persistentMeshlets, int count, ByteBuffer masks,
                                      float[] planes, float camX, float camY, float camZ) {
        count = Math.min(count, MAX_MESHLETS);
        if (count <= 0) return new DrawPrep(0, false);
        if (masks == null || masks.remaining() != SECTION_MASK_BYTES) {
            throw new IllegalArgumentException("expected exactly " + SECTION_MASK_BYTES + " bytes of section masks");
        }
        sectionMasks.uploadSub(0, masks);
        return dispatch(persistentMeshlets, count, sectionMasks, true, planes, camX, camY, camZ);
    }

    private DrawPrep dispatch(GpuBuffer meshletBuffer, int count, GpuBuffer masks, boolean useMasks,
                              float[] planes, float camX, float camY, float camZ) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            counters.uploadSub(0, stack.ints(0, 0, 0, 0));
        }
        boolean useCount = HariRenderState.hasIndirectCount();
        if (!useCount) commands.zeroRange(0, (long) count * COMMAND_BYTES);

        int previous = GL20C.glGetInteger(GL20C.GL_CURRENT_PROGRAM);
        compute.use();
        if (uCount >= 0) GL30C.glUniform1ui(uCount, count);
        if (uUseSectionMasks >= 0) GL30C.glUniform1ui(uUseSectionMasks, useMasks ? 1 : 0);
        if (uCamera >= 0) GL20C.glUniform3f(uCamera, camX, camY, camZ);
        if (uPlanes >= 0) {
            try (MemoryStack stack = MemoryStack.stackPush()) {
                FloatBuffer fb = stack.mallocFloat(24);
                fb.put(planes).flip();
                GL20C.glUniform4fv(uPlanes, fb);
            }
        }

        meshletBuffer.bindSsbo(0);
        if (masks != null) masks.bindSsbo(1);
        sectionMasks.bindSsbo(1);
        counters.bindSsbo(2);
        commands.bindSsbo(3);
        GL43C.glDispatchCompute((count + 63) / 64, 1, 1);
        GL42C.glMemoryBarrier(GL43C.GL_COMMAND_BARRIER_BIT | GL43C.GL_SHADER_STORAGE_BARRIER_BIT);
        GL20C.glUseProgram(previous);
        return new DrawPrep(count, useCount);
    }

    public static int cpuThreshold() {
        int value = AsyncConfig.gpuTerrainCpuThreshold.getValue();
        String override = System.getProperty("harimt.gpuTerrainCpuThreshold");
        if (override != null && !override.isBlank()) {
            try {
                value = Integer.parseInt(override.trim());
            } catch (NumberFormatException ignored) {
                // Invalid diagnostic override falls back to the normal config value.
            }
        }
        return Math.max(1, Math.min(MAX_MESHLETS, value));
    }

    public GpuBuffer commands() { return commands; }
    public GpuBuffer counters() { return counters; }

    private static ByteBuffer ensure(ByteBuffer buffer, int bytes) {
        if (buffer != null && buffer.capacity() >= bytes) return buffer;
        int cap = buffer == null ? bytes : Math.max(bytes, buffer.capacity() * 2);
        return ByteBuffer.allocateDirect(cap).order(ByteOrder.nativeOrder());
    }

    @Override
    public void close() {
        compute.close();
        transientMeshlets.close();
        sectionMasks.close();
        counters.close();
        commands.close();
    }
}
