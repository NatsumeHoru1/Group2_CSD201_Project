package hashbench.workload;

import hashbench.HashIndex;
import hashbench.test.Registry;

/** Bảng đối chứng RQ2: dựng MỚI cùng thuật toán từ đúng tập khóa đang sống (không còn TOMB, không lịch sử xóa). */
public final class ControlTable {
    private ControlTable() {}

    public static HashIndex build(Registry.Factory f, int p, Workload w) {
        HashIndex t = f.create(p);
        for (int i = 0; i < w.finalLive.length; i++) {
            long k = w.finalLive[i];
            if (t.put(k, Workload.valueOf(k)) == HashIndex.FULL) throw new IllegalStateException("bảng đối chứng bị FULL");
        }
        return t;
    }
}
