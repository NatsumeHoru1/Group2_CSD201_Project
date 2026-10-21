package hashbench;

/** Chứa các bộ đếm tùy chọn dùng chung trong chế độ --count. */
public final class Counters {
    public static final boolean ON = Main.countMode;

    public static long probeCount;
    public static long rebuildCount;
    public static long rebuildNanos;

    private Counters() {
    }

    /** Đặt mọi bộ đếm về 0 trước một lượt chạy. */
    public static void reset() {
        probeCount = 0L;
        rebuildCount = 0L;
        rebuildNanos = 0L;
    }

    /** Cộng số lần thăm dò khi chế độ đếm được bật. */
    public static void addProbes(long count) {
        if (ON) {
            probeCount += count;
        }
    }
}
