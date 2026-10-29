package hashbench.test;

import static hashbench.test.TestHarness.check;
import static hashbench.test.TestHarness.eq;
import static hashbench.test.TestHarness.test;

import hashbench.HashIndex;
import hashbench.HashIndexFactory;
import hashbench.workload.ControlTable;
import hashbench.workload.QueryBench;
import hashbench.workload.Workload;
import hashbench.test.Registry.Impl;
import java.util.Arrays;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Set;

/**
 * Kiem tra Workload API moi va ControlTable.
 * p = 12 de chay nhanh; blockCycles = 500.
 */
public final class WorkloadTests {
    private static final int P          = 12;
    private static final int M          = 1 << P;       // 4096
    private static final long SEED      = 20261101L;
    private static final int  BLOCK_CYC = 500;

    private WorkloadTests() {}

    public static void run(Impl impl) {
        test("workload tai 0.5, C=2*M, blockCycles=500: dung va khop doi chung",
             new TestHarness.Body() { public void run() { one(impl, 0.5, 2 * M); } });
        test("workload tai 0.5, C=0: execute tra ops=0 blocks=0",
             new TestHarness.Body() { public void run() { zeroSteps(impl, 0.5); } });
        test("workload tai 0.8, C=M: dung va khop doi chung",
             new TestHarness.Body() { public void run() { one(impl, 0.8, M); } });
        test("generate tat dinh: cung seed => y het, seed khac => khac",
             new TestHarness.Body() { public void run() { determinism(); } });
        test("workload nhieu seed, muc tai va thuat toan khop bang doi chung",
             new TestHarness.Body() { public void run() { matrix(impl); } });
        test("presentQueries: moi phan tu trong liveKeys, khong co object trung pool",
             new TestHarness.Body() { public void run() { presentQueryCheck(); } });
        test("generate tu choi tham so load hoac steps khong hop le",
             new TestHarness.Body() { public void run() { invalidInputs(); } });
        test("thoi gian generate/execute p=20 (in ra, khong assert)",
             new TestHarness.Body() { public void run() { timingLarge(impl); } });
    }

    // ------------------------------------------------------------------ //

    /** Kiem tra day du mot cau hinh (a)(c)(d)(e). */
    static void one(Impl impl, double load, int steps) {
        Workload w = Workload.generate(P, load, steps, SEED, 20_000);

        // (a) pool khong trung, absentQueries khong giao pool
        checkNodup(w);

        // (b) generate hai lan cung seed => y het
        // (kiem trong determinism(); o day tin tuong)

        // (d) load + execute
        HashIndex t = impl.f.create(P);
        int fullLoad = w.load(t);
        eq(0, fullLoad, "nap du lieu ban dau phai khong FULL");

        int needed = (steps <= 0) ? 0 : (int) ((steps + BLOCK_CYC - 1) / BLOCK_CYC);
        long[] blockNanos = new long[needed == 0 ? 1 : needed];
        Workload.Result r = w.execute(t, BLOCK_CYC, blockNanos);

        eq(0, r.fullCount, "put trong workload khong duoc FULL");
        eq(w.n, t.size(), "size sau workload == N");
        check(w.verify(t), "verify() sau workload phai true");

        if (steps > 0) {
            eq(needed, r.blocks, "so khoi = ceil(steps/blockCycles)");
            for (int i = 0; i < needed; i++) {
                check(blockNanos[i] > 0, "blockNanos[" + i + "] phai > 0");
            }
        }

        // (e) ControlTable: checksum get presentQueries & absentQueries giong nhau
        HashIndex ctl = impl.f.create(P);
        ControlTable.fill(ctl, w);

        long pChkT   = QueryBench.timeGets(t,   w.presentQueries()).checksum;
        long pChkCtl = QueryBench.timeGets(ctl, w.presentQueries()).checksum;
        eq(pChkT, pChkCtl, "checksum present: workload == doi chung");

        long aChkT   = QueryBench.timeGets(t,   w.absentQueries()).checksum;
        long aChkCtl = QueryBench.timeGets(ctl, w.absentQueries()).checksum;
        eq(aChkT, aChkCtl, "checksum absent: workload == doi chung");
    }

    /** (f) steps=0: execute tra ops=0, blocks=0. */
    static void zeroSteps(Impl impl, double load) {
        Workload w = Workload.generate(P, load, 0, SEED, 100);
        HashIndex t = impl.f.create(P);
        w.load(t);
        Workload.Result r = w.execute(t, BLOCK_CYC, null);
        eq(0L,  r.ops,    "ops phai = 0 khi steps=0");
        eq(0,   r.blocks, "blocks phai = 0 khi steps=0");
        eq(0,   r.fullCount, "fullCount phai = 0 khi steps=0");
        check(w.verify(t), "verify sau steps=0");
    }

    /** (b) tinh tat dinh. */
    static void determinism() {
        Workload w1a = Workload.generate(P, 0.5, M, SEED,     500);
        Workload w1b = Workload.generate(P, 0.5, M, SEED,     500);
        Workload w2  = Workload.generate(P, 0.5, M, SEED + 1, 500);

        // Cung seed => pool y het
        check(Arrays.equals(w1a.pool(), w1b.pool()), "pool cung seed phai giong nhau");
        check(Arrays.equals(w1a.presentQueries(), w1b.presentQueries()),
              "presentQueries cung seed phai giong nhau");
        check(Arrays.equals(w1a.absentQueries(), w1b.absentQueries()),
              "absentQueries cung seed phai giong nhau");

        // Seed khac => pool khac (xac suat va cham cuc thap)
        check(!Arrays.equals(w1a.pool(), w2.pool()), "pool seed khac phai khac");
    }

    /** Chay workload tren ma tran seed/load voi moi implementation kha dung. */
    static void matrix(Impl impl) {
        double[] loads = {0.25, 0.5, 0.8, 1.0};
        long[] seeds = {1L, 42L, 20261101L};
        int p = 8;
        int steps = 129;
        int blockCycles = 32;

        for (double load : loads) {
            for (long seed : seeds) {
                Workload w = Workload.generate(p, load, steps, seed, 256);
                HashIndex table = impl.f.create(p);
                eq(0, w.load(table), "nap workload ma tran khong duoc FULL");

                int blocks = (steps + blockCycles - 1) / blockCycles;
                long[] blockNanos = new long[blocks];
                Workload.Result result = w.execute(table, blockCycles, blockNanos);
                eq(0, result.fullCount, "put workload ma tran khong duoc FULL");
                eq(blocks, result.blocks, "so block workload ma tran");
                eq(w.n, table.size(), "size workload ma tran");
                check(w.verify(table), "verify workload ma tran");
                for (long nanos : blockNanos) {
                    check(nanos > 0, "block workload ma tran phai co thoi gian");
                }

                HashIndex control = impl.f.create(p);
                ControlTable.fill(control, w);
                eq(QueryBench.timeGets(table, w.presentQueries()).checksum,
                        QueryBench.timeGets(control, w.presentQueries()).checksum,
                        "checksum present workload ma tran");
                eq(QueryBench.timeGets(table, w.absentQueries()).checksum,
                        QueryBench.timeGets(control, w.absentQueries()).checksum,
                        "checksum absent workload ma tran");
            }
        }
    }

    static void invalidInputs() {
        boolean threw = false;
        try {
            Workload.generate(8, 0.5, -1, SEED, 10);
        } catch (IllegalArgumentException expected) {
            threw = true;
        }
        check(threw, "steps am phai nem IllegalArgumentException");

        double[] invalidLoads = {0.0, -0.5, 1.01, Double.NaN,
                Double.POSITIVE_INFINITY};
        for (double load : invalidLoads) {
            threw = false;
            try {
                Workload.generate(8, load, 0, SEED, 10);
            } catch (IllegalArgumentException expected) {
                threw = true;
            }
            check(threw, "load ngoai (0, 1] phai nem IllegalArgumentException: "
                    + load);
        }
    }

    /** (a) va (c). */
    static void checkNodup(Workload w) {
        String[] pool = w.pool();
        // pool khong co phan tu trung
        Set<String> poolSet = new HashSet<String>(Arrays.asList(pool));
        eq(pool.length, poolSet.size(), "pool khong duoc co phan tu trung");

        // absentQueries khong giao pool
        for (String k : w.absentQueries()) {
            check(!poolSet.contains(k), "absentQueries khong duoc nam trong pool: " + k);
        }

        // (c) presentQueries: moi phan tu la khoa song (nam trong liveKeys)
        //     va khong co object nao trung tham chieu voi pool
        Set<String> liveSet = new HashSet<String>(Arrays.asList(w.liveKeys()));
        IdentityHashMap<String, Boolean> poolIdentity = new IdentityHashMap<String, Boolean>();
        for (String s : pool) poolIdentity.put(s, Boolean.TRUE);

        for (String pq : w.presentQueries()) {
            check(liveSet.contains(pq),
                  "presentQuery phai la khoa song: " + pq);
            check(!poolIdentity.containsKey(pq),
                  "presentQuery khong duoc la cung object voi phan tu pool");
        }
    }

    /** (c) kiem rieng identity. */
    static void presentQueryCheck() {
        Workload w = Workload.generate(P, 0.5, M, SEED, 1000);
        checkNodup(w);
    }

    /** Do thoi gian generate/execute p=20 (in ra, khong assert). */
    static void timingLarge(Impl impl) {
        int bigP = 20;
        long bigSteps = 1 << 20; // 1048576
        long t0 = System.currentTimeMillis();
        Workload w = Workload.generate(bigP, 0.5, bigSteps, 99L, 1000);
        long genMs = System.currentTimeMillis() - t0;
        System.out.println("  [timing] p=20 generate: " + genMs + " ms");

        HashIndex t = impl.f.create(bigP);
        w.load(t);
        long t1 = System.currentTimeMillis();
        w.execute(t, 5000, null);
        long execMs = System.currentTimeMillis() - t1;
        System.out.println("  [timing] p=20 execute:  " + execMs + " ms");
    }
}
