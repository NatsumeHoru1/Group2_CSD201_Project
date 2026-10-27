package hashbench.workload;

import hashbench.HashIndex;

/**
 * Bảng đối chứng: dựng bảng mới sạch chỉ với đúng tập khóa sống tại mốc C.
 * Không phụ thuộc Registry hay hashbench.test.
 */
public final class ControlTable {
    private ControlTable() {}

    /**
     * Nạp liveKeys() của workload vào bảng trống theo thứ tự.
     * Ném IllegalStateException nếu bảng báo FULL.
     */
    public static void fill(HashIndex emptyTable, Workload w) {
        for (String k : w.liveKeys()) {
            if (emptyTable.put(k, Workload.valueOf(k)) == HashIndex.FULL) {
                throw new IllegalStateException("bảng đối chứng bị FULL khi nạp khóa: " + k);
            }
        }
    }
}
