package com.axalotl.async.common.spawn;

import net.minecraft.world.level.chunk.LevelChunk;

/**
 * Worker-local chunk identity for one NaturalSpawner.spawnForChunk call.
 *
 * Latest Async moved back to this model after broad off-thread chunk lookup
 * handling caused spawn regressions. It lets spawn internals reuse the exact
 * LevelChunk already scheduled by ServerChunkCache instead of asking the world
 * chunk source again from a worker thread.
 */
public final class SpawnChunkContext {
    private static final ThreadLocal<LevelChunk> CURRENT = new ThreadLocal<>();

    private SpawnChunkContext() {}

    public static LevelChunk current() {
        return CURRENT.get();
    }

    public static void runWith(LevelChunk chunk, Runnable action) {
        LevelChunk previous = CURRENT.get();
        CURRENT.set(chunk);
        try {
            action.run();
        } finally {
            if (previous == null) CURRENT.remove();
            else CURRENT.set(previous);
        }
    }

    public static void clear() {
        CURRENT.remove();
    }
}
