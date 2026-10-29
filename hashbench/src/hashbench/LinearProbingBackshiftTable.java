package hashbench;

/**
 * Bang bam do mo tuyen tinh (linear probing) voi co che xoa dich lui cum
 * (backward-shift deletion) theo Algorithm R cua Knuth (TAOCP Vol. 3, muc 6.4).
 *
 * Khac biet so voi lp-tomb: khong dung tombstone nen so o EMPTY khong bi giam
 * am tham, moi lan xoa deu dich cac khoa phia sau ve truoc mot o.
 *
 * Co che da dung: Algorithm R (Knuth). Ten co che duoc ghi vao cot params cua
 * CSV duoi dang "backshift=knuth-r" (xem CsvWriter.writePhaseRow).
 *
 * Phu trach: TV6.
 */
public class LinearProbingBackshiftTable implements HashIndex, HashStats {
    /** Ten co che xoa dich lui, ghi vao cot params cua CSV. */
    public static final String BACKSHIFT_METHOD = "knuth-r";

    private final int p;
    private final int capacity;
    private final int mask;
    private final String[] keys;
    private final int[] vals;
    private int size;

    /** Tao bang rong voi dung luong M = 2^p. */
    public LinearProbingBackshiftTable(int p) {
        if (p < 1 || p > 30) {
            throw new IllegalArgumentException("p phai nam trong khoang 1..30");
        }
        this.p = p;
        this.capacity = 1 << p;
        this.mask = capacity - 1;
        this.keys = new String[capacity];
        this.vals = new int[capacity];
    }

    /** O nha cua khoa, dung thu vien Hashing do TV2 cung cap. */
    private int home(String key) {
        return Hashing.hash(key, p);
    }

    private static void requireKey(String key) {
        if (key == null) {
            throw new IllegalArgumentException("key khong duoc null");
        }
    }

    /**
     * Them hoac cap nhat khoa bang do mo tuyen tinh.
     * Do toi da M o; neu do het M o ma khong gap o trong va cung khong thay khoa
     * thi tra FULL va du lieu khong doi.
     */
    @Override
    public int put(String key, int value) {
        requireKey(key);
        if (value == NOT_FOUND) {
            throw new IllegalArgumentException("value khong duoc bang NOT_FOUND");
        }
        int idx = home(key);
        for (int step = 0; step < capacity; step++) {
            if (keys[idx] == null) { // o trong: khoa chac chan chua co trong bang
                keys[idx] = key;
                vals[idx] = value;
                size++;
                return INSERTED;
            }
            if (key.equals(keys[idx])) { // khoa da co: cap nhat gia tri
                vals[idx] = value;
                return UPDATED;
            }
            idx = (idx + 1) & mask;
        }
        return FULL; // da do du M o: bang day va khoa chua ton tai
    }

    /**
     * Tim khoa. Vi khong co tombstone nen do vong cho toi o EMPTY; so o da xem
     * (ke ca o EMPTY cuoi cung) duoc dem vao bo dem o luot --count.
     */
    @Override
    public int get(String key) {
        requireKey(key);
        int idx = home(key);
        int viewed = 0;
        int result = NOT_FOUND;
        for (int step = 0; step < capacity; step++) {
            viewed++;
            if (keys[idx] == null) {
                break;
            }
            if (key.equals(keys[idx])) {
                result = vals[idx];
                break;
            }
            idx = (idx + 1) & mask;
        }
        Counters.addProbes(viewed); // so o da xem, theo don vi cua de cho LP
        return result;
    }

    /**
     * Xoa khoa bang Algorithm R cua Knuth (dich lui cum).
     *
     * Sau khi xoa, cac khoa con lai trong cum duoc dich ve truoc mot o sao cho
     * khong con o trong nam giua cum; nho vay bang khong bao gio sinh tombstone.
     */
    @Override
    public boolean remove(String key) {
        requireKey(key);
        int home = home(key);
        int target = find(key, home);
        if (target < 0) {
            return false; // khoa khong co: du lieu khong doi
        }
        int hole = target;
        keys[hole] = null;
        vals[hole] = 0;
        size--;
        int idx = hole;
        while (true) {
            idx = (idx + 1) & mask;
            if (keys[idx] == null) {
                return true; // da quet het cum: khong con khoa nao can dich
            }
            int h = home(keys[idx]);
            // Khoa o o idx duoc doi ve o hole khi hole nam tren duong do
            // tu o nha h toi o idx, tuc ((hole - h) & mask) < ((idx - h) & mask).
            if (((hole - h) & mask) < ((idx - h) & mask)) {
                keys[hole] = keys[idx];
                vals[hole] = vals[idx];
                keys[idx] = null;
                vals[idx] = 0;
                hole = idx; // lo hong chuyen sang o vua don, tiep tuc do
            }
        }
    }

    /** Tra ve chi so o chua khoa, hoac -1 neu khong co. */
    private int find(String key, int home) {
        int idx = home;
        for (int step = 0; step < capacity; step++) {
            if (keys[idx] == null) {
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
    public int size() {
        return size;
    }

    @Override
    public int capacity() {
        return capacity;
    }

    // ---- HashStats: cac ham quet bang chi duoc goi ngoai vung do ----

    /** Khong dung tombstone nen luon bang 0. */
    @Override
    public int tombCount() {
        return 0;
    }

    @Override
    public int emptyCount() {
        return capacity - size;
    }

    /** Khoang cach do trung binh, tinh theo (cur - home) & mask. */
    @Override
    public double dibMean() {
        if (size == 0) {
            return 0.0;
        }
        long sum = 0L;
        for (int i = 0; i < capacity; i++) {
            if (keys[i] != null) {
                sum += (i - home(keys[i])) & mask;
            }
        }
        return (double) sum / size;
    }

    /** Khoang cach do lon nhat, tinh theo (cur - home) & mask. */
    @Override
    public int dibMax() {
        int max = 0;
        for (int i = 0; i < capacity; i++) {
            if (keys[i] != null) {
                int dib = (i - home(keys[i])) & mask;
                if (dib > max) {
                    max = dib;
                }
            }
        }
        return max;
    }

    /** Bang nay khong co co che rebuild nen hai gia tri nay luon bang 0. */
    @Override
    public long rebuildCount() {
        return 0L;
    }

    @Override
    public long rebuildNanos() {
        return 0L;
    }

    @Override
    public long probeCount() {
        return Counters.probeCount;
    }

    /** Kiem tra bat bien; nem IllegalStateException neu sai. */
    public void validate() {
        int count = 0;
        for (int i = 0; i < capacity; i++) {
            if (keys[i] == null) {
                continue;
            }
            count++;
            // 1: moi o nam giua o nha va o i phai con khoa (cum phai lien tuc)
            int idx = home(keys[i]);
            while (idx != i) {
                if (keys[idx] == null) {
                    throw new IllegalStateException("cum khong lien tuc tai o " + i);
                }
                idx = (idx + 1) & mask;
            }
            // 2: khoa phai tim lai duoc dung o dang chua no
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
                sb.append("key=").append(keys[i])
                        .append(" dib=").append((i - home(keys[i])) & mask);
            }
            sb.append('\n');
        }
        return sb.toString();
    }
}
