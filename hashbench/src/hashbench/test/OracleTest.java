package hashbench.test;

import static hashbench.HashIndex.*;
import static hashbench.test.TestHarness.check;
import static hashbench.test.TestHarness.eq;
import static hashbench.test.TestHarness.test;

import hashbench.HashIndex;
import hashbench.test.Registry.Impl;
import java.util.HashMap;
import java.util.SplittableRandom;

/**
 * Oracle: so voi HashMap tren 10^6 thao tac (put/get/remove = 40/40/20), M = 2^12, ba mien khoa ~0,5M / M / 2M.
 * Chi cap nhat oracle khi bang KHONG tra FULL. Kiem tra ca dieu kien FULL dung/sai (size == M va khoa chua co).
 */
public final class OracleTest {
    static final int P = 12, M = 1 << P;
    static final long SEED = 20260002L;

    private OracleTest() {}

    public static void runAll(final Impl impl, final long ops) {
        final double[] factors = { 0.5, 1.0, 2.0 };
        for (int i = 0; i < factors.length; i++) {
            final double f = factors[i];
            final boolean offset = (i == 1);
            test("oracle " + ops + " thao tac, mien = " + f + "M" + (offset ? " (khoa lon)" : ""),
                 new TestHarness.Body() { public void run() { one(impl, f, offset, ops); } });
        }
    }

    static void one(Impl impl, double factor, boolean offset, long ops) {
        final int domain = (int) (factor * M);
        final long mul = offset ? 1_000_003L : 1L, off = offset ? 12_345_678_901L : 0L;
        HashIndex t = impl.f.create(P);
        HashMap<String, Integer> ref = new HashMap<String, Integer>();
        SplittableRandom rng = new SplittableRandom(SEED + (long) (factor * 100));
        long fulls = 0, inserts = 0, updates = 0, removes = 0;

        for (long op = 1; op <= ops; op++) {
            String key = hashbench.KeyCodec.encode(rng.nextInt(domain) * mul + off);
            int r = rng.nextInt(100);
            if (r < 40) {
                int v = rng.nextInt(); if (v == NOT_FOUND) v = 0;
                boolean present = ref.containsKey(key);
                int expect = present ? UPDATED : (ref.size() == M ? FULL : INSERTED);
                int got = t.put(key, v);
                if (got != expect) throw new AssertionError("op " + op + " put(" + key + "): mong " + expect + ", nhan " + got);
                if (got == FULL) fulls++; else { ref.put(key, v); if (got == INSERTED) inserts++; else updates++; }
            } else if (r < 80) {
                Integer e = ref.get(key);
                int exp = e == null ? NOT_FOUND : e;
                int got = t.get(key);
                if (got != exp) throw new AssertionError("op " + op + " get(" + key + "): mong " + exp + ", nhan " + got);
            } else {
                boolean exp = ref.remove(key) != null;
                boolean got = t.remove(key);
                if (got != exp) throw new AssertionError("op " + op + " remove(" + key + "): mong " + exp + ", nhan " + got);
                if (got) removes++;
            }
            if (t.size() != ref.size()) throw new AssertionError("op " + op + ": size " + t.size() + " != " + ref.size());
            if ((op & 0x3FFF) == 0) TestUtil.validate(t);
            if (op % 100_000 == 0) sweep(t, ref, domain, mul, off, op);
        }
        sweep(t, ref, domain, mul, off, ops);
        TestUtil.validate(t);
        if (factor > 1.0 && ops >= 200_000) check(fulls > 0, "mien " + factor + "M ma chua bao gio gap FULL: oracle khong phu nhanh day");
        check(inserts > 0 && removes > 0, "oracle khong sinh du loai thao tac");
        System.out.println("      (insert=" + inserts + " update=" + updates + " remove=" + removes + " FULL=" + fulls + " size cuoi=" + t.size() + ")");
    }

    static void sweep(HashIndex t, HashMap<String, Integer> ref, int domain, long mul, long off, long op) {
        for (int k = 0; k < domain; k++) {
            String key = hashbench.KeyCodec.encode(k * mul + off);
            Integer e = ref.get(key);
            int exp = e == null ? NOT_FOUND : e;
            if (t.get(key) != exp) throw new AssertionError("quet toan mien tai op " + op + ": get(" + key + ") sai");
        }
    }
}
