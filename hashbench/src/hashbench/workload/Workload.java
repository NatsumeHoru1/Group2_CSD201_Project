package hashbench.workload;

import hashbench.Generator;
import hashbench.HashIndex;
import hashbench.KeyCodec;
import java.util.SplittableRandom;

/**
 * Workload xóa-thêm tuần hoàn cho RQ2/RQ3.
 * Mỗi chu kỳ: xóa 1 khóa ngẫu nhiên đang sống → thêm 1 khóa mới.
 * Thiết kế O(1)/chu kỳ: dùng mảng chỉ số liveIdx thay vì HashSet.
 */
public final class Workload {

    // ------------------------------------------------------------------ //
    //  Kết quả execute                                                     //
    // ------------------------------------------------------------------ //
    public static final class Result {
        public final int  fullCount;
        public final long ops;
        public final int  blocks;

        public Result(int fullCount, long ops, int blocks) {
            this.fullCount = fullCount;
            this.ops       = ops;
            this.blocks    = blocks;
        }
    }

    // ------------------------------------------------------------------ //
    //  Trường công khai bất biến                                           //
    // ------------------------------------------------------------------ //
    public final int  p;
    public final int  n;          // N = round(load * 2^p), kẹp [1, 2^p]
    public final long steps;      // số chu kỳ C
    public final int  queries;    // số truy vấn mỗi loại

    // ------------------------------------------------------------------ //
    //  Dữ liệu nội bộ (tính một lần khi generate)                         //
    // ------------------------------------------------------------------ //
    private final String[] pool;        // dài N+steps: [0,N)=ban đầu, [N,N+steps)=thêm theo thứ tự
    private final int[]    victimIdx;   // victimIdx[i]: chỉ số trong pool bị xóa ở chu kỳ i
    private final int[]    liveIdxFinal;// liveIdx sau khi mô phỏng đủ steps chu kỳ (dài N)
    private final String[] presentQueriesArr; // queries object String MỚI từ tập sống
    private final String[] absentQueriesArr;  // queries khóa không bao giờ trong pool

    // ------------------------------------------------------------------ //
    //  Constructor riêng tư                                                //
    // ------------------------------------------------------------------ //
    private Workload(int p, int n, long steps, int queries,
                     String[] pool, int[] victimIdx, int[] liveIdxFinal,
                     String[] presentQueriesArr, String[] absentQueriesArr) {
        this.p                  = p;
        this.n                  = n;
        this.steps              = steps;
        this.queries            = queries;
        this.pool               = pool;
        this.victimIdx          = victimIdx;
        this.liveIdxFinal       = liveIdxFinal;
        this.presentQueriesArr  = presentQueriesArr;
        this.absentQueriesArr   = absentQueriesArr;
    }

    // ================================================================== //
    //  API công khai                                                       //
    // ================================================================== //

    /**
     * Sinh workload tất định theo seed.
     *
     * @param p       log2 dung lượng bảng, [1..30]
     * @param load    hệ số tải, (0,1]
     * @param steps   số chu kỳ C (ép int khi dùng mảng Java)
     * @param seed    hạt gieo ngẫu nhiên
     * @param queries số truy vấn mỗi loại (có mặt / vắng mặt)
     */
    public static Workload generate(int p, double load, long steps, long seed, int queries) {
        if (p < 1 || p > 30) throw new IllegalArgumentException("p phải trong [1, 30]");
        int cap = 1 << p;
        int n   = (int) Math.round(load * cap);
        if (n < 1) n = 1;
        if (n > cap) n = cap;

        // Kiểm tràn mảng Java (mảng tối đa Integer.MAX_VALUE phần tử)
        long totalPool = (long) n + steps;
        long totalIds  = totalPool + queries;
        if (steps > Integer.MAX_VALUE || queries < 0 ||
            totalPool > Integer.MAX_VALUE || totalIds > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("steps hoặc queries quá lớn cho mảng Java");
        }
        int stepsI   = (int) steps;
        int totalInt = (int) totalIds;

        // ── Bước 1: sinh toàn bộ ID không trùng một lần ─────────────── //
        long[] allIds = Generator.generatePresentIds(seed, totalInt);
        // pool[0..N+steps): các khóa ban đầu + thêm
        String[] pool = Generator.idsToKeys(java.util.Arrays.copyOfRange(allIds, 0, n + stepsI));
        // absentQueries: phần còn lại (không bao giờ nằm trong pool)
        String[] absentQ = Generator.idsToKeys(
                java.util.Arrays.copyOfRange(allIds, n + stepsI, totalInt));

        // ── Bước 2: mô phỏng nạn nhân (O(steps), không cần bảng) ────── //
        int[] liveIdx  = new int[n];
        for (int k = 0; k < n; k++) liveIdx[k] = k;  // liveIdx[k] = chỉ số trong pool

        int[] vicIdx = new int[stepsI];
        SplittableRandom rng = new SplittableRandom(seed ^ 0x57A11C0DEL);
        for (int i = 0; i < stepsI; i++) {
            int j = rng.nextInt(n);
            vicIdx[i]  = liveIdx[j];   // chỉ số trong pool bị xóa
            liveIdx[j] = n + i;        // thay bằng khóa mới (chỉ số trong pool)
        }
        // liveIdx giờ là tập sống SAU steps chu kỳ (dùng cho liveKeys & presentQueries)
        int[] liveIdxFinal = java.util.Arrays.copyOf(liveIdx, n);

        // ── Bước 3: presentQueries – object String MỚI từ tập sống ───── //
        SplittableRandom rng2 = new SplittableRandom(seed ^ 0x5DEECE66DL);
        String[] presentQ = new String[queries];
        for (int i = 0; i < queries; i++) {
            int idx = liveIdxFinal[rng2.nextInt(n)];  // chỉ số trong pool
            // tạo object String MỚI bằng encode(decode(pool[idx]))
            presentQ[i] = KeyCodec.encode(KeyCodec.decode(pool[idx]));
        }

        return new Workload(p, n, steps, queries,
                pool, vicIdx, liveIdxFinal, presentQ, absentQ);
    }

    // ------------------------------------------------------------------ //

    /** pool[0..N+steps): [0,N)=khóa ban đầu, [N,N+steps)=khóa thêm theo chu kỳ. */
    public String[] pool() { return pool; }

    /** queries object String MỚI – đều là khóa sống tại mốc C. */
    public String[] presentQueries() { return presentQueriesArr; }

    /** queries khóa không bao giờ nằm trong pool. */
    public String[] absentQueries() { return absentQueriesArr; }

    /**
     * N khóa sống tại mốc C (tham chiếu tới phần tử pool),
     * thứ tự theo ô sống 0..N-1.
     */
    public String[] liveKeys() {
        String[] live = new String[n];
        for (int k = 0; k < n; k++) {
            live[k] = pool[liveIdxFinal[k]];
        }
        return live;
    }

    /**
     * Giá trị xác định theo khóa: key.hashCode() | 1.
     * Không bao giờ bằng Integer.MIN_VALUE (NOT_FOUND).
     */
    public static int valueOf(String key) {
        // key.hashCode() | 1 đảm bảo giá trị lẻ, không phải MIN_VALUE
        return key.hashCode() | 1;
    }

    /**
     * Nạp N khóa ban đầu (pool[0..N)) vào bảng theo thứ tự.
     * @return số lần t.put trả FULL
     */
    public int load(HashIndex t) {
        int fullCount = 0;
        for (int i = 0; i < n; i++) {
            if (t.put(pool[i], valueOf(pool[i])) == HashIndex.FULL) fullCount++;
        }
        return fullCount;
    }

    /**
     * Chạy đúng steps chu kỳ: xóa pool[victimIdx[i]], thêm pool[N+i].
     * Vòng lặp chỉ gồm t.remove và t.put, không cấp phát, không HashSet.
     * Đo block bằng System.nanoTime() đặt ở đầu và cuối MỖI khối.
     *
     * @param t           bảng băm (đã load)
     * @param blockCycles số chu kỳ mỗi khối (caller dùng 5000)
     * @param blockNanos  mảng ghi thời gian mỗi khối (null nếu không cần);
     *                    độ dài phải >= ceil(steps/blockCycles)
     * @return Result{fullCount, ops=steps*2, blocks}
     */
    public Result execute(HashIndex t, int blockCycles, long[] blockNanos) {
        int  stepsI    = (int) steps;
        int  fullCount = 0;
        int  blockIdx  = 0;
        long blockStart = System.nanoTime(); // khởi đầu khối đầu tiên

        for (int i = 0; i < stepsI; i++) {
            // Đầu khối: khởi đồng hồ (trừ khối đầu tiên đã khởi trước vòng lặp)
            if (i > 0 && blockCycles > 0 && i % blockCycles == 0) {
                blockStart = System.nanoTime();
            }

            // Chu kỳ i: remove nạn nhân, put khóa mới
            t.remove(pool[victimIdx[i]]);
            String newKey = pool[n + i];
            if (t.put(newKey, valueOf(newKey)) == HashIndex.FULL) fullCount++;

            // Cuối khối: ghi thời gian
            if (blockCycles > 0 && (i + 1) % blockCycles == 0) {
                if (blockNanos != null) {
                    blockNanos[blockIdx] = System.nanoTime() - blockStart;
                }
                blockIdx++;
            }
        }

        // Khối cuối (ngắn hơn) – nếu chưa ghi
        if (blockCycles > 0 && stepsI % blockCycles != 0) {
            if (blockNanos != null) {
                blockNanos[blockIdx] = System.nanoTime() - blockStart;
            }
            blockIdx++;
        }
        // Trường hợp blockCycles <= 0 hoặc steps == 0
        if (blockCycles <= 0 && stepsI > 0) blockIdx = 0;

        return new Result(fullCount, steps * 2L, blockIdx);
    }

    /**
     * Kiểm tra sau workload (NGOÀI vùng đo):
     * size == N và mọi khóa sống get đúng giá trị.
     */
    public boolean verify(HashIndex t) {
        String[] live = liveKeys();
        if (t.size() != live.length) return false;
        for (int k = 0; k < live.length; k++) {
            if (t.get(live[k]) != valueOf(live[k])) return false;
        }
        return true;
    }
}
