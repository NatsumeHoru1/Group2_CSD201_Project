package hashbench.workload;

import hashbench.Generator;
import hashbench.HashIndex;
import hashbench.LongSet;
import hashbench.test.TestUtil;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.SplittableRandom;

/**
 * Workload xóa-thêm của RQ2/RQ3.
 * Mỗi chu kỳ: xóa 1 khóa ngẫu nhiên đang tồn tại -> thêm 1 khóa mới.
 */
public final class Workload {
    public interface BlockListener { void onBlock(int blockNo, int ops, long elapsedNanos); }

    public static final class Result {
        public final int fullCount;
        public final long ops;
        public Result(int fullCount, long ops) {
            this.fullCount = fullCount;
            this.ops = ops;
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

    private Workload(int p, int n, int steps, long[] ini, long[] ins, long[] vic, long[] live, long[] pq, long[] aq) {
        this.p = p;
        this.n = n;
        this.steps = steps;
        this.initialKeys = ini;
        this.insertKeys = ins;
        this.victimKeys = vic;
        this.liveKeys = live;
        this.presentQueries = pq;
        this.absentQueries = aq;
        this.pq = pq;
        this.aq = aq;
    }

    public static int valueOf(long key) {
        return TestUtil.valueOf(key);
    }

    public static Workload generate(int p, double loadFactor, int steps, long seed, int presentCount, int absentCount) {
        if (p < 1 || p > 30) throw new IllegalArgumentException("p phải nằm trong [1, 30]");
        int cap = 1 << p;
        int n = (int) Math.round(cap * loadFactor);
        if (n <= 0) n = 1;
        if (n > cap) n = cap;

        long[] initial = Generator.generatePresentKeys(seed + 101, n);
        LongSet liveSet = new LongSet(n + presentCount + absentCount);
        for (long k : initial) liveSet.add(k);

        long[] inserts = Generator.generatePresentKeys(seed + 777, presentCount);
        long[] absent = Generator.generateAbsentKeys(seed + 999, absentCount, liveSet);

        long[] presentQueries = new long[presentCount];
        for (int i = 0; i < presentCount; i++) {
            presentQueries[i] = initial[i % initial.length];
        }
        if (presentQueries.length == 0) presentQueries = new long[0];

        long[] live = Arrays.copyOf(initial, initial.length);
        return new Workload(p, n, steps, initial, inserts, new long[0], live, presentQueries, absent);
    }

    public int load(HashIndex t) {
        int fullCount = 0;
        for (int i = 0; i < initialKeys.length; i++) {
            long key = initialKeys[i];
            if (t.put(key, valueOf(key)) == HashIndex.FULL) fullCount++;
        }
        this.liveKeys = Arrays.copyOf(initialKeys, initialKeys.length);
        return fullCount;
    }

    public Result execute(HashIndex t, int opsPerBlock, BlockListener listener) {
        Set<Long> liveSet = new HashSet<Long>();
        for (long k : initialKeys) liveSet.add(k);

        int fullCount = 0;
        long totalOps = 0;
        SplittableRandom rng = new SplittableRandom(20261025L + steps);
        for (int step = 0; step < steps; step++) {
            if (liveSet.isEmpty()) break;
            Long[] arr = liveSet.toArray(new Long[0]);
            long victim = arr[rng.nextInt(arr.length)].longValue();
            t.remove(victim);
            liveSet.remove(victim);

            long newKey = insertKeys[step % insertKeys.length];
            int putRes = t.put(newKey, valueOf(newKey));
            if (putRes == HashIndex.FULL) fullCount++;
            else liveSet.add(newKey);
            totalOps++;

            if (opsPerBlock > 0 && (step + 1) % opsPerBlock == 0 && listener != null) {
                long start = System.nanoTime();
                listener.onBlock(step / opsPerBlock, opsPerBlock, System.nanoTime() - start);
            }
        }
        liveKeys = new long[liveSet.size()];
        int index = 0;
        for (Long k : liveSet) liveKeys[index++] = k.longValue();
        return new Result(fullCount, totalOps);
    }

    /** Kiểm sau workload (NGOÀI vùng đo): size == n và mỗi khóa sống có đúng giá trị. */
    public boolean verify(HashIndex t) {
        if (t.size() != liveKeys.length) return false;
        Set<Long> seen = new HashSet<Long>();
        for (long k : liveKeys) {
            if (!seen.add(k)) return false;
            if (t.get(k) != valueOf(k)) return false;
        }
        return true;
    }
}
