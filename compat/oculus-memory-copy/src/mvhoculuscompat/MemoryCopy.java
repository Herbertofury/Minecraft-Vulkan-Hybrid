/* SPDX-License-Identifier: LGPL-3.0-only
 * Copyright 2026 Minecraft Vulkan Hybrid contributors.
 * Compatibility with the Embeddium 0.3.31 / Sodium public memory-copy API.
 * Preserves its JDK copy primitive without loading the Embeddium renderer.
 */
package mvhoculuscompat;

import java.lang.reflect.Field;
import sun.misc.Unsafe;

public final class MemoryCopy {
    private static final Unsafe MEMORY;
    static {
        try {
            Field field=Unsafe.class.getDeclaredField("theUnsafe");
            field.setAccessible(true);
            MEMORY=(Unsafe)field.get(null);
        } catch (NoSuchFieldException | IllegalAccessException failure) {
            throw new RuntimeException("Unable to obtain the existing JDK memory-copy primitive",failure);
        }
    }
    private MemoryCopy() {}
    public static void copyMemory(long source,long destination,int bytes) {
        MEMORY.copyMemory(source,destination,(long)bytes);
    }
}
