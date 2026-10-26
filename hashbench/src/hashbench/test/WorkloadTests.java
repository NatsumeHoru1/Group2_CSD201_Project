package hashbench.test;

import static hashbench.test.TestHarness.check;
import static hashbench.test.TestHarness.eq;
import static hashbench.test.TestHarness.test;

import hashbench.HashIndex;
import hashbench.workload.ControlTable;
import hashbench.workload.QueryBench;
import hashbench.workload.Workload;
import hashbench.test.Registry.Impl;

/** Kiểm tra bản thân workload + bảng đối chứng trên từng cài đặt (p = 12 cho nhanh). */
public final class WorkloadTests {
    private static final int P = 12;
    private WorkloadTests() {}

    public static void run(Impl impl) {
        test("workload tải 0,5, C = 2M, khối 10^3: đúng và khớp bảng đối chứng", new TestHarness.Body() { public void run() { one(impl, 0.5, 2 * M); } });
        test("workload tải 0,8, C = M: đúng và khớp bảng đối chứng", new TestHarness.Body() { public void run() { one(impl, 0.8, M); } });
    }

    private static final int M = 1 << 12;

    static void one(Impl impl, double load, int steps) {
        Workload w = Workload.generate(P, load, steps, 20261101L, 20_000, 20_000);
        HashIndex t = impl.f.create(P);
        int fullCount = w.load(t);
        eq(0, fullCount, "nạp dữ liệu ban đầu phải không FULL");
        Workload.Result r = w.execute(t, 1000, null);
        eq(0, r.fullCount, "put trong workload không được FULL");
        eq(w.n, t.size(), "size sau workload");
        check(w.verify(t), "dữ liệu sau workload sai");

        HashIndex ctl = ControlTable.build(impl.f, P, w);
        eq(QueryBench.timeGets(ctl, w.presentQueries).checksum, QueryBench.timeGets(t, w.presentQueries).checksum, "checksum truy vấn có mặt (output_check)");
        long absentSum = QueryBench.timeGets(t, w.absentQueries).checksum;
        eq(QueryBench.timeGets(ctl, w.absentQueries).checksum, absentSum, "checksum truy vấn vắng mặt");
    }
}
