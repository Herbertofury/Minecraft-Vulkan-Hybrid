package com.axalotl.async.forge.client.hari263.gl;

import org.lwjgl.opengl.ARBIndirectParameters;
import org.lwjgl.opengl.GL15C;
import org.lwjgl.opengl.GL30C;
import org.lwjgl.opengl.GL43C;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;

/** Thin SSBO / indirect-buffer wrapper. Render-thread use only. */
public final class GpuBuffer implements AutoCloseable {
    private int id;
    private long capacityBytes;

    public GpuBuffer() {
        this.id = GL15C.glGenBuffers();
    }

    public int id() { return id; }

    public void allocate(long bytes, int usage) {
        GL15C.glBindBuffer(GL43C.GL_SHADER_STORAGE_BUFFER, id);
        GL15C.glBufferData(GL43C.GL_SHADER_STORAGE_BUFFER, bytes, usage);
        capacityBytes = bytes;
    }

    public void ensureCapacity(long bytes, int usage) {
        if (bytes > capacityBytes) {
            allocate(Math.max(bytes, capacityBytes == 0 ? bytes : capacityBytes * 2L), usage);
        }
    }

    public void uploadSub(long offset, ByteBuffer data) {
        GL15C.glBindBuffer(GL43C.GL_SHADER_STORAGE_BUFFER, id);
        GL15C.glBufferSubData(GL43C.GL_SHADER_STORAGE_BUFFER, offset, data);
    }

    public void uploadSub(long offset, IntBuffer data) {
        GL15C.glBindBuffer(GL43C.GL_SHADER_STORAGE_BUFFER, id);
        GL15C.glBufferSubData(GL43C.GL_SHADER_STORAGE_BUFFER, offset, data);
    }

    public void zeroRange(long offset, long bytes) {
        if (bytes <= 0) return;
        GL15C.glBindBuffer(GL43C.GL_SHADER_STORAGE_BUFFER, id);
        GL43C.glClearBufferSubData(GL43C.GL_SHADER_STORAGE_BUFFER, GL30C.GL_R8,
                offset, bytes, GL30C.GL_RED, GL30C.GL_UNSIGNED_BYTE, (ByteBuffer) null);
    }

    public void bindSsbo(int binding) {
        GL30C.glBindBufferBase(GL43C.GL_SHADER_STORAGE_BUFFER, binding, id);
    }

    public void bindDrawIndirect() {
        GL15C.glBindBuffer(GL43C.GL_DRAW_INDIRECT_BUFFER, id);
    }

    public void bindParameterBuffer() {
        GL15C.glBindBuffer(ARBIndirectParameters.GL_PARAMETER_BUFFER_ARB, id);
    }

    @Override
    public void close() {
        if (id != 0) {
            GL15C.glDeleteBuffers(id);
            id = 0;
            capacityBytes = 0;
        }
    }
}
