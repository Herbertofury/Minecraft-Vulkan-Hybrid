package net.vulkanmod.render.chunk.buffer;

/** Reusable region storage for one source/destination pair. Never retains source pointers. */
public final class UploadCopies {
    public static final int CAPACITY = 128;
    private final long[] sourceOffsets = new long[CAPACITY];
    private final long[] destinationOffsets = new long[CAPACITY];
    private final long[] sizes = new long[CAPACITY];
    private long source, destination;
    private int count;

    public boolean needsFlush(long source, long destination) {
        return count == CAPACITY || count != 0 && (this.source != source || this.destination != destination);
    }

    public void add(long source, long sourceOffset, long destination, long destinationOffset, long size) {
        if (needsFlush(source, destination)) throw new IllegalStateException("Copy batch must be flushed");
        if (size <= 0 || sourceOffset < 0 || destinationOffset < 0)
            throw new IllegalArgumentException("Invalid copy region");
        this.source = source;
        this.destination = destination;
        sourceOffsets[count] = sourceOffset;
        destinationOffsets[count] = destinationOffset;
        sizes[count++] = size;
    }

    public int count() { return count; }
    public long source() { return source; }
    public long destination() { return destination; }
    public long sourceOffset(int index) { return sourceOffsets[index]; }
    public long destinationOffset(int index) { return destinationOffsets[index]; }
    public long size(int index) { return sizes[index]; }
    public void clear() { count = 0; }
}
