package net.vulkanmod.gl;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.charset.StandardCharsets;

/** Shader API strings use caller-owned extents and preserve every buffer cursor. */
public final class GlShaderStrings {
    private GlShaderStrings() {}

    public static String readCString(ByteBuffer input) {
        if (input == null) throw new IllegalArgumentException("Shader API name required");
        int end = input.position();
        while (end < input.limit() && input.get(end) != 0) end++;
        byte[] bytes = new byte[end - input.position()];
        input.duplicate().get(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    public static String truncate(String value, int maxLength) {
        if (maxLength < 0) throw new IllegalArgumentException("Negative shader API string extent");
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        int count = Math.min(bytes.length, Math.max(0, maxLength - 1));
        return new String(bytes, 0, count, StandardCharsets.UTF_8);
    }

    public static int write(String value, ByteBuffer output) {
        if (output == null || output.remaining() == 0) return 0;
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        int count = Math.min(bytes.length, output.remaining() - 1), start = output.position();
        for (int i = 0; i < count; i++) output.put(start + i, bytes[i]);
        output.put(start + count, (byte) 0);
        return count;
    }

    public static void write(String value, IntBuffer length, ByteBuffer output) {
        int count = write(value, output);
        if (length != null && length.hasRemaining()) length.put(length.position(), count);
    }

    public static void write(String value, int[] length, ByteBuffer output) {
        int count = write(value, output);
        if (length != null && length.length != 0) length[0] = count;
    }
}
