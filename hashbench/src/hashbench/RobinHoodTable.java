package hashbench;

/** Bang bam Robin Hood. earlyStop=false la bien the rh-noearly. */
public class RobinHoodTable implements HashIndex, HashStats {
    private final int p;
    private final int capacity;
    private final int mask;
    private final String[] keys;
    private final int[] vals;
    private final int[] dib;          // khoang cach do cua khoa trong moi o
    private final boolean earlyStop;
    private int size;

    public RobinHoodTable(int p) {
        this(p, true);
    }

    public RobinHoodTable(int p, boolean earlyStop) {
        if (p < 1 || p > 30) {
            throw new IllegalArgumentException("p phai nam trong khoang 1..30");
        }
        this.p = p;
        this.capacity = 1 << p;
        this.mask = capacity - 1;
        this.keys = new String[capacity];
        this.vals = new int[capacity];
        this.dib = new int[capacity];
        this.earlyStop = earlyStop;
    }

    /** O nha cua khoa, dung thu vien Hashing do TV2 cung cap. */
    private int home(String key) {
        return Hashing.hash(key, p);
    }

    @Override
    public int get(String key) {
        requireKey(key);
        int idx = home(key);
        int d = 0;
        int result = NOT_FOUND;
        for (; d < capacity; d++) {
            if (keys[idx] == null) {
                break;
            }
            if (key.equals(keys[idx])) {
                result = vals[idx];
                break;
            }
            if (earlyStop && dib[idx] < d) {
                break;
            }
            idx = (idx + 1) & mask;
        }
        Counters.addProbes(Math.min(d + 1, capacity));
        return result;
    }

    /** Tra ve chi so o chua khoa, hoac -1 neu khong co. */
    private int find(String key, int home) {
        int idx = home;
        for (int d = 0; d < capacity; d++) {
            if (keys[idx] == null || dib[idx] < d) {
                return -1;
            }
            if (key.equals(keys[idx])) {
                return idx;
            }
            idx = (idx + 1) & mask;
        }
        return -1;
    }

    @Override
    public int put(String key, int value) {
        requireKey(key);
        if (value == NOT_FOUND) {
            throw new IllegalArgumentException("value khong duoc bang NOT_FOUND");
        }
        int home = home(key);

        if (size == capacity) {
            int at = find(key, home);
            if (at >= 0) {
                vals[at] = value;
                return UPDATED;
            }
            return FULL;
        }

        String currentKey = key;
        int currentValue = value;
        int currentDib = 0;
        int idx = home;
        while (true) {
            if (keys[idx] == null) {
                keys[idx] = currentKey;
                vals[idx] = currentValue;
                dib[idx] = currentDib;
                size++;
                return INSERTED;
            }
            if (currentKey.equals(keys[idx])) {
                vals[idx] = currentValue;
                return UPDATED;
            }
            if (dib[idx] < currentDib) {
                String displacedKey = keys[idx];
                int displacedValue = vals[idx];
                int displacedDib = dib[idx];
                keys[idx] = currentKey;
                vals[idx] = currentValue;
                dib[idx] = currentDib;
                currentKey = displacedKey;
                currentValue = displacedValue;
                currentDib = displacedDib;
            }
            idx = (idx + 1) & mask;
            currentDib++;
        }
    }

    @Override
    public boolean remove(String key) {
        requireKey(key);
        int i = find(key, home(key));
        if (i < 0) {
            return false;
        }
        int next = (i + 1) & mask;
        while (keys[next] != null && dib[next] > 0) {
            keys[i] = keys[next];
            vals[i] = vals[next];
            dib[i] = dib[next] - 1;
            i = next;
            next = (next + 1) & mask;
        }
        keys[i] = null;
        vals[i] = 0;
        dib[i] = 0;
        size--;
        return true;
    }

    private static void requireKey(String key) {
        if (key == null) {
            throw new IllegalArgumentException("key khong duoc null");
        }
    }

    @Override public int size() { return size; }
    @Override public int capacity() { return capacity; }

    @Override public int tombCount() { return 0; }
    @Override public int emptyCount() { return capacity - size; }
    @Override public double dibMean() {
        if (size == 0) return 0.0;
        long sum = 0;
        for (int i = 0; i < capacity; i++) {
            if (keys[i] != null) sum += dib[i];
        }
        return (double) sum / size;
    }

    @Override public int dibMax() {
        int max = 0;
        for (int i = 0; i < capacity; i++) {
            if (keys[i] != null && dib[i] > max) max = dib[i];
        }
        return max;
    }
    @Override public long rebuildCount() { return 0L; }
    @Override public long rebuildNanos() { return 0L; }
    @Override public long probeCount() { return Counters.probeCount; }

    /** Kiem tra bat bien; nem IllegalStateException neu sai. */
    public void validate() {
        int count = 0;
        for (int i = 0; i < capacity; i++) {
            if (keys[i] == null) continue;
            count++;
            int expected = (i - home(keys[i])) & mask;
            if (dib[i] != expected) {
                throw new IllegalStateException("dib sai o o " + i);
            }
            int prev = (i - 1) & mask;
            if (keys[prev] == null) {
                if (dib[i] != 0) {
                    throw new IllegalStateException("dib phai bang 0 sau o rong, o o " + i);
                }
            } else if (dib[i] > dib[prev] + 1) {
                throw new IllegalStateException("vi pham bat bien Robin Hood o o " + i);
            }
            if (find(keys[i], home(keys[i])) != i) {
                throw new IllegalStateException("khong tim lai duoc khoa o o " + i);
            }
        }
        if (count != size) {
            throw new IllegalStateException("size khong khop so o co khoa");
        }
    }

    /** In trang thai bang de duot, khong dung trong vung do. */
    public String dump() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < capacity; i++) {
            sb.append(i).append(": ");
            if (keys[i] == null) {
                sb.append("-");
            } else {
                sb.append("key=").append(keys[i]).append(" dib=").append(dib[i]);
            }
            sb.append('\n');
        }
        return sb.toString();
    }
}