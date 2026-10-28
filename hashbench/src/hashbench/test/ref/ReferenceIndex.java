package hashbench.test.ref;

import hashbench.HashIndex;
import java.util.HashMap;
import java.util.Map;

/** Bản chuẩn đúng dùng cho self-check của harness. */
public class ReferenceIndex implements HashIndex {
    private final int capacity;
    private final Map<String, Integer> map = new HashMap<String, Integer>();

    public ReferenceIndex(int capacity) {
        if (capacity <= 0) throw new IllegalArgumentException("capacity phải > 0");
        this.capacity = capacity;
    }

    @Override
    public int put(String key, int value) {
        requireKey(key);
        if (value == NOT_FOUND) throw new IllegalArgumentException("value không được bằng NOT_FOUND");
        if (map.containsKey(key)) {
            map.put(key, value);
            return UPDATED;
        }
        if (map.size() >= capacity) return FULL;
        map.put(key, value);
        return INSERTED;
    }

    @Override
    public int get(String key) {
        requireKey(key);
        Integer v = map.get(key);
        return v == null ? NOT_FOUND : v.intValue();
    }

    @Override
    public boolean remove(String key) {
        requireKey(key);
        return map.remove(key) != null;
    }

    @Override
    public int size() {
        return map.size();
    }

    @Override
    public int capacity() {
        return capacity;
    }

    private static void requireKey(String key) {
        if (key == null) throw new IllegalArgumentException("key không được null");
    }
}
