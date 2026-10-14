package net.vulkanmod.render.chunk.buffer;

import java.util.Arrays;

/** Tracks outstanding destination writes; touching intervals do not overlap. */
public final class UploadRanges {
    private long[] buffers = new long[32];
    private long[] starts = new long[32];
    private long[] ends = new long[32];
    private int count;

    public boolean overlaps(long buffer, long offset, long size) {
        if (offset < 0 || size <= 0 || offset > Long.MAX_VALUE - size)
            throw new IllegalArgumentException("Invalid upload interval");
        long end = offset + size;
        for (int i = 0; i < count; i++)
            if (buffers[i] == buffer && offset < ends[i] && starts[i] < end) return true;
        return false;
    }

    public void add(long buffer, long offset, long size) {
        if (count == buffers.length) {
            int capacity = Math.multiplyExact(count, 2);
            buffers = Arrays.copyOf(buffers, capacity);
            starts = Arrays.copyOf(starts, capacity);
            ends = Arrays.copyOf(ends, capacity);
        }
        buffers[count] = buffer;
        starts[count] = offset;
        ends[count++] = Math.addExact(offset, size);
    }

    public void clear() { count = 0; }
}
