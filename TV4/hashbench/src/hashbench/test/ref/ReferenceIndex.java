package hashbench.test.ref;

import hashbench.HashIndex;
import java.util.HashMap;

/** Cài đặt tham chiếu (HashMap) chỉ để tự kiểm tra chính harness (--self). KHÔNG dùng để đo. */
public class ReferenceIndex implements HashIndex {
    protected final HashMap<Long, Integer> map = new HashMap<Long, Integer>();
    protected final int m;

    public ReferenceIndex(int m) { this.m = m; }

    public int put(long key, int value) {
        if (value == NOT_FOUND) throw new IllegalArgumentException("value == NOT_FOUND");
        if (map.containsKey(key)) { map.put(key, value); return UPDATED; }
        if (map.size() >= m) return FULL;
        map.put(key, value);
        return INSERTED;
    }
    public int get(long key) { Integer v = map.get(key); return v == null ? NOT_FOUND : v; }
    public boolean remove(long key) { return map.remove(key) != null; }
    public int size() { return map.size(); }
    public int capacity() { return m; }
    public void validate() { if (map.size() > m) throw new IllegalStateException("size > M"); }
}
