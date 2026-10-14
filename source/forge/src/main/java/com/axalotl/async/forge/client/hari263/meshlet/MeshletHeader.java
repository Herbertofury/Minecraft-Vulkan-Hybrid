package com.axalotl.async.forge.client.hari263.meshlet;

import java.nio.ByteBuffer;

/** Draw-boundary metadata only; Hari never rewrites Embeddium vertex attributes. */
public final class MeshletHeader {
    // std430-friendly: eight uints followed by two vec4s.
    public static final int BYTES = 64;
    public int vertexOffset;
    public int primitiveOffset;
    public int primitiveCount;
    public int sectionIndex;
    public int facingMask;
    public float sphereX, sphereY, sphereZ, sphereRadius;
    public float coneX, coneY, coneZ, coneW = 2.0f;

    public void write(ByteBuffer dst) {
        dst.putInt(vertexOffset);
        dst.putInt(primitiveCount * 3);
        dst.putInt(primitiveOffset);
        dst.putInt(primitiveCount);
        dst.putInt(sectionIndex);
        dst.putInt(facingMask);
        dst.putInt(0);
        dst.putInt(0);
        dst.putFloat(sphereX);
        dst.putFloat(sphereY);
        dst.putFloat(sphereZ);
        dst.putFloat(sphereRadius);
        dst.putFloat(coneX);
        dst.putFloat(coneY);
        dst.putFloat(coneZ);
        dst.putFloat(coneW);
    }
}
