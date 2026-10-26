package hashbench.workload;

import hashbench.HashIndex;
import java.util.Arrays;
import java.util.SplittableRandom;

/**
 * Workload xóa-thêm của RQ2/RQ3.
 *  - Sinh TRƯỚC (ngoài vùng đo): pool n + C khóa phân biệt, dãy khóa bị xóa, truy vấn có mặt / vắng mặt.
 *  - Bước i (0 <= i < C): remove(victims[i]) rồi put(insertKeys[i]). Một "thao tác" = một remove hoặc một put,
 *    nên tổng 2C thao tác; khối = opsPerBlock thao tác liên tiếp (mặc định 10^4). XÁC NHẬN với CONVENTIONS (B7).
 *  - Khóa vắng mặt là khóa chưa từng nằm trong pool.
 */
public final class Workload {
    public interface BlockListener { void onBlock(int blockIdx, long opsDone, long blockNanos); }

    public static final class Result {
        public long totalNanos;
        public long[] blockNanos;
        public int blocks;
        public long fullCount, badRemoves;
        public long medianBlockNanos() {
            long[] c = Arrays.copyOf(blockNanos, blocks);
            Arrays.sort(c);
            return blocks == 0 ? 0 : c[blocks / 2];
        }
        public long maxBlockNanos() {
            long m = 0;
            for (int i = 0; i < blocks; i++) m = Math.max(m, blockNanos[i]);
            return m;
        }
    }

    public final int p, m, n, steps;
    public final long[] initialKeys, insertKeys, victims, finalLive, presentQueries, absentQueries;

    private Workload(int p, int n, int steps, long[] ini, long[] ins, long[] vic, long[] live, long[] pq, long[] aq) {
        this.p = p; this.m = 1 << p; this.n = n; this.steps = steps;
        initialKeys = ini; insertKeys = ins; victims = vic; finalLive = live; presentQueries = pq; absentQueries = aq;
    }

    public static int valueOf(long key) {
        int v = (int) (key ^ (key >>> 32)) * 0x9E3779B1;
        return v == HashIndex.NOT_FOUND ? 0 : v;
    }

    /** loadFactor: 0,5 hoặc 0,8; steps = C (số cặp xóa-thêm); xem lớp-doc về đơn vị. */
    public static Workload generate(int p, double loadFactor, int steps, long seed, int presentCount, int absentCount) {
        int m = 1 << p;
        int n = (int) Math.round(loadFactor * m);
        SplittableRandom root = new SplittableRandom(seed);
        SplittableRandom rKeys = root.split(), rPick = root.split(), rQ = root.split();

        hashbench.LongSet universe = new hashbench.LongSet(n + steps);
        long[] ini = new long[n], ins = new long[steps];
        for (int i = 0; i < n; i++) ini[i] = freshKey(rKeys, universe);
        for (int i = 0; i < steps; i++) ins[i] = freshKey(rKeys, universe);

        long[] live = ini.clone(), vic = new long[steps];
        for (int i = 0; i < steps; i++) {
            int j = rPick.nextInt(n);
            vic[i] = live[j];
            live[j] = ins[i];
        }
        long[] pq = new long[presentCount];
        for (int i = 0; i < presentCount; i++) pq[i] = live[rQ.nextInt(n)];     // có hoàn lại (B6)
        long[] aq = new long[absentCount];
        for (int i = 0; i < absentCount; i++) {
            long k;
            do { k = rQ.nextLong() & Long.MAX_VALUE; } while (universe.contains(k));
            aq[i] = k;
        }
        return new Workload(p, n, steps, ini, ins, vic, live, pq, aq);
    }

    private static long freshKey(SplittableRandom r, hashbench.LongSet set) {
        long k;
        do { k = r.nextLong() & Long.MAX_VALUE; } while (!set.add(k));
        return k;
    }

    /** Nạp n khóa ban đầu (pha dựng bảng; người gọi tự đo thời gian). Trả về số lần put trả FULL (phải = 0). */
    public int load(HashIndex t) {
        int full = 0;
        for (int i = 0; i < initialKeys.length; i++)
            if (t.put(initialKeys[i], valueOf(initialKeys[i])) == HashIndex.FULL) full++;
        return full;
    }

    /** Thực thi 2*steps thao tác theo khối. nanoTime chỉ bao quanh từng khối; listener chạy NGOÀI vùng đo. */
    public Result execute(HashIndex t, int opsPerBlock, BlockListener listener) {
        long totalOps = 2L * steps;
        int blocks = (int) ((totalOps + opsPerBlock - 1) / opsPerBlock);
        Result res = new Result();
        res.blockNanos = new long[blocks];
        res.blocks = blocks;
        long full = 0, bad = 0, total = 0;
        for (int b = 0; b < blocks; b++) {
            long start = (long) b * opsPerBlock, end = Math.min(totalOps, start + opsPerBlock);
            long t0 = System.nanoTime();
            for (long k = start; k < end; k++) {
                int i = (int) (k >> 1);
                if ((k & 1) == 0) {
                    if (!t.remove(victims[i])) bad++;
                } else {
                    long key = insertKeys[i];
                    if (t.put(key, valueOf(key)) == HashIndex.FULL) full++;
                }
            }
            long dt = System.nanoTime() - t0;
            res.blockNanos[b] = dt;
            total += dt;
            if (listener != null) listener.onBlock(b, end, dt);
        }
        res.totalNanos = total;
        res.fullCount = full;
        res.badRemoves = bad;
        return res;
    }

    /** Kiểm sau workload (NGOÀI vùng đo): size == n và mọi khóa sống có đúng giá trị. */
    public boolean verify(HashIndex t) {
        if (t.size() != n) return false;
        for (int i = 0; i < finalLive.length; i++)
            if (t.get(finalLive[i]) != valueOf(finalLive[i])) return false;
        return true;
    }
}
