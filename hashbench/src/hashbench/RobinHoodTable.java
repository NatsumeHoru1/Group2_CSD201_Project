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
    int i = find(key);
    if (i < 0) return false;                       // khóa không có: dữ liệu không đổi
    int next = (i + 1) & mask;
    while (keys[next] != EMPTY && dib[next] > 0) { // 1: phần tử kế chưa nằm đúng nhà
        keys[i] = keys[next];
        vals[i] = vals[next];
        dib[i] = dib[next] - 1;                    // 2: giảm dib đi 1
        i = next;
        next = (next + 1) & mask;
    }
    keys[i] = EMPTY;                               // 3: ô cuối cùng bị bỏ trống
    vals[i] = 0;
    dib[i] = 0;
    size--;
    return true;
}

    @Override public int size() { return size; }
    @Override public int capacity() { return capacity; }

    // HashStats
    @Override public int tombCount() { return 0; }          // Robin Hood không có tombstone
    @Override public int emptyCount() { return capacity - size; }
@Override
public double dibMean() {
    if (size == 0) return 0.0;
    long sum = 0;
    for (int i = 0; i < capacity; i++) {
        if (keys[i] != EMPTY) sum += dib[i];
    }
    return (double) sum / size;
}

@Override
public int dibMax() {
    int max = 0;
    for (int i = 0; i < capacity; i++) {
        if (keys[i] != EMPTY && dib[i] > max) max = dib[i];
    }
    return max;
}
    @Override public long rebuildCount() { return 0L; }
    @Override public long rebuildNanos() { return 0L; }
    @Override public long probeCount() { return 0L; }       // TODO: nối với Counters

/** Kiểm tra bất biến; ném IllegalStateException nếu sai. */
public void validate() {
    int count = 0;
    for (int i = 0; i < capacity; i++) {
        if (keys[i] == EMPTY) continue;
        count++;
        // 1: dib phải bằng khoảng cách thật từ ô nhà đến ô i
        int expected = (i - home(keys[i])) & mask;
        if (dib[i] != expected) {
            throw new IllegalStateException("dib sai o o " + i);
        }
        // 2: bất biến Robin Hood với ô đứng trước
        int prev = (i - 1) & mask;
        if (keys[prev] == EMPTY) {
            if (dib[i] != 0) {                       // sau ô rỗng thì dib phải bằng bao nhiêu?
                throw new IllegalStateException("dib phai bang 0 sau o rong, o o " + i);
            }
        } else if (dib[i] > dib[prev] + 1) {                    // dib[i] không được vượt dib[prev] cộng mấy?
            throw new IllegalStateException("vi pham bat bien Robin Hood o o " + i);
        }
        // 3: get phải tìm lại được chính ô này
        if (find(keys[i]) != i) {
            throw new IllegalStateException("khong tim lai duoc khoa o o " + i);
        }
    }
    if (count != size) {
        throw new IllegalStateException("size khong khop so o co khoa");
    }
}

/** In trạng thái bảng để dượt, không dùng trong vùng đo. */
public String dump() {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < capacity; i++) {
        sb.append(i).append(": ");
        if (keys[i] == EMPTY) {
            sb.append("-");
        } else {
            sb.append("key=").append(keys[i]).append(" dib=").append(dib[i]);
        }
        sb.append('\n');
    }
    return sb.toString();
}

}