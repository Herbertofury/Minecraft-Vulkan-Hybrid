package com.axalotl.async.forge.client.hari263.compute;

import com.axalotl.async.forge.client.hari263.meshlet.MeshletFrameList;
import com.axalotl.async.forge.client.hari263.meshlet.MeshletHeader;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/** Very-small-batch fast path: emit indirect offsets only; geometry remains Embeddium-owned. */
public final class CpuIndirectBuilder {
    private CpuIndirectBuilder() {}
    public static int buildAll(MeshletFrameList list, ByteBuffer out){
        out.clear();
        for(MeshletHeader h:list.headers()){
            out.putInt(h.primitiveCount*3); out.putInt(1); out.putInt(h.primitiveOffset); out.putInt(h.vertexOffset); out.putInt(0);
        }
        out.flip(); return list.size();
    }
    public static ByteBuffer ensureCapacity(ByteBuffer current,int draws){
        int need=Math.max(draws*ComputePipeline.COMMAND_BYTES,4096);
        if(current!=null && current.capacity()>=need){current.clear();return current;}
        return ByteBuffer.allocateDirect(need).order(ByteOrder.nativeOrder());
    }
}
