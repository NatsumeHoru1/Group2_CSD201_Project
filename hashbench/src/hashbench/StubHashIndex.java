package hashbench;

import java.util.HashMap;
import java.util.Map;

/** Bảng giả lập giao diện để kiểm tra runner; STUB, tuyệt đối không dùng để đo chính thức. */
public class StubHashIndex implements HashIndex {
    private final Map<String, Integer> values;
    private final int tableCapacity;

    /** Tạo bảng giả có dung lượng 2^p. */
    public StubHashIndex(int p) {
        if (p < 0 || p > 30) {
            throw new IllegalArgumentException("p phải nằm trong khoảng 0..30");
        }
        this.tableCapacity = 1 << p;
        this.values = new HashMap<String, Integer>();
    }

    /** Thêm khóa mới hoặc cập nhật khóa đang có. */
    @Override
    public int put(String key, int value) {
        requireKey(key);
        if (value == NOT_FOUND) {
            throw new IllegalArgumentException("value không được bằng NOT_FOUND");
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

    /** Tìm giá trị theo khóa. */
    @Override
    public int get(String key) {
        requireKey(key);
        Integer value = values.get(key);
        return value == null ? NOT_FOUND : value.intValue();
    }

    /** Xóa khóa nếu khóa đang tồn tại. */
    @Override
    public boolean remove(String key) {
        requireKey(key);
        return values.remove(key) != null;
    }

    private static void requireKey(String key) {
        if (key == null) {
            throw new IllegalArgumentException("key không được null");
        }
    }

    /** Trả về số khóa đang lưu. */
    @Override
    public int size() {
        return values.size();
    }

    /** Trả về dung lượng giả lập của bảng. */
    @Override
    public int capacity() {
        return tableCapacity;
    }
}
