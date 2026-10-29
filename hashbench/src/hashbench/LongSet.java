package hashbench;

import java.util.Arrays;

/**
 * Tap hop chua khoa long primitive khong gay boxing / GC overhead.
 * Dung de loai tru trung lap khi sinh du lieu va kiem tra khoa vang mat.
 * Phu trach: TV2
 */
public final class LongSet {
    private static final long EMPTY = -1L;
    private long[] table;
    private int mask;
    private int size;
    private int threshold;

    public LongSet(int expectedElements) {
        int cap = 16;
        while (cap < (expectedElements * 2)) {
            cap <<= 1;
            if (cap <= 0) { // Tranh tran so int
                cap = 1 << 30;
                break;
            }
        }
        this.table = new long[cap];
        Arrays.fill(this.table, EMPTY);
        this.mask = cap - 1;
        this.threshold = (int) (cap * 0.70f);
        this.size = 0;
    }

    /**
     * Them khoa vao tap hop.
     * @param key khoa long >= 0
     * @return true neu la khoa moi, false neu da ton tai
     */
    public boolean add(long key) {
        if (key < 0) {
            throw new IllegalArgumentException("LongSet chi ho tro key >= 0");
        }
        if (size >= threshold) {
            rehash();
        }
        int idx = (int) ((key * 0x9E3779B97F4A7C15L) >>> 32) & mask;
        while (table[idx] != EMPTY) {
            if (table[idx] == key) {
                return false; // Da ton tai
            }
            idx = (idx + 1) & mask;
        }
        table[idx] = key;
        size++;
        return true;
    }

    /**
     * Kiem tra khoa co nam trong tap hop hay khong.
     */
    public boolean contains(long key) {
        if (key < 0) return false;
        int idx = (int) ((key * 0x9E3779B97F4A7C15L) >>> 32) & mask;
        while (table[idx] != EMPTY) {
            if (table[idx] == key) {
                return true;
            }
            idx = (idx + 1) & mask;
        }
        return false;
    }

    public int size() {
        return size;
    }

    private void rehash() {
        int newCap = table.length << 1;
        if (newCap <= 0) {
            return; // Da dat dung luong toi da
        }
        long[] oldTable = table;
        table = new long[newCap];
        Arrays.fill(table, EMPTY);
        mask = newCap - 1;
        threshold = (int) (newCap * 0.70f);
        size = 0;

        for (int i = 0; i < oldTable.length; i++) {
            long k = oldTable[i];
            if (k != EMPTY) {
                add(k);
            }
        }
    }

    /**
     * Xuat mang toan bo cac khoa dang co trong tap hop.
     */
    public long[] toArray() {
        long[] result = new long[size];
        int ptr = 0;
        for (int i = 0; i < table.length; i++) {
            if (table[i] != EMPTY) {
                result[ptr++] = table[i];
            }
        }
        return result;
    }
}
