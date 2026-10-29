package hashbench;

import java.util.HashMap;
import java.util.Map;

/** Bang gia lap giao dien de kiem tra runner; STUB, tuyet doi khong dung de do chinh thuc. */
public class StubHashIndex implements HashIndex {
    private final Map<String, Integer> values;
    private final int tableCapacity;

    /** Tao bang gia co dung luong 2^p. */
    public StubHashIndex(int p) {
        if (p < 0 || p > 30) {
            throw new IllegalArgumentException("p phai nam trong khoang 0..30");
        }
        this.tableCapacity = 1 << p;
        this.values = new HashMap<String, Integer>();
    }

    /** Them khoa moi hoac cap nhat khoa dang co. */
    @Override
    public int put(String key, int value) {
        requireKey(key);
        if (value == NOT_FOUND) {
            throw new IllegalArgumentException("value khong duoc bang NOT_FOUND");
        }
        if (values.containsKey(key)) {
            values.put(key, Integer.valueOf(value));
            return UPDATED;
        }
        if (values.size() >= tableCapacity) {
            return FULL;
        }
        values.put(key, Integer.valueOf(value));
        return INSERTED;
    }

    /** Tim gia tri theo khoa. */
    @Override
    public int get(String key) {
        requireKey(key);
        Integer value = values.get(key);
        return value == null ? NOT_FOUND : value.intValue();
    }

    /** Xoa khoa neu khoa dang ton tai. */
    @Override
    public boolean remove(String key) {
        requireKey(key);
        return values.remove(key) != null;
    }

    private static void requireKey(String key) {
        if (key == null) {
            throw new IllegalArgumentException("key khong duoc null");
        }
    }

    /** Tra ve so khoa dang luu. */
    @Override
    public int size() {
        return values.size();
    }

    /** Tra ve dung luong gia lap cua bang. */
    @Override
    public int capacity() {
        return tableCapacity;
    }
}
