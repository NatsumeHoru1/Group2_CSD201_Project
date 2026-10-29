package hashbench.workload;

import hashbench.Generator;
import hashbench.HashIndex;
import hashbench.KeyCodec;
import java.util.SplittableRandom;

/**
 * Workload xoa-them tuan hoan cho RQ2/RQ3.
 * Moi chu ky: xoa 1 khoa ngau nhien dang song -> them 1 khoa moi.
 * Thiet ke O(1)/chu ky: dung mang chi so liveIdx thay vi HashSet.
 */
public final class Workload {

    // ------------------------------------------------------------------ //
    //  Ket qua execute                                                     //
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
    //  Truong cong khai bat bien                                           //
    // ------------------------------------------------------------------ //
    public final int  p;
    public final int  n;          // N = round(load * 2^p), kep [1, 2^p]
    public final long steps;      // so chu ky C
    public final int  queries;    // so truy van moi loai

    // ------------------------------------------------------------------ //
    //  Du lieu noi bo (tinh mot lan khi generate)                         //
    // ------------------------------------------------------------------ //
    private final String[] pool;        // dai N+steps: [0,N)=ban dau, [N,N+steps)=them theo thu tu
    private final int[]    victimIdx;   // victimIdx[i]: chi so trong pool bi xoa o chu ky i
    private final int[]    liveIdxFinal;// liveIdx sau khi mo phong du steps chu ky (dai N)
    private final String[] presentQueriesArr; // queries object String MOI tu tap song
    private final String[] absentQueriesArr;  // queries khoa khong bao gio trong pool

    // ------------------------------------------------------------------ //
    //  Constructor rieng tu                                                //
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
    //  API cong khai                                                       //
    // ================================================================== //

    /**
     * Sinh workload tat dinh theo seed.
     *
     * @param p       log2 dung luong bang, [1..30]
     * @param load    he so tai, (0,1]
     * @param steps   so chu ky C (ep int khi dung mang Java)
     * @param seed    hat gieo ngau nhien
     * @param queries so truy van moi loai (co mat / vang mat)
     */
    public static Workload generate(int p, double load, long steps, long seed, int queries) {
        if (p < 1 || p > 30) throw new IllegalArgumentException("p phai trong [1, 30]");
        if (Double.isNaN(load) || load <= 0.0 || load > 1.0) {
            throw new IllegalArgumentException("load phai trong (0, 1]");
        }
        if (steps < 0) throw new IllegalArgumentException("steps phai >= 0");
        int cap = 1 << p;
        int n   = (int) Math.round(load * cap);
        if (n < 1) n = 1;
        if (n > cap) n = cap;

        // Kiem tran mang Java (mang toi da Integer.MAX_VALUE phan tu)
        long totalPool = (long) n + steps;
        long totalIds  = totalPool + queries;
        if (steps > Integer.MAX_VALUE || queries < 0 ||
            totalPool > Integer.MAX_VALUE || totalIds > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("steps hoac queries qua lon cho mang Java");
        }
        int stepsI   = (int) steps;
        int totalInt = (int) totalIds;

        // -- Buoc 1: sinh toan bo ID khong trung mot lan --------------- //
        long[] allIds = Generator.generatePresentIds(seed, totalInt);
        // pool[0..N+steps): cac khoa ban dau + them
        String[] pool = Generator.idsToKeys(java.util.Arrays.copyOfRange(allIds, 0, n + stepsI));
        // absentQueries: phan con lai (khong bao gio nam trong pool)
        String[] absentQ = Generator.idsToKeys(
                java.util.Arrays.copyOfRange(allIds, n + stepsI, totalInt));

        // -- Buoc 2: mo phong nan nhan (O(steps), khong can bang) ------ //
        int[] liveIdx  = new int[n];
        for (int k = 0; k < n; k++) liveIdx[k] = k;  // liveIdx[k] = chi so trong pool

        int[] vicIdx = new int[stepsI];
        SplittableRandom rng = new SplittableRandom(seed ^ 0x57A11C0DEL);
        for (int i = 0; i < stepsI; i++) {
            int j = rng.nextInt(n);
            vicIdx[i]  = liveIdx[j];   // chi so trong pool bi xoa
            liveIdx[j] = n + i;        // thay bang khoa moi (chi so trong pool)
        }
        // liveIdx gio la tap song SAU steps chu ky (dung cho liveKeys & presentQueries)
        int[] liveIdxFinal = java.util.Arrays.copyOf(liveIdx, n);

        // -- Buoc 3: presentQueries - object String MOI tu tap song ----- //
        SplittableRandom rng2 = new SplittableRandom(seed ^ 0x5DEECE66DL);
        String[] presentQ = new String[queries];
        for (int i = 0; i < queries; i++) {
            int idx = liveIdxFinal[rng2.nextInt(n)];  // chi so trong pool
            // tao object String MOI bang encode(decode(pool[idx]))
            presentQ[i] = KeyCodec.encode(KeyCodec.decode(pool[idx]));
        }

        return new Workload(p, n, steps, queries,
                pool, vicIdx, liveIdxFinal, presentQ, absentQ);
    }

    // ------------------------------------------------------------------ //

    /** pool[0..N+steps): [0,N)=khoa ban dau, [N,N+steps)=khoa them theo chu ky. */
    public String[] pool() { return pool; }

    /** queries object String MOI - deu la khoa song tai moc C. */
    public String[] presentQueries() { return presentQueriesArr; }

    /** queries khoa khong bao gio nam trong pool. */
    public String[] absentQueries() { return absentQueriesArr; }

    /**
     * N khoa song tai moc C (tham chieu toi phan tu pool),
     * thu tu theo o song 0..N-1.
     */
    public String[] liveKeys() {
        String[] live = new String[n];
        for (int k = 0; k < n; k++) {
            live[k] = pool[liveIdxFinal[k]];
        }
        return live;
    }

    /**
     * Gia tri xac dinh theo khoa: key.hashCode() | 1.
     * Khong bao gio bang Integer.MIN_VALUE (NOT_FOUND).
     */
    public static int valueOf(String key) {
        // key.hashCode() | 1 dam bao gia tri le, khong phai MIN_VALUE
        return key.hashCode() | 1;
    }

    /**
     * Nap N khoa ban dau (pool[0..N)) vao bang theo thu tu.
     * @return so lan t.put tra FULL
     */
    public int load(HashIndex t) {
        int fullCount = 0;
        for (int i = 0; i < n; i++) {
            if (t.put(pool[i], valueOf(pool[i])) == HashIndex.FULL) fullCount++;
        }
        return fullCount;
    }

    /**
     * Chay dung steps chu ky: xoa pool[victimIdx[i]], them pool[N+i].
     * Vong lap chi gom t.remove va t.put, khong cap phat, khong HashSet.
     * Do block bang System.nanoTime() dat o dau va cuoi MOI khoi.
     *
     * @param t           bang bam (da load)
     * @param blockCycles so chu ky moi khoi (caller dung 5000)
     * @param blockNanos  mang ghi thoi gian moi khoi (null neu khong can);
     *                    do dai phai >= ceil(steps/blockCycles)
     * @return Result{fullCount, ops=steps*2, blocks}
     */
    public Result execute(HashIndex t, int blockCycles, long[] blockNanos) {
        int  stepsI    = (int) steps;
        int  fullCount = 0;
        int  blockIdx  = 0;
        long blockStart = System.nanoTime(); // khoi dau khoi dau tien

        for (int i = 0; i < stepsI; i++) {
            // Dau khoi: khoi dong ho (tru khoi dau tien da khoi truoc vong lap)
            if (i > 0 && blockCycles > 0 && i % blockCycles == 0) {
                blockStart = System.nanoTime();
            }

            // Chu ky i: remove nan nhan, put khoa moi
            t.remove(pool[victimIdx[i]]);
            String newKey = pool[n + i];
            if (t.put(newKey, valueOf(newKey)) == HashIndex.FULL) fullCount++;

            // Cuoi khoi: ghi thoi gian
            if (blockCycles > 0 && (i + 1) % blockCycles == 0) {
                if (blockNanos != null) {
                    long elapsed = System.nanoTime() - blockStart; blockNanos[blockIdx] = elapsed > 0 ? elapsed : 1L;
                }
                blockIdx++;
            }
        }

        // Khoi cuoi (ngan hon) - neu chua ghi
        if (blockCycles > 0 && stepsI % blockCycles != 0) {
            if (blockNanos != null) {
                long elapsed = System.nanoTime() - blockStart; blockNanos[blockIdx] = elapsed > 0 ? elapsed : 1L;
            }
            blockIdx++;
        }
        // Truong hop blockCycles <= 0 hoac steps == 0
        if (blockCycles <= 0 && stepsI > 0) blockIdx = 0;

        return new Result(fullCount, steps * 2L, blockIdx);
    }

    /**
     * Kiem tra sau workload (NGOAI vung do):
     * size == N va moi khoa song get dung gia tri.
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
