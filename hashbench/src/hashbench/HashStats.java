package hashbench;

/** Cung cấp các thống kê nội bộ của một bảng băm. */
public interface HashStats {
    /** Trả về số ô tombstone. */
    int tombCount();

    /** Trả về số ô trống. */
    int emptyCount();

    /** Trả về khoảng cách tìm kiếm trung bình. */
    double dibMean();

    /** Trả về khoảng cách tìm kiếm lớn nhất. */
    int dibMax();

    /** Trả về số lần xây dựng lại bảng. */
    long rebuildCount();

    /** Trả về tổng thời gian xây dựng lại, tính bằng nano giây. */
    long rebuildNanos();

    /** Trả về tổng số lần thăm dò. */
    long probeCount();
}
