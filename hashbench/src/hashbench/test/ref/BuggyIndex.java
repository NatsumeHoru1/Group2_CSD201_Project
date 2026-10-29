package hashbench.test.ref;

import hashbench.HashIndex;
import java.util.HashMap;
import java.util.Map;

/**
 * Ban dot bien thu nghiem: moi che do lam mot loi nho de harness phai bat duoc.
 */
public class BuggyIndex implements HashIndex {
    public enum Mode {
        WRONG_FULL,
        WRONG_GET,
        WRONG_REMOVE,
        WRONG_UPDATE,
        WRONG_SIZE
    }

    private final int capacity;
    private final Map<String, Integer> map = new HashMap<String, Integer>();
    private final Mode mode;

    public BuggyIndex(int capacity, Mode mode) {
        this.capacity = capacity;
        this.mode = mode;
    }

    @Override
    public int put(String key, int value) {
        requireKey(key);
        if (value == NOT_FOUND) throw new IllegalArgumentException("value khong duoc bang NOT_FOUND");
        if (map.containsKey(key)) {
            map.put(key, value);
            return mode == Mode.WRONG_UPDATE ? INSERTED : UPDATED;
        }
        if (map.size() >= capacity) {
            return mode == Mode.WRONG_FULL ? INSERTED : FULL;
        }
        map.put(key, value);
        return INSERTED;
    }

    @Override
    public int get(String key) {
        requireKey(key);
        Integer v = map.get(key);
        if (mode == Mode.WRONG_GET && !map.containsKey(key)) return 0;
        return v == null ? NOT_FOUND : v.intValue();
    }

    @Override
    public boolean remove(String key) {
        requireKey(key);
        if (mode == Mode.WRONG_REMOVE && map.containsKey(key)) {
            return true;
        }
        return map.remove(key) != null;
    }

    @Override
    public int size() {
        if (mode == Mode.WRONG_SIZE) return map.size() + 1;
        return map.size();
    }

    @Override
    public int capacity() {
        return capacity;
    }

    private static void requireKey(String key) {
        if (key == null) throw new IllegalArgumentException("key khong duoc null");
    }
}
