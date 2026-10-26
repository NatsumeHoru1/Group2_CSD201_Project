package hashbench.test;

import static hashbench.test.TestHarness.check;
import static hashbench.test.TestHarness.eq;
import static hashbench.test.TestHarness.test;

import hashbench.HashIndex;
import hashbench.test.Registry.Impl;
import hashbench.workload.ControlTable;
import hashbench.workload.QueryBench;
import hashbench.workload.Workload;

/** Kiểm tra bản thân workload + bảng đối chứng trên từng cài đặt (p = 12 cho nhanh). */
public final class WorkloadTests {
    static final int P = 12, M = 1 << P, BLOCK = 1000;
    static final long SEED = 20260003L;

    private WorkloadTests() {}

    public static void run(final Impl impl) {
        test("workload tải 0,5, C = 2M, khối 10^3: đúng và khớp bảng đối chứng", new TestHarness.Body() { public void run() { one(impl, 0.5, 2 * M); } });
        test("workload tải 0,8, C = M: đúng và khớp bảng đối chứng", new TestHarness.Body() { public void run() { one(impl, 0.8, M); } });
    }

    static void one(Impl impl, double load, int steps) {
        Workload w = Workload.generate(P, load, steps, SEED, 20000, 20000);
        HashIndex t = impl.f.create(P);
        eq(0, w.load(t), "nạp ban đầu không được FULL");
        eq(w.n, t.size(), "size sau nạp");
        Workload.Result r = w.execute(t, BLOCK, null);
        eq(0, r.fullCount, "put trong workload không được FULL");
        eq(0, r.badRemoves, "remove khóa đang sống phải true");
        eq((2L * steps + BLOCK - 1) / BLOCK, r.blocks, "số khối");
        eq(w.n, t.size(), "size sau workload");
        TestUtil.validate(t);
        check(w.verify(t), "dữ liệu sau workload sai");

        HashIndex ctl = ControlTable.build(impl.f, P, w);
        eq(w.n, ctl.size(), "size bảng đối chứng");
        eq(QueryBench.timeGets(ctl, w.presentQueries).checksum, QueryBench.timeGets(t, w.presentQueries).checksum, "checksum truy vấn có mặt (output_check)");
        long absentSum = QueryBench.timeGets(t, w.absentQueries).checksum;
        eq(QueryBench.timeGets(ctl, w.absentQueries).checksum, absentSum, "checksum truy vấn vắng mặt");
        eq(w.absentQueries.length, absentSum, "output_check vắng mặt = số truy vấn (mọi get phải NOT_FOUND)");
    }
}
