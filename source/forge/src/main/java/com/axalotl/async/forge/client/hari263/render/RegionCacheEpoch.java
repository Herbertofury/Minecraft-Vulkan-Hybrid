package com.axalotl.async.forge.client.hari263.render;

import java.util.concurrent.atomic.AtomicInteger;

public final class RegionCacheEpoch {
    private static final AtomicInteger EPOCH = new AtomicInteger(1);
    private RegionCacheEpoch() {}
    public static int current() { return EPOCH.get(); }
    public static void bump() { EPOCH.updateAndGet(v -> v == Integer.MAX_VALUE ? 1 : v + 1); }
}
