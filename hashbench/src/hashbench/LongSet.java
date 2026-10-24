package hashbench;

import java.util.Arrays;

/**
 * Tập hợp chứa khóa long primitive không gây boxing / GC overhead.
 * Dùng để loại trừ trùng lặp khi sinh dữ liệu và kiểm tra khóa vắng mặt.
 * Phụ trách: TV2
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
            if (cap <= 0) { // Tránh tràn số int
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
     * Thêm khóa vào tập hợp.
     * @param key khóa long >= 0
     * @return true nếu là khóa mới, false nếu đã tồn tại
     */
    public boolean add(long key) {
        if (key < 0) {
            throw new IllegalArgumentException("LongSet chỉ hỗ trợ key >= 0");
        }
        if (size >= threshold) {
            rehash();
        }
        int idx = (int) ((key * 0x9E3779B97F4A7C15L) >>> 32) & mask;
        while (table[idx] != EMPTY) {
            if (table[idx] == key) {
                return false; // Đã tồn tại
            }
            idx = (idx + 1) & mask;
        }
        table[idx] = key;
        size++;
        return true;
    }

    /**
     * Kiểm tra khóa có nằm trong tập hợp hay không.
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
            return; // Đã đạt dung lượng tối đa
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
     * Xuất mảng toàn bộ các khóa đang có trong tập hợp.
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
