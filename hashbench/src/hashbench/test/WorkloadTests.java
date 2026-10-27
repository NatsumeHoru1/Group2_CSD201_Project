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
 * Kiểm tra Workload API mới và ControlTable.
 * p = 12 để chạy nhanh; blockCycles = 500.
 */
public final class WorkloadTests {
    private static final int P          = 12;
    private static final int M          = 1 << P;       // 4096
    private static final long SEED      = 20261101L;
    private static final int  BLOCK_CYC = 500;

    private WorkloadTests() {}

    public static void run(Impl impl) {
        test("workload tải 0.5, C=2*M, blockCycles=500: đúng và khớp đối chứng",
             new TestHarness.Body() { public void run() { one(impl, 0.5, 2 * M); } });
        test("workload tải 0.5, C=0: execute trả ops=0 blocks=0",
             new TestHarness.Body() { public void run() { zeroSteps(impl, 0.5); } });
        test("workload tải 0.8, C=M: đúng và khớp đối chứng",
             new TestHarness.Body() { public void run() { one(impl, 0.8, M); } });
        test("generate tất định: cùng seed => y hệt, seed khác => khác",
             new TestHarness.Body() { public void run() { determinism(); } });
        test("presentQueries: mọi phần tử trong liveKeys, không có object trùng pool",
             new TestHarness.Body() { public void run() { presentQueryCheck(); } });
        test("thời gian generate/execute p=20 (in ra, không assert)",
             new TestHarness.Body() { public void run() { timingLarge(impl); } });
    }

    // ------------------------------------------------------------------ //

    /** Kiểm tra đầy đủ một cấu hình (a)(c)(d)(e). */
    static void one(Impl impl, double load, int steps) {
        Workload w = Workload.generate(P, load, steps, SEED, 20_000);

        // (a) pool không trùng, absentQueries không giao pool
        checkNodup(w);

        // (b) generate hai lần cùng seed => y hệt
        // (kiểm trong determinism(); ở đây tin tưởng)

        // (d) load + execute
        HashIndex t = impl.f.create(P);
        int fullLoad = w.load(t);
        eq(0, fullLoad, "nạp dữ liệu ban đầu phải không FULL");

        int needed = (steps <= 0) ? 0 : (int) ((steps + BLOCK_CYC - 1) / BLOCK_CYC);
        long[] blockNanos = new long[needed == 0 ? 1 : needed];
        Workload.Result r = w.execute(t, BLOCK_CYC, blockNanos);

        eq(0, r.fullCount, "put trong workload không được FULL");
        eq(w.n, t.size(), "size sau workload == N");
        check(w.verify(t), "verify() sau workload phải true");

        if (steps > 0) {
            eq(needed, r.blocks, "số khối = ceil(steps/blockCycles)");
            for (int i = 0; i < needed; i++) {
                check(blockNanos[i] > 0, "blockNanos[" + i + "] phải > 0");
            }
        }

        // (e) ControlTable: checksum get presentQueries & absentQueries giống nhau
        HashIndex ctl = impl.f.create(P);
        ControlTable.fill(ctl, w);

        long pChkT   = QueryBench.timeGets(t,   w.presentQueries()).checksum;
        long pChkCtl = QueryBench.timeGets(ctl, w.presentQueries()).checksum;
        eq(pChkT, pChkCtl, "checksum present: workload == đối chứng");

        long aChkT   = QueryBench.timeGets(t,   w.absentQueries()).checksum;
        long aChkCtl = QueryBench.timeGets(ctl, w.absentQueries()).checksum;
        eq(aChkT, aChkCtl, "checksum absent: workload == đối chứng");
    }

    /** (f) steps=0: execute trả ops=0, blocks=0. */
    static void zeroSteps(Impl impl, double load) {
        Workload w = Workload.generate(P, load, 0, SEED, 100);
        HashIndex t = impl.f.create(P);
        w.load(t);
        Workload.Result r = w.execute(t, BLOCK_CYC, null);
        eq(0L,  r.ops,    "ops phải = 0 khi steps=0");
        eq(0,   r.blocks, "blocks phải = 0 khi steps=0");
        eq(0,   r.fullCount, "fullCount phải = 0 khi steps=0");
        check(w.verify(t), "verify sau steps=0");
    }

    /** (b) tính tất định. */
    static void determinism() {
        Workload w1a = Workload.generate(P, 0.5, M, SEED,     500);
        Workload w1b = Workload.generate(P, 0.5, M, SEED,     500);
        Workload w2  = Workload.generate(P, 0.5, M, SEED + 1, 500);

        // Cùng seed => pool y hệt
        check(Arrays.equals(w1a.pool(), w1b.pool()), "pool cùng seed phải giống nhau");
        check(Arrays.equals(w1a.presentQueries(), w1b.presentQueries()),
              "presentQueries cùng seed phải giống nhau");
        check(Arrays.equals(w1a.absentQueries(), w1b.absentQueries()),
              "absentQueries cùng seed phải giống nhau");

        // Seed khác => pool khác (xác suất va chạm cực thấp)
        check(!Arrays.equals(w1a.pool(), w2.pool()), "pool seed khác phải khác");
    }

    /** (a) và (c). */
    static void checkNodup(Workload w) {
        String[] pool = w.pool();
        // pool không có phần tử trùng
        Set<String> poolSet = new HashSet<String>(Arrays.asList(pool));
        eq(pool.length, poolSet.size(), "pool không được có phần tử trùng");

        // absentQueries không giao pool
        for (String k : w.absentQueries()) {
            check(!poolSet.contains(k), "absentQueries không được nằm trong pool: " + k);
        }

        // (c) presentQueries: mọi phần tử là khóa sống (nằm trong liveKeys)
        //     và không có object nào trùng tham chiếu với pool
        Set<String> liveSet = new HashSet<String>(Arrays.asList(w.liveKeys()));
        IdentityHashMap<String, Boolean> poolIdentity = new IdentityHashMap<String, Boolean>();
        for (String s : pool) poolIdentity.put(s, Boolean.TRUE);

        for (String pq : w.presentQueries()) {
            check(liveSet.contains(pq),
                  "presentQuery phải là khóa sống: " + pq);
            check(!poolIdentity.containsKey(pq),
                  "presentQuery không được là cùng object với phần tử pool");
        }
    }

    /** (c) kiểm riêng identity. */
    static void presentQueryCheck() {
        Workload w = Workload.generate(P, 0.5, M, SEED, 1000);
        checkNodup(w);
    }

    /** Đo thời gian generate/execute p=20 (in ra, không assert). */
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
