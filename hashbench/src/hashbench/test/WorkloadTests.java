package hashbench.test;

import static hashbench.test.TestHarness.check;
import static hashbench.test.TestHarness.eq;
import static hashbench.test.TestHarness.test;

import hashbench.HashIndex;
import hashbench.LongSet;
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
        test("workload có lịch khóa duy nhất và đo block đúng", new TestHarness.Body() { public void run() { blocksAndUniqueKeys(impl); } });
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

    private static void blocksAndUniqueKeys(Impl impl) {
        Workload w = Workload.generate(4, 0.5, 13, 17L, 3, 5);
        LongSet unique = new LongSet(w.initialKeys.length + w.insertKeys.length + w.absentQueries.length);
        for (long key : w.initialKeys) check(unique.add(key), "khóa ban đầu bị trùng");
        for (long key : w.insertKeys) check(unique.add(key), "khóa thêm bị trùng");
        for (long key : w.absentQueries) check(unique.add(key), "khóa vắng mặt bị trùng/đụng pool");

        HashIndex table = impl.f.create(4);
        eq(0, w.load(table), "nạp workload kiểm thử");
        final int[] expectedOps = {10, 10, 6};
        final int[] blockCount = {0};
        Workload.Result result = w.execute(table, 5, new Workload.BlockListener() {
            public void onBlock(int blockNo, int ops, long elapsedNanos) {
                eq(blockCount[0], blockNo, "thứ tự block");
                eq(expectedOps[blockCount[0]], ops, "số thao tác trong block");
                check(elapsedNanos >= 0L, "thời gian block phải không âm");
                blockCount[0]++;
            }
        });
        eq(26L, result.ops, "số thao tác workload");
        eq(3, result.blocks, "số block workload");
        eq(3, blockCount[0], "số callback block");
        check(w.verify(table), "workload nhỏ sau khi chạy phải hợp lệ");
        for (long query : w.presentQueries) {
            check(table.get(query) != HashIndex.NOT_FOUND, "present query phải còn sống");
        }

        HashIndex control = ControlTable.build(impl.f, 4, w);
        eq(QueryBench.timeGets(table, w.presentQueries).checksum,
                QueryBench.timeGets(control, w.presentQueries).checksum,
                "checksum present với bảng đối chứng");
    }
}
