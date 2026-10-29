package hashbench.workload;

import hashbench.HashIndex;

/**
 * Bang doi chung: dung bang moi sach chi voi dung tap khoa song tai moc C.
 * Khong phu thuoc Registry hay hashbench.test.
 */
public final class ControlTable {
    private ControlTable() {}

    /**
     * Nap liveKeys() cua workload vao bang trong theo thu tu.
     * Nem IllegalStateException neu bang bao FULL.
     */
    public static void fill(HashIndex emptyTable, Workload w) {
        for (String k : w.liveKeys()) {
            if (emptyTable.put(k, Workload.valueOf(k)) == HashIndex.FULL) {
                throw new IllegalStateException("bang doi chung bi FULL khi nap khoa: " + k);
            }
        }
    }
}
