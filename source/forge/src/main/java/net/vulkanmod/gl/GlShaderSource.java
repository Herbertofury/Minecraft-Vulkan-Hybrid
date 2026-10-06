package net.vulkanmod.gl;

import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryUtil;

import java.nio.IntBuffer;
import java.util.Objects;

/** Preserve every source string and the caller's buffer positions at GL API boundaries. */
public final class GlShaderSource {
    private GlShaderSource() {}

    public static String concatenate(CharSequence[] strings) {
        Objects.requireNonNull(strings, "shader source strings");
        StringBuilder source = new StringBuilder();
        for (CharSequence string : strings) source.append(Objects.requireNonNull(string, "shader source string"));
        return source.toString();
    }

    public static String concatenate(PointerBuffer strings, IntBuffer lengths) {
        Objects.requireNonNull(strings, "shader source pointers");
        if (lengths != null && lengths.remaining() < strings.remaining()) {
            throw new IllegalArgumentException("Not enough shader source lengths");
        }
        StringBuilder source = new StringBuilder();
        for (int i = 0; i < strings.remaining(); i++) {
            int length = lengths == null ? -1 : lengths.get(lengths.position() + i);
            source.append(read(strings.get(strings.position() + i), length));
        }
        return source.toString();
    }

    public static String concatenate(PointerBuffer strings, int[] lengths) {
        Objects.requireNonNull(strings, "shader source pointers");
        if (lengths != null && lengths.length < strings.remaining()) {
            throw new IllegalArgumentException("Not enough shader source lengths");
        }
        StringBuilder source = new StringBuilder();
        for (int i = 0; i < strings.remaining(); i++) {
            source.append(read(strings.get(strings.position() + i), lengths == null ? -1 : lengths[i]));
        }
        return source.toString();
    }

    private static String read(long pointer, int length) {
        if (pointer == 0) throw new IllegalArgumentException("Null shader source pointer");
        return length < 0 ? MemoryUtil.memUTF8(pointer) : MemoryUtil.memUTF8(pointer, length);
    }
}
