package mvhgrasscompat;

/** Uncached sections have no snapshot to invalidate; their queued first build reads current state. */
public final class InvalidationRetention {
    private InvalidationRetention() {}
    public static boolean retain(boolean cached, boolean inFlight) {
        return cached || inFlight;
    }
}
