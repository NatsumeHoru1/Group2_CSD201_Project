package hashbench;

/**
 * Bảng băm dò mở tuyến tính (linear probing) với cơ chế xóa dịch lùi cụm
 * (backward-shift deletion) theo Algorithm R của Knuth (TAOCP Vol. 3, mục 6.4).
 *
 * Khác biệt so với lp-tomb: không dùng tombstone nên số ô EMPTY không bị giảm
 * âm thầm, mỗi lần xóa đều dịch các khóa phía sau về trước một ô.
 *
 * Cơ chế đã dùng: Algorithm R (Knuth). Tên cơ chế được ghi vào cột params của
 * CSV dưới dạng "backshift=knuth-r" (xem CsvWriter.writePhaseRow).
 *
 * Phụ trách: TV6.
 */
public class LinearProbingBackshiftTable implements HashIndex, HashStats {
    /** Tên cơ chế xóa dịch lùi, ghi vào cột params của CSV. */
    public static final String BACKSHIFT_METHOD = "knuth-r";

    private static final long EMPTY = -1L; // an toàn vì khóa luôn >= 0

    private final int p;
    private final int capacity;
    private final int mask;
    private final long[] keys;
    private final int[] vals;
    private int size;

    /** Tạo bảng rỗng với dung lượng M = 2^p. */
    public LinearProbingBackshiftTable(int p) {
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

    /** Ô nhà của khóa, dùng thư viện Hashing do TV2 cung cấp. */
    private int home(long key) {
        return Hashing.hash(key, p);
    }

    /**
     * Thêm hoặc cập nhật khóa bằng dò mở tuyến tính.
     * Dò tối đa M ô; nếu dò hết M ô mà không gặp ô trống và cũng không thấy khóa
     * thì trả FULL và dữ liệu không đổi.
     */
    @Override
    public int put(long key, int value) {
        if (value == NOT_FOUND) {
            throw new IllegalArgumentException("value không được bằng NOT_FOUND");
        }
        int idx = home(key);
        for (int step = 0; step < capacity; step++) {
            if (keys[idx] == EMPTY) { // ô trống: khóa chắc chắn chưa có trong bảng
                keys[idx] = key;
                vals[idx] = value;
                size++;
                return INSERTED;
            }
            if (keys[idx] == key) { // khóa đã có: cập nhật giá trị
                vals[idx] = value;
                return UPDATED;
            }
            idx = (idx + 1) & mask;
        }
        return FULL; // đã dò đủ M ô: bảng đầy và khóa chưa tồn tại
    }

    /**
     * Tìm khóa. Vì không có tombstone nên dò vòng cho tới ô EMPTY; số ô đã xem
     * (kể cả ô EMPTY cuối cùng) được đếm vào bộ đếm ở lượt --count.
     */
    @Override
    public int get(long key) {
        int idx = home(key);
        int viewed = 0;
        int result = NOT_FOUND;
        for (int step = 0; step < capacity; step++) {
            viewed++;
            if (keys[idx] == EMPTY) {
                break;
            }
            if (keys[idx] == key) {
                result = vals[idx];
                break;
            }
            idx = (idx + 1) & mask;
        }
        Counters.addProbes(viewed); // số ô đã xem, theo đơn vị của đề cho LP
        return result;
    }

    /**
     * Xóa khóa bằng Algorithm R của Knuth (dịch lùi cụm).
     *
     * Sau khi xóa, các khóa còn lại trong cụm được dịch về trước một ô sao cho
     * không còn ô trống nằm giữa cụm; nhờ vậy bảng không bao giờ sinh tombstone.
     */
    @Override
    public boolean remove(long key) {
        int target = find(key);
        if (target < 0) {
            return false; // khóa không có: dữ liệu không đổi
        }
        int hole = target;
        keys[hole] = EMPTY;
        vals[hole] = 0;
        size--;
        int idx = hole;
        while (true) {
            idx = (idx + 1) & mask;
            if (keys[idx] == EMPTY) {
                return true; // đã quét hết cụm: không còn khóa nào cần dịch
            }
            int h = home(keys[idx]);
            // Khóa ở ô idx được dời về ô hole khi hole nằm trên đường dò
            // từ ô nhà h tới ô idx, tức ((hole - h) & mask) < ((idx - h) & mask).
            if (((hole - h) & mask) < ((idx - h) & mask)) {
                keys[hole] = keys[idx];
                vals[hole] = vals[idx];
                keys[idx] = EMPTY;
                vals[idx] = 0;
                hole = idx; // lỗ hổng chuyển sang ô vừa dọn, tiếp tục dò
            }
        }
    }

    /** Trả về chỉ số ô chứa khóa, hoặc -1 nếu không có. */
    private int find(long key) {
        int idx = home(key);
        for (int step = 0; step < capacity; step++) {
            if (keys[idx] == EMPTY) {
                return -1;
            }
            if (keys[idx] == key) {
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

    // ---- HashStats: các hàm quét bảng chỉ được gọi ngoài vùng đo ----

    /** Không dùng tombstone nên luôn bằng 0. */
    @Override
    public int tombCount() {
        return 0;
    }

    @Override
    public int emptyCount() {
        return capacity - size;
    }

    /** Khoảng cách dò trung bình, tính theo (cur - home) & mask. */
    @Override
    public double dibMean() {
        if (size == 0) {
            return 0.0;
        }
        long sum = 0L;
        for (int i = 0; i < capacity; i++) {
            if (keys[i] != EMPTY) {
                sum += (i - home(keys[i])) & mask;
            }
        }
        return (double) sum / size;
    }

    /** Khoảng cách dò lớn nhất, tính theo (cur - home) & mask. */
    @Override
    public int dibMax() {
        int max = 0;
        for (int i = 0; i < capacity; i++) {
            if (keys[i] != EMPTY) {
                int dib = (i - home(keys[i])) & mask;
                if (dib > max) {
                    max = dib;
                }
            }
        }
        return max;
    }

    /** Bảng này không có cơ chế rebuild nên hai giá trị này luôn bằng 0. */
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

    /** Kiểm tra bất biến; ném IllegalStateException nếu sai. */
    public void validate() {
        int count = 0;
        for (int i = 0; i < capacity; i++) {
            if (keys[i] == EMPTY) {
                continue;
            }
            count++;
            if (keys[i] < 0L) {
                throw new IllegalStateException("khóa âm tại ô " + i);
            }
            // 1: mọi ô nằm giữa ô nhà và ô i phải còn khóa (cụm phải liên tục)
            int idx = home(keys[i]);
            while (idx != i) {
                if (keys[idx] == EMPTY) {
                    throw new IllegalStateException("cụm không liên tục tại ô " + i);
                }
                idx = (idx + 1) & mask;
            }
            // 2: khóa phải tìm lại được đúng ô đang chứa nó
            if (find(keys[i]) != i) {
                throw new IllegalStateException("không tìm lại được khóa ở ô " + i);
            }
        }
        if (count != size) {
            throw new IllegalStateException("size không khớp số ô có khóa");
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
                sb.append("key=").append(keys[i])
                        .append(" dib=").append((i - home(keys[i])) & mask);
            }
            sb.append('\n');
        }
        return sb.toString();
    }
}
