package hashbench.workload;

import hashbench.Generator;
import hashbench.HashIndex;
import hashbench.LongSet;
import hashbench.test.TestUtil;
import java.util.Arrays;
import java.util.SplittableRandom;

/**
 * Deterministic delete-and-insert workload for RQ2/RQ3.
 * The workload and its victim schedule are generated before measurement.
 */
public final class Workload {
    public interface BlockListener {
        void onBlock(int blockNo, int ops, long elapsedNanos);
    }

    public static final class Result {
        public final int fullCount;
        public final long ops;
        public final int blocks;

        public Result(int fullCount, long ops, int blocks) {
            this.fullCount = fullCount;
            this.ops = ops;
            this.blocks = blocks;
        }
    }

    public final int p;
    public final int n;
    public final int steps;
    public final long[] initialKeys;
    public final long[] insertKeys;
    public final long[] victimKeys;
    public long[] liveKeys;
    public final long[] presentQueries;
    public final long[] absentQueries;
    public final long[] pq;
    public final long[] aq;
    private final long[] finalLiveKeys;

    private Workload(int p, int n, int steps, long[] pool, long[] victimKeys,
            long[] finalLiveKeys, long[] presentQueries, long[] absentQueries) {
        this.p = p;
        this.n = n;
        this.steps = steps;
        this.initialKeys = Arrays.copyOfRange(pool, 0, n);
        this.insertKeys = Arrays.copyOfRange(pool, n, pool.length);
        this.victimKeys = victimKeys;
        this.finalLiveKeys = Arrays.copyOf(finalLiveKeys, finalLiveKeys.length);
        this.liveKeys = Arrays.copyOf(initialKeys, initialKeys.length);
        this.presentQueries = presentQueries;
        this.absentQueries = absentQueries;
        this.pq = presentQueries;
        this.aq = absentQueries;
    }

    public static int valueOf(long key) {
        return TestUtil.valueOf(key);
    }

    public static Workload generate(int p, double loadFactor, int steps, long seed,
            int presentCount, int absentCount) {
        if (p < 1 || p > 30) {
            throw new IllegalArgumentException("p phải nằm trong [1, 30]");
        }
        if (!Double.isFinite(loadFactor) || loadFactor <= 0.0 || loadFactor > 1.0) {
            throw new IllegalArgumentException("loadFactor phải nằm trong (0, 1]");
        }
        if (steps < 0 || presentCount < 0 || absentCount < 0) {
            throw new IllegalArgumentException("steps và số truy vấn phải >= 0");
        }

        int capacity = 1 << p;
        int n = (int) Math.round(capacity * loadFactor);
        if (n < 1) n = 1;
        if (n > capacity) n = capacity;

        long poolSize = (long) n + steps;
        long allKeyCount = poolSize + absentCount;
        if (allKeyCount > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("workload quá lớn cho mảng Java");
        }

        long[] allKeys = Generator.generatePresentKeys(seed + 101, (int) allKeyCount);
        long[] pool = Arrays.copyOfRange(allKeys, 0, (int) poolSize);
        long[] absent = Arrays.copyOfRange(allKeys, (int) poolSize, allKeys.length);

        int[] liveSlots = new int[n];
        for (int i = 0; i < n; i++) liveSlots[i] = i;
        long[] victims = new long[steps];
        SplittableRandom random = new SplittableRandom(seed ^ 0x57A11C0DEL);
        for (int step = 0; step < steps; step++) {
            int slot = random.nextInt(n);
            victims[step] = pool[liveSlots[slot]];
            liveSlots[slot] = n + step;
        }

        long[] live = new long[n];
        for (int i = 0; i < n; i++) live[i] = pool[liveSlots[i]];

        long[] present = new long[presentCount];
        SplittableRandom queryRandom = new SplittableRandom(seed ^ 0x5DEECE66DL);
        for (int i = 0; i < present.length; i++) {
            present[i] = live[queryRandom.nextInt(n)];
        }

        return new Workload(p, n, steps, pool, victims, live, present, absent);
    }

    public int load(HashIndex table) {
        int fullCount = 0;
        for (long key : initialKeys) {
            if (table.put(key, valueOf(key)) == HashIndex.FULL) fullCount++;
        }
        liveKeys = Arrays.copyOf(initialKeys, initialKeys.length);
        return fullCount;
    }

    /**
     * Run exactly {@code steps} delete/insert cycles. A block is measured in
     * cycles, so the callback's operation count is twice its cycle count.
     */
    public Result execute(HashIndex table, int cyclesPerBlock, BlockListener listener) {
        if (table == null) throw new IllegalArgumentException("table không được null");

        int fullCount = 0;
        int blockIndex = 0;
        int blockCycles = 0;
        long blockStart = 0L;

        for (int step = 0; step < steps; step++) {
            if (listener != null && cyclesPerBlock > 0 && blockCycles == 0) {
                blockStart = System.nanoTime();
            }

            if (!table.remove(victimKeys[step])) {
                throw new IllegalStateException("không xóa được khóa nạn nhân ở chu kỳ " + step);
            }
            if (table.put(insertKeys[step], valueOf(insertKeys[step])) == HashIndex.FULL) {
                fullCount++;
            }

            if (listener != null && cyclesPerBlock > 0) {
                blockCycles++;
                if (blockCycles == cyclesPerBlock || step == steps - 1) {
                    listener.onBlock(blockIndex++, blockCycles * 2,
                            System.nanoTime() - blockStart);
                    blockCycles = 0;
                }
            }
        }

        liveKeys = Arrays.copyOf(finalLiveKeys, finalLiveKeys.length);
        int blocks = cyclesPerBlock > 0
                ? (int) ((steps + (long) cyclesPerBlock - 1L) / cyclesPerBlock)
                : 0;
        return new Result(fullCount, steps * 2L, blocks);
    }

    /** Verify the final live keys after the measured workload. */
    public boolean verify(HashIndex table) {
        if (table.size() != liveKeys.length) return false;
        LongSet seen = new LongSet(liveKeys.length);
        for (long key : liveKeys) {
            if (!seen.add(key) || table.get(key) != valueOf(key)) return false;
        }
        return true;
    }
}
