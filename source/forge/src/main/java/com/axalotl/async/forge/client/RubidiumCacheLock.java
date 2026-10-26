package com.axalotl.async.forge.client;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;

/** Use the same monitor as Rubidium's original synchronized acquire/invalidate. */
public final class RubidiumCacheLock {
    private RubidiumCacheLock() {}

    public static void apply(ClassNode cache) {
        if (!cache.name.equals("me/jellysquid/mods/sodium/client/world/cloned/ClonedChunkSectionCache"))
            throw new IllegalStateException("Unexpected Rubidium cache target: " + cache.name);
        var cleanup = cache.methods.stream()
                .filter(method -> method.name.equals("cleanup") && method.desc.equals("()V"))
                .toList();
        if (cleanup.size() != 1) throw new IllegalStateException("Rubidium cleanup source drift");
        for (String name : new String[]{"acquire", "invalidate"}) {
            var mutation = cache.methods.stream().filter(method -> method.name.equals(name)).toList();
            if (mutation.size() != 1 || (mutation.get(0).access & Opcodes.ACC_SYNCHRONIZED) == 0)
                throw new IllegalStateException("Rubidium cache mutation monitor source drift: " + name);
        }
        cleanup.get(0).access |= Opcodes.ACC_SYNCHRONIZED;
        System.out.println("[Hari/Compat] Rubidium cleanup shares the original acquire/invalidate monitor");
    }
}
