package hashbench.workload;

import hashbench.HashIndex;
import hashbench.test.Registry;

/**
 * Bảng đối chứng: dựng một bảng mới sạch chỉ với đúng tập khóa sống tại mốc C.
 */
public final class ControlTable {
    private ControlTable() {}

    public static HashIndex build(Registry.Factory f, int p, Workload w) {
        HashIndex t = f.create(p);
        for (long k : w.liveKeys) {
            if (t.put(k, Workload.valueOf(k)) == HashIndex.FULL) {
                throw new IllegalStateException("bảng đối chứng bị FULL");
            }
        }
        return t;
    }
}
