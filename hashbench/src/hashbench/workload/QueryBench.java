package hashbench.workload;

import hashbench.HashIndex;

/** Đo một dãy get. checksum = output_check (giá trị tìm thấy + số get không tìm thấy). */
public final class QueryBench {
    public static final class Timing {
        public final long checksum;
        public final long nanos;
        public Timing(long checksum, long nanos) {
            this.checksum = checksum;
            this.nanos = nanos;
        }
    }

    private QueryBench() {}

    public static Timing timeGets(HashIndex t, long[] q) {
        long checksum = 0L;
        long start = System.nanoTime();
        for (long key : q) {
            int v = t.get(key);
            checksum += (v == HashIndex.NOT_FOUND) ? 1L : (long) v + 1L;
        }
        long elapsed = System.nanoTime() - start;
        return new Timing(checksum, elapsed);
    }
}
