package hashbench;

/** Chua cac bo dem tuy chon dung chung trong che do --count. */
public final class Counters {
    public static final boolean ON = Main.countMode;

    public static long probeCount;
    public static long rebuildCount;
    public static long rebuildNanos;

    private Counters() {
    }

    /** Dat moi bo dem ve 0 truoc mot luot chay. */
    public static void reset() {
        probeCount = 0L;
        rebuildCount = 0L;
        rebuildNanos = 0L;
    }

    /** Cong so lan tham do khi che do dem duoc bat. */
    public static void addProbes(long count) {
        if (ON) {
            probeCount += count;
        }
    }
}
