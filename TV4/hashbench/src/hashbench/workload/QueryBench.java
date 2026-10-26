package hashbench.workload;

import hashbench.HashIndex;

/** Đo một dãy get. nanoTime chỉ bao vòng lặp; checksum = output_check (giá trị tìm thấy + số get không tìm thấy) (và chặn JIT loại bỏ code). */
public final class QueryBench {
    public static final class Timing {
        public final long nanos, checksum;
        Timing(long n, long c) { nanos = n; checksum = c; }
    }

    private QueryBench() {}

    public static Timing timeGets(HashIndex t, long[] q) {
        long sum = 0;
        long s = System.nanoTime();
        for (int i = 0; i < q.length; i++) {
            int v = t.get(q[i]);
            if (v == HashIndex.NOT_FOUND) sum++; else sum += v;     // quy ước output_check của nhóm
        }
        long e = System.nanoTime();
        return new Timing(e - s, sum);
    }
}
