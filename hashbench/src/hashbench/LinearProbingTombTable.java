package hashbench;

import java.util.Arrays;

/**
 * Bang bam dia chi mo, do tuyen tinh (linear probing), xoa bang tombstone (lp-tomb).
 * Phu trach: TV3.
 *
 * Moi o co 3 trang thai, ma hoa ngay trong mang keys (khoa luon >= 0):
 *   EMPTY = -1  o chua tung dung      -> get/put dung lai khi gap
 *   TOMB  = -2  o da xoa (bia mo)     -> get di qua, put co the tai su dung
 *   >= 0        o dang chua khoa song
 *
 * tau = nguong rebuild theo ti le TOMB: sau moi remove, neu tombs > tau * M thi rebuild.
 * tau = Double.POSITIVE_INFINITY nghia la khong bao gio rebuild (RQ2).
 */
public class LinearProbingTombTable implements HashIndex, HashStats {
    private static final long EMPTY = -1L;
    private static final long TOMB = -2L;

    private final int p;
    private final int capacity;
    private final int mask;
    private final long[] keys;
    private final int[] vals;
    private final double tau;
    private int size;          // so khoa song
    private int tombs;         // so o TOMB
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
        this.keys = new long[capacity];
        this.vals = new int[capacity];
        this.tau = tau;
        Arrays.fill(keys, EMPTY);
    }

    private int home(long key) {
        return Hashing.hash(key, p);
    }

    @Override
    public int get(long key) {
        if (key < 0) {
            return NOT_FOUND;
        }
        int idx = home(key);
        int d = 0;
        int result = NOT_FOUND;
        for (; d < capacity; d++) {
            long k = keys[idx];
            if (k == EMPTY) break;                 // gap o chua tung dung: khoa khong co
            if (k == key) { result = vals[idx]; break; }
            idx = (idx + 1) & mask;                // TOMB hoac khoa khac: di tiep
        }
        Counters.addProbes(Math.min(d + 1, capacity));
        return result;
    }

    /** Tra ve chi so o chua khoa song, hoac -1. Do toi da M o. */
    private int find(long key) {
        if (key < 0) {
            return -1;
        }
        int idx = home(key);
        for (int d = 0; d < capacity; d++) {
            long k = keys[idx];
            if (k == EMPTY) return -1;
            if (k == key) return idx;
            idx = (idx + 1) & mask;
        }
        return -1;
    }

    @Override
    public int put(long key, int value) {
        if (value == NOT_FOUND) {
            throw new IllegalArgumentException("value khong duoc bang NOT_FOUND");
        }
        if (key < 0) {
            throw new IllegalArgumentException("key phai >= 0");
        }
        int idx = home(key);
        int firstTomb = -1;
        for (int d = 0; d < capacity; d++) {
            long k = keys[idx];
            if (k == key) {                        // khoa da co: cap nhat tai cho
                vals[idx] = value;
                return UPDATED;
            }
            if (k == EMPTY) {                      // chac chan khoa chua co
                int target = firstTomb >= 0 ? firstTomb : idx;
                if (firstTomb >= 0) {
                    tombs--;
                }
                keys[target] = key;
                vals[target] = value;
                size++;
                return INSERTED;
            }
            if (k == TOMB && firstTomb < 0) {
                firstTomb = idx;                   // nho TOMB dau tien, nhung van phai do tiep
            }
            idx = (idx + 1) & mask;
        }
        // Da xem du M o, khong gap EMPTY va khong thay khoa
        if (firstTomb >= 0) {
            keys[firstTomb] = key;
            vals[firstTomb] = value;
            tombs--;
            size++;
            return INSERTED;
        }
        return FULL;
    }

    @Override
    public boolean remove(long key) {
        int i = find(key);
        if (i < 0) {
            return false;
        }
        keys[i] = TOMB;
        vals[i] = 0;
        size--;
        tombs++;
        if (tombs > tau * capacity) {
            rebuild();
        }
        return true;
    }

    /** Dung lai bang: chi giu khoa song, xoa het TOMB. Do thoi gian vao rebuildNanos. */
    private void rebuild() {
        long start = System.nanoTime();
        int n = size;
        long[] liveKeys = new long[n];
        int[] liveVals = new int[n];
        int c = 0;
        for (int i = 0; i < capacity; i++) {
            if (keys[i] >= 0) {
                liveKeys[c] = keys[i];
                liveVals[c] = vals[i];
                c++;
            }
        }
        Arrays.fill(keys, EMPTY);
        Arrays.fill(vals, 0);
        for (int j = 0; j < n; j++) {
            int idx = home(liveKeys[j]);
            while (keys[idx] != EMPTY) {
                idx = (idx + 1) & mask;
            }
            keys[idx] = liveKeys[j];
            vals[idx] = liveVals[j];
        }
        tombs = 0;
        rebuildCount++;
        rebuildNanos += System.nanoTime() - start;
    }

    @Override public int size() { return size; }
    @Override public int capacity() { return capacity; }

    // HashStats
    @Override public int tombCount() { return tombs; }
    @Override public int emptyCount() { return capacity - size - tombs; }
    @Override public double dibMean() { return 0.0; }   // khong ap dung cho lp-tomb
    @Override public int dibMax() { return 0; }          // khong ap dung cho lp-tomb
    @Override public long rebuildCount() { return rebuildCount; }
    @Override public long rebuildNanos() { return rebuildNanos; }
    @Override public long probeCount() { return Counters.probeCount; }

    /** Kiem tra bat bien; nem IllegalStateException neu sai. Khong dung trong vung do. */
    public void validate() {
        int live = 0;
        int tomb = 0;
        for (int i = 0; i < capacity; i++) {
            long k = keys[i];
            if (k == TOMB) { tomb++; continue; }
            if (k == EMPTY) continue;
            live++;
            // tu o nha den o i khong duoc co o EMPTY (neu co thi get se khong tim thay)
            int j = home(k);
            while (j != i) {
                if (keys[j] == EMPTY) {
                    throw new IllegalStateException("co o EMPTY giua o nha va o " + i);
                }
                j = (j + 1) & mask;
            }
            if (find(k) != i) {
                throw new IllegalStateException("khoa bi trung hoac khong tim lai duoc o o " + i);
            }
        }
        if (live != size) throw new IllegalStateException("size khong khop: " + size + " vs " + live);
        if (tomb != tombs) throw new IllegalStateException("tombs khong khop: " + tombs + " vs " + tomb);
    }

    /** In trang thai bang de duyet tay, khong dung trong vung do. */
    public String dump() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < capacity; i++) {
            sb.append(i).append(": ");
            if (keys[i] == EMPTY) sb.append("-");
            else if (keys[i] == TOMB) sb.append("TOMB");
            else sb.append("key=").append(keys[i]);
            sb.append('\n');
        }
        return sb.toString();
    }
}