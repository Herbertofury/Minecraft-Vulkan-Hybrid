package com.axalotl.async.common;

import net.minecraft.server.level.ServerLevel;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Supplier;

/** Execute owner-only side effects on the world thread, retaining results and failures. */
public final class C2meThreadBoundary {
    private C2meThreadBoundary() {}

    public static boolean mustHandoff() {
        return AsyncCommon.HARICHUNK && Thread.currentThread() instanceof AsyncWorkerThread;
    }

    public static <T> T call(ServerLevel world, Supplier<T> action) {
        if (!mustHandoff()) return action.get();
        try {
            return CompletableFuture.supplyAsync(action,
                    ((ChunkOwnerExecutor) (Object) world.getChunkSource()).harimt$ownerExecutor()).join();
        } catch (CompletionException failure) {
            Throwable cause = failure.getCause();
            if (cause instanceof RuntimeException runtime) throw runtime;
            if (cause instanceof Error error) throw error;
            throw failure;
        }
    }

    public static void run(ServerLevel world, Runnable action) {
        call(world, () -> { action.run(); return null; });
    }
}
