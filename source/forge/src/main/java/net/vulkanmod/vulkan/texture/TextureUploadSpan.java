package net.vulkanmod.vulkan.texture;

/** Exact source span consumed by one Vulkan buffer-to-image rectangle. */
public final class TextureUploadSpan {
    private TextureUploadSpan() {}

    public static int rowLength(int width, int unpackRowLength) {
        int rowLength = unpackRowLength == 0 ? width : unpackRowLength;
        if (width <= 0 || rowLength < width)
            throw new IllegalArgumentException("Invalid texture upload row length");
        return rowLength;
    }

    public static int sourceOffset(int rowLength, int skipRows, int skipPixels, int formatSize) {
        if (rowLength <= 0 || skipRows < 0 || skipPixels < 0 || formatSize <= 0)
            throw new IllegalArgumentException("Invalid texture upload source layout");
        return Math.toIntExact(Math.multiplyExact(
                Math.addExact(Math.multiplyExact((long) rowLength, skipRows), skipPixels), formatSize));
    }

    public static int byteSize(int width, int height, int rowLength, int formatSize) {
        if (width <= 0 || height <= 0 || rowLength < width || formatSize <= 0)
            throw new IllegalArgumentException("Invalid texture upload extent");
        return Math.toIntExact(Math.multiplyExact(
                Math.addExact(Math.multiplyExact((long) rowLength, height - 1), width), formatSize));
    }
}
