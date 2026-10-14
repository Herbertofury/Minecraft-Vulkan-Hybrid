package mvhgrasscompat;
/** Original cached refreshes remain eligible; only a known invisible first build may be skipped. */
public final class FirstBuildEligibility {
    public static long SKIPPED_CANDIDATES;
    public static boolean skip(boolean inFlight,boolean cached,boolean visibilityKnown,boolean visible){return inFlight||(!cached&&visibilityKnown&&!visible);}
}
