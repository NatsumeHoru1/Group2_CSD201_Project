package hashbench;

/** Bảng băm Robin Hood (khung: chưa cài thuật toán). */
public class RobinHoodTable implements HashIndex, HashStats {
    private static final long EMPTY = -1L; // an toàn vì khóa luôn >= 0

    private final int p;
    private final int capacity;
    private final int mask;
    private final long[] keys;
    private final int[] vals;
    private int size;

    public RobinHoodTable(int p) {
        if (p < 1 || p > 30) {
            throw new IllegalArgumentException("p phải nằm trong khoảng 1..30");
        }
        this.p = p;
        this.capacity = 1 << p;
        this.mask = capacity - 1;
        this.keys = new long[capacity];
        this.vals = new int[capacity];
        java.util.Arrays.fill(keys, EMPTY);
    }

    /** Ô nhà của khóa: hàm băm Fibonacci. TV2 sẽ có lớp Hashing, khi đó thay thân hàm này. */
    private int home(long key) {
        return (int) ((key * 0x9E3779B97F4A7C15L) >>> (64 - p));
    }

    @Override
    public int put(long key, int value) {
        if (value == NOT_FOUND) {
            throw new IllegalArgumentException("value không được bằng NOT_FOUND");
        }
        throw new UnsupportedOperationException("TODO put");
    }

    @Override
    public int get(long key) {
        throw new UnsupportedOperationException("TODO get");
    }

    @Override
    public boolean remove(long key) {
        throw new UnsupportedOperationException("TODO remove");
    }

    @Override public int size() { return size; }
    @Override public int capacity() { return capacity; }

    // HashStats
    @Override public int tombCount() { return 0; }              // Robin Hood không có tombstone
    @Override public int emptyCount() { return capacity - size; }
    @Override public double dibMean() { return 0.0; }           // TODO
    @Override public int dibMax() { return 0; }                 // TODO
    @Override public long rebuildCount() { return 0L; }
    @Override public long rebuildNanos() { return 0L; }
    @Override public long probeCount() { return 0L; }           // TODO: nối với Counters

    /** Kiểm tra bất biến; ném IllegalStateException nếu sai. */
    public void validate() {
        // TODO
    }
}