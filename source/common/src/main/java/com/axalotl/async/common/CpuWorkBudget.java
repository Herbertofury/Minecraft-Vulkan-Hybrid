package com.axalotl.async.common;

/** Coordinates Hari-owned pools. External chunk engines keep their own scheduler. */
public final class CpuWorkBudget {
    private static volatile boolean client;
    private static volatile boolean externalChunks;

    private CpuWorkBudget() {}

    public static void configure(boolean clientSide, boolean chunkProvider) {
        client = clientSide;
        externalChunks = chunkProvider;
    }

    public static int entityWorkers() {
        return entityWorkers(Runtime.getRuntime().availableProcessors(), client, externalChunks);
    }

    public static int meshWorkers() {
        return meshWorkers(Runtime.getRuntime().availableProcessors(), externalChunks);
    }

    public static int entityWorkers(int processors, boolean clientSide, boolean chunkProvider) {
        int usable = Math.max(1, processors - (clientSide ? 2 : 1));
        int divisor = clientSide ? (chunkProvider ? 3 : 2) : (chunkProvider ? 2 : 1);
        return Math.max(1, usable / divisor);
    }

    public static int meshWorkers(int processors, boolean chunkProvider) {
        return Math.max(1, Math.max(1, processors - 2) / (chunkProvider ? 3 : 2));
    }
}
