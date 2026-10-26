package com.axalotl.async.forge.client.hari263.meshlet;

import java.util.ArrayList;
import java.util.List;

public final class MeshletFrameList {
    private final ArrayList<MeshletHeader> pool = new ArrayList<>(4096);
    private int size;
    public void clear() { size = 0; }
    public MeshletHeader acquire() {
        if (size < pool.size()) return pool.get(size++);
        MeshletHeader h = new MeshletHeader();
        pool.add(h); size++; return h;
    }
    public List<MeshletHeader> headers() { return pool.subList(0, size); }
    public int size() { return size; }
}
