package hashbench;

import java.util.Arrays;

/** Bang bam do tuyen tinh dung trang thai EMPTY, FULL va TOMB. */
public class LinearProbingTombTable implements HashIndex, HashStats {
    private static final byte EMPTY = 0;
    private static final byte STATE_FULL = 1;
    private static final byte TOMB = 2;

    private final int p;
    private final int capacity;
    private final int mask;
    private final String[] keys;
    private final int[] vals;
    private final byte[] state;
    private final double tau;
    private int size;
    private int tombs;
    private long rebuildCount;
    private long rebuildNanos;

    public LinearProbingTombTable(int p) {
        this(p, Double.POSITIVE_INFINITY);
    }

    public LinearProbingTombTable(int p, double tau) {
        if (p < 1 || p > 30) {
            throw new IllegalArgumentException("p phai nam trong khoang 1..30");
        }
        if (Double.isNaN(tau) || tau < 0.0) {
            throw new IllegalArgumentException("tau phai >= 0 (hoac vo han)");
        }
        this.p = p;
        this.capacity = 1 << p;
        this.mask = capacity - 1;
        this.keys = new String[capacity];
        this.vals = new int[capacity];
        this.state = new byte[capacity];
        this.tau = tau;
    }

    private int home(String key) {
        return Hashing.hash(key, p);
    }

    private static void requireKey(String key) {
        if (key == null) {
            throw new IllegalArgumentException("key khong duoc null");
        }
    }

    @Override
    public int get(String key) {
        requireKey(key);
        int idx = home(key);
        int d = 0;
        int result = NOT_FOUND;
        for (; d < capacity; d++) {
            if (state[idx] == EMPTY) {
                break;
            }
            if (state[idx] == STATE_FULL && key.equals(keys[idx])) {
                result = vals[idx];
                break;
            }
            idx = (idx + 1) & mask;
        }
        Counters.addProbes(Math.min(d + 1, capacity));
        return result;
    }

    /** Tra ve o chua khoa, hoac -1 neu khong tim thay. */
    private int find(String key, int home) {
        int idx = home;
        for (int d = 0; d < capacity; d++) {
            if (state[idx] == EMPTY) {
                return -1;
            }
            if (state[idx] == STATE_FULL && key.equals(keys[idx])) {
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
        int idx = home(key);
        int firstTomb = -1;
        for (int d = 0; d < capacity; d++) {
            if (state[idx] == STATE_FULL && key.equals(keys[idx])) {
                vals[idx] = value;
                return UPDATED;
            }
            if (state[idx] == EMPTY) {
                int target = firstTomb >= 0 ? firstTomb : idx;
                if (firstTomb >= 0) {
                    tombs--;
                }
                keys[target] = key;
                vals[target] = value;
                state[target] = STATE_FULL;
                size++;
                return INSERTED;
            }
            if (state[idx] == TOMB && firstTomb < 0) {
                firstTomb = idx;
            }
            idx = (idx + 1) & mask;
        }
        if (firstTomb >= 0) {
            keys[firstTomb] = key;
            vals[firstTomb] = value;
            state[firstTomb] = STATE_FULL;
            tombs--;
            size++;
            return INSERTED;
        }
        return FULL;
    }

    @Override
    public boolean remove(String key) {
        requireKey(key);
        int idx = home(key);
        int found = find(key, idx);
        if (found < 0) {
            return false;
        }
        keys[found] = null;
        vals[found] = 0;
        state[found] = TOMB;
        size--;
        tombs++;
        if ((double) tombs >= tau * capacity) {
            rebuild();
        }
        return true;
    }

    /** Xay dung lai bang, chi giu khoa song va xoa trang thai TOMB. */
    private void rebuild() {
        long start = System.nanoTime();
        int liveCount = size;
        String[] liveKeys = new String[liveCount];
        int[] liveVals = new int[liveCount];
        int count = 0;
        for (int i = 0; i < capacity; i++) {
            if (state[i] == STATE_FULL) {
                liveKeys[count] = keys[i];
                liveVals[count] = vals[i];
                count++;
            }
        }
        Arrays.fill(keys, null);
        Arrays.fill(vals, 0);
        Arrays.fill(state, EMPTY);
        for (int i = 0; i < liveCount; i++) {
            int idx = home(liveKeys[i]);
            while (state[idx] == STATE_FULL) {
                idx = (idx + 1) & mask;
            }
            keys[idx] = liveKeys[i];
            vals[idx] = liveVals[i];
            state[idx] = STATE_FULL;
        }
        tombs = 0;
        rebuildCount++;
        rebuildNanos += System.nanoTime() - start;
    }

    @Override public int size() { return size; }
    @Override public int capacity() { return capacity; }
    @Override public int tombCount() { return tombs; }
    @Override public int emptyCount() { return capacity - size - tombs; }
    @Override public double dibMean() { return 0.0; }
    @Override public int dibMax() { return 0; }
    @Override public long rebuildCount() { return rebuildCount; }
    @Override public long rebuildNanos() { return rebuildNanos; }
    @Override public long probeCount() { return Counters.probeCount; }

    /** Kiem tra bat bien cua cac trang thai o va chuoi do. */
    public void validate() {
        int live = 0;
        int tomb = 0;
        for (int i = 0; i < capacity; i++) {
            if (state[i] == TOMB) {
                if (keys[i] != null) {
                    throw new IllegalStateException("o TOMB khong duoc giu khoa");
                }
                tomb++;
                continue;
            }
            if (state[i] == EMPTY) {
                if (keys[i] != null) {
                    throw new IllegalStateException("o EMPTY khong duoc giu khoa");
                }
                continue;
            }
            if (state[i] != STATE_FULL || keys[i] == null) {
                throw new IllegalStateException("trang thai FULL khong hop le");
            }
            live++;
            int idx = home(keys[i]);
            while (idx != i) {
                if (state[idx] == EMPTY) {
                    throw new IllegalStateException("co o EMPTY giua o nha va o " + i);
                }
                idx = (idx + 1) & mask;
            }
            if (find(keys[i], home(keys[i])) != i) {
                throw new IllegalStateException(
                        "khoa bi trung hoac khong tim lai duoc o o " + i);
            }
        }
        if (live != size) {
            throw new IllegalStateException("size khong khop: " + size + " vs " + live);
        }
        if (tomb != tombs) {
            throw new IllegalStateException("tombs khong khop: " + tombs + " vs " + tomb);
        }
    }

    /** In trang thai bang de duyet tay, khong dung trong vung do. */
    public String dump() {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < capacity; i++) {
            result.append(i).append(": ");
            if (state[i] == EMPTY) {
                result.append("-");
            } else if (state[i] == TOMB) {
                result.append("TOMB");
            } else {
                result.append("key=").append(keys[i]);
            }
            result.append('\n');
        }
        return result.toString();
    }
}
