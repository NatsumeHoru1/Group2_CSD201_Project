package hashbench;

/** Cung cap cac thong ke noi bo cua mot bang bam. */
public interface HashStats {
    /** Tra ve so o tombstone. */
    int tombCount();

    /** Tra ve so o trong. */
    int emptyCount();

    /** Tra ve khoang cach tim kiem trung binh. */
    double dibMean();

    /** Tra ve khoang cach tim kiem lon nhat. */
    int dibMax();

    /** Tra ve so lan xay dung lai bang. */
    long rebuildCount();

    /** Tra ve tong thoi gian xay dung lai, tinh bang nano giay. */
    long rebuildNanos();

    /** Tra ve tong so lan tham do. */
    long probeCount();
}
