package hashbench;

/** Bảng băm Robin Hood. earlyStop=false là biến thể rh-noearly. */
public class RobinHoodTable implements HashIndex, HashStats {
    private static final long EMPTY = -1L; // an toàn vì khóa luôn >= 0

    private final int p;
    private final int capacity;
    private final int mask;
    private final long[] keys;
    private final int[] vals;
    private final int[] dib;          // khoảng cách dò của khóa trong mỗi ô
    private final boolean earlyStop;
    private int size;

    public RobinHoodTable(int p) {
        this(p, true);
    }

    public RobinHoodTable(int p, boolean earlyStop) {
        if (p < 1 || p > 30) {
            throw new IllegalArgumentException("p phải nằm trong khoảng 1..30");
        }
        this.p = p;
        this.capacity = 1 << p;
        this.mask = capacity - 1;
        this.keys = new long[capacity];
        this.vals = new int[capacity];
        this.dib = new int[capacity];
        this.earlyStop = earlyStop;
        java.util.Arrays.fill(keys, EMPTY);
    }

    /** Ô nhà của khóa. TV2 sẽ có lớp Hashing, khi đó thay thân hàm này. */
    private int home(long key) {
        return (int) ((key * 0x9E3779B97F4A7C15L) >>> (64 - p));
    }


    @Override
    public int get(long key) {
        int idx = home(key);
        for (int d = 0; d < capacity; d++) {
            if (keys[idx] == EMPTY) return NOT_FOUND;               // 1: ô rỗng
            if (keys[idx] == key) return vals[idx];               // 2: đúng khóa
            if (earlyStop && dib[idx] < d) return NOT_FOUND;  // 3: so dib[idx] với d
            idx = (idx + 1) & mask;
        }
        return NOT_FOUND;
    }

/** Trả về chỉ số ô chứa khóa, hoặc -1 nếu không có. Luôn dừng sớm (đúng với mọi bảng hợp lệ). */
private int find(long key) {
    int idx = home(key);
    for (int d = 0; d < capacity; d++) {
        if (keys[idx] == EMPTY || dib[idx] < d) return -1;
        if (keys[idx] == key) return idx;
        idx = (idx + 1) & mask;
    }
    return -1;
}

@Override
public int put(long key, int value) {
    if (value == NOT_FOUND) {
        throw new IllegalArgumentException("value không được bằng NOT_FOUND");
    }

    if (size == capacity) {            // bảng đầy: chỉ được cập nhật, không được chèn
        int at = find(key);
        if (at >= 0) {
            vals[at] = value;
            return UPDATED;
        }
        return FULL;
    }

    long curKey = key;
    int curVal = value;
    int curDib = 0;
    int idx = home(key);
    while (true) {
        if (keys[idx] == EMPTY) {      // ô rỗng: đặt khóa đang cầm vào đây
            keys[idx] = curKey;
            vals[idx] = curVal;
            dib[idx] = curDib;
            size++;
            return INSERTED;
        }
        if (keys[idx] == curKey) {     // khóa gốc đã có: cập nhật
            vals[idx] = curVal;
            return UPDATED;
        }
        if (dib[idx] < curDib) {       // hoán đổi: khóa đang cầm vào ô, khóa cũ bị đẩy ra
            long tk = keys[idx];
            int tv = vals[idx];
            int td = dib[idx];
            keys[idx] = curKey;
            vals[idx] = curVal;
            dib[idx] = curDib;
            curKey = tk;
            curVal = tv;
            curDib = td;
        }
        idx = (idx + 1) & mask;
        curDib++;
    }
}

    @Override
    public boolean remove(long key) {
        throw new UnsupportedOperationException("TODO remove");
    }

    @Override public int size() { return size; }
    @Override public int capacity() { return capacity; }

    // HashStats
    @Override public int tombCount() { return 0; }          // Robin Hood không có tombstone
    @Override public int emptyCount() { return capacity - size; }
    @Override public double dibMean() { return 0.0; }       // TODO: đọc từ dib[]
    @Override public int dibMax() { return 0; }             // TODO: đọc từ dib[]
    @Override public long rebuildCount() { return 0L; }
    @Override public long rebuildNanos() { return 0L; }
    @Override public long probeCount() { return 0L; }       // TODO: nối với Counters

    /** Kiểm tra bất biến; ném IllegalStateException nếu sai. */
    public void validate() {
        // TODO
    }
}