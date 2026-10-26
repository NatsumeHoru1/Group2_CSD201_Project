package hashbench.test;

import static hashbench.HashIndex.*;
import static hashbench.test.TestHarness.check;
import static hashbench.test.TestHarness.eq;
import static hashbench.test.TestHarness.test;

import hashbench.HashIndex;
import hashbench.HashStats;
import hashbench.test.Registry.Impl;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SplittableRandom;

/**
 * Ca kiểm thử trên bảng nhỏ M = 16 (p = 4), chạy cho cả 6 cấu hình.
 * Đây là các ca của TV4; ÁNH XẠ sang "6 ca bắt buộc" của đề: xem TV4_KE_HOACH.md mục 4.3.
 */
public final class CoreCases {
    static final int P = 4, M = 16;
    static final long SEED = 20260001L;

    private CoreCases() {}

    public static void run(final Impl impl) {
        test("cơ bản: put/get/update/remove", new TestHarness.Body() { public void run() { basic(impl); } });
        test("put(value = Integer.MIN_VALUE) ném IllegalArgumentException", new TestHarness.Body() { public void run() { minValue(impl); } });
        test("bảng đầy: put khóa mới trả FULL, không đổi dữ liệu; update vẫn được", new TestHarness.Body() { public void run() { full(impl); } });
        test("bảng đầy: get/remove khóa vắng mặt phải dừng (giới hạn dò M ô)", new TestHarness.Body() { public void run() { fullAbsentTerminates(impl); } });
        test("cụm quấn vòng M-1 -> 0: xóa từng phần tử (2 thứ tự chèn)", new TestHarness.Body() { public void run() { wrapAround(impl); } });
        test("xóa-thêm liên tục (tải 8/16 và 14/16) khớp oracle", new TestHarness.Body() { public void run() { churn(impl, 8); churn(impl, 14); } });
        test("Hashing (TV2) khớp bản băm độc lập và hằng Fibonacci", new TestHarness.Body() { public void run() { hashingCrossCheck(); } });
        test("thống kê (HashStats): bất biến TOMB/EMPTY/rebuild", new TestHarness.Body() { public void run() { statsInvariants(impl); } });
    }

    // ---------------------------------------------------------------- các ca
    static void basic(Impl impl) {
        HashIndex t = impl.f.create(P);
        eq(M, t.capacity(), "capacity");
        eq(0, t.size(), "size ban đầu");
        eq(NOT_FOUND, t.get(5), "get khóa vắng");
        eq(INSERTED, t.put(5, 50), "put mới");
        eq(50, t.get(5), "get sau put");
        eq(UPDATED, t.put(5, 51), "put cập nhật");
        eq(51, t.get(5), "get sau update");
        eq(1, t.size(), "size sau update không đổi");
        check(!t.remove(7), "remove khóa vắng phải false");
        check(t.remove(5), "remove khóa có phải true");
        eq(NOT_FOUND, t.get(5), "get sau remove");
        eq(0, t.size(), "size sau remove");
        check(!t.remove(5), "remove lần hai phải false");
        eq(INSERTED, t.put(5, 52), "thêm lại sau remove");
        eq(52, t.get(5), "get sau thêm lại");
        TestUtil.validate(t);
    }

    static void minValue(Impl impl) {
        HashIndex t = impl.f.create(P);
        try {
            t.put(1, NOT_FOUND);
            throw new AssertionError("put(MIN_VALUE) phải ném IllegalArgumentException");
        } catch (IllegalArgumentException expected) { /* đúng */ }
        eq(0, t.size(), "size sau put bị từ chối");
        eq(NOT_FOUND, t.get(1), "khóa không được lưu");
        eq(7, t.put(2, 7) == INSERTED ? 7 : -1, "bảng vẫn dùng được sau ngoại lệ");
    }

    static void full(Impl impl) {
        HashIndex t = impl.f.create(P);
        SplittableRandom rng = new SplittableRandom(SEED);
        Map<Long, Integer> ref = new HashMap<Long, Integer>();
        while (ref.size() < M) {
            long k = rng.nextLong() & Long.MAX_VALUE;
            if (ref.containsKey(k)) continue;
            int v = TestUtil.valueOf(k);
            eq(INSERTED, t.put(k, v), "chèn tới đầy");
            ref.put(k, v);
        }
        eq(M, t.size(), "size == M");
        long extra;
        do { extra = rng.nextLong() & Long.MAX_VALUE; } while (ref.containsKey(extra));
        eq(FULL, t.put(extra, 123), "put khóa mới khi đầy");
        eq(M, t.size(), "size không đổi sau FULL");
        eq(NOT_FOUND, t.get(extra), "khóa bị từ chối không được lưu");
        for (Map.Entry<Long, Integer> e : ref.entrySet())
            eq(e.getValue(), t.get(e.getKey()), "dữ liệu cũ phải nguyên vẹn sau FULL (key " + e.getKey() + ")");
        Long any = ref.keySet().iterator().next();
        eq(UPDATED, t.put(any, 999), "update khi đầy phải được (không phải FULL)");
        eq(999, t.get(any), "giá trị sau update khi đầy");
        TestUtil.validate(t);
        check(t.remove(any), "remove khi đầy");
        eq(INSERTED, t.put(extra, 124), "sau khi xóa 1, put khóa mới phải được");
        eq(124, t.get(extra), "get khóa vừa chèn");
        TestUtil.validate(t);
    }

    static void fullAbsentTerminates(final Impl impl) {
        TestUtil.withTimeout("get/remove trên bảng đầy", 5000, new Runnable() {
            public void run() {
                HashIndex t = impl.f.create(P);
                Set<Long> used = new HashSet<Long>();
                for (int i = 0; i < M; i++) { long k = i; used.add(k); eq(INSERTED, t.put(k, i + 1), "chèn"); }
                long absent = 1_000_003L;
                eq(NOT_FOUND, t.get(absent), "get vắng trên bảng đầy");
                check(!t.remove(absent), "remove vắng trên bảng đầy");
                eq(M, t.size(), "size");
            }
        });
    }

    /** Cụm bắt đầu ở ô M-1 và quấn sang ô 0..3; xóa từng phần tử rồi kiểm các phần tử sau nó. */
    static void wrapAround(Impl impl) {
        for (int order = 0; order < 2; order++) {
            for (int rm = 0; rm < 5; rm++) {
                Set<Long> used = new HashSet<Long>();
                long[] a = TestUtil.keysWithHome(P, M - 1, 3, used);   // home = 15
                long[] b = TestUtil.keysWithHome(P, 0, 2, used);       // home = 0
                long[] ks = { a[0], a[1], a[2], b[0], b[1] };
                int[] idx = { 0, 1, 2, 3, 4 };
                if (order == 1) idx = new int[] { 4, 3, 2, 1, 0 };
                HashIndex t = impl.f.create(P);
                for (int i : idx) eq(INSERTED, t.put(ks[i], 100 + i), "chèn cụm quấn vòng");
                TestUtil.validate(t);
                for (int i = 0; i < 5; i++) eq(100 + i, t.get(ks[i]), "get trước khi xóa");

                check(t.remove(ks[rm]), "remove phần tử " + rm + " (order " + order + ")");
                TestUtil.validate(t);
                eq(4, t.size(), "size sau xóa");
                for (int i = 0; i < 5; i++) {
                    int exp = i == rm ? NOT_FOUND : 100 + i;
                    eq(exp, t.get(ks[i]), "get phần tử " + i + " sau khi xóa " + rm + " (order " + order + ")");
                }
                eq(INSERTED, t.put(ks[rm], 500 + rm), "chèn lại phần tử đã xóa");
                for (int i = 0; i < 5; i++) eq(i == rm ? 500 + rm : 100 + i, t.get(ks[i]), "get sau chèn lại");
                for (int i = 4; i >= 0; i--) check(t.remove(ks[i]), "xóa dần " + i);
                eq(0, t.size(), "bảng rỗng");
                for (int i = 0; i < 5; i++) eq(NOT_FOUND, t.get(ks[i]), "rỗng");
                TestUtil.validate(t);
            }
        }
    }

    static void churn(Impl impl, int live) {
        HashIndex t = impl.f.create(P);
        SplittableRandom rng = new SplittableRandom(SEED + live);
        Map<Long, Integer> ref = new HashMap<Long, Integer>();
        List<Long> keys = new ArrayList<Long>();
        for (int i = 0; i < live; i++) {
            long k = rng.nextLong() & Long.MAX_VALUE;
            if (ref.containsKey(k)) { i--; continue; }
            ref.put(k, TestUtil.valueOf(k)); keys.add(k);
            eq(INSERTED, t.put(k, TestUtil.valueOf(k)), "nạp ban đầu");
        }
        for (int step = 0; step < 20000; step++) {
            int j = rng.nextInt(keys.size());
            long victim = keys.get(j);
            check(t.remove(victim), "remove khóa đang sống (bước " + step + ")");
            ref.remove(victim);
            long nk;
            do { nk = rng.nextLong() & Long.MAX_VALUE; } while (ref.containsKey(nk) || nk == victim);
            eq(INSERTED, t.put(nk, TestUtil.valueOf(nk)), "put khóa mới (bước " + step + ")");
            ref.put(nk, TestUtil.valueOf(nk));
            keys.set(j, nk);
            eq(ref.size(), t.size(), "size (bước " + step + ")");
            TestUtil.validate(t);
            if (step % 250 == 0) {
                for (Map.Entry<Long, Integer> e : ref.entrySet())
                    eq(e.getValue(), t.get(e.getKey()), "get sống (bước " + step + ")");
                eq(NOT_FOUND, t.get(victim), "get khóa đã xóa (bước " + step + ")");
                for (int q = 0; q < 8; q++) {
                    long ab;
                    do { ab = rng.nextLong() & Long.MAX_VALUE; } while (ref.containsKey(ab));
                    eq(NOT_FOUND, t.get(ab), "get vắng (bước " + step + ")");
                }
            }
        }
    }

    static void hashingCrossCheck() {
        check(hashbench.Hashing.verifyConstant(), "hằng Fibonacci sai");
        eq(TestUtil.FIB, hashbench.Hashing.FIBONACCI_CONSTANT, "hằng khác bản độc lập");
        SplittableRandom r = new SplittableRandom(SEED);
        for (int p = 1; p <= 30; p += 3)
            for (int i = 0; i < 2000; i++) {
                long k = r.nextLong() & Long.MAX_VALUE;
                int h = hashbench.Hashing.hash(k, p);
                eq(TestUtil.homeOf(k, p), h, "hash(" + k + "," + p + ")");
                check(h >= 0 && h < (1 << p), "home ngoài [0, M)");
            }
    }

    static void statsInvariants(Impl impl) {
        HashIndex t = impl.f.create(P);
        if (!(t instanceof HashStats)) return;               // chưa implement HashStats: bỏ qua
        HashStats st = (HashStats) t;
        churn(impl, 8);                                       // kiểm khi chạy thật bên dưới
        // chạy lại churn trên chính bảng t để đọc thống kê
        SplittableRandom rng = new SplittableRandom(SEED);
        List<Long> keys = new ArrayList<Long>();
        Set<Long> seen = new HashSet<Long>();
        for (int i = 0; i < 8; i++) { long k = rng.nextLong() & Long.MAX_VALUE; if (!seen.add(k)) { i--; continue; } keys.add(k); t.put(k, 1); }
        for (int s = 0; s < 5000; s++) {
            int j = rng.nextInt(keys.size());
            t.remove(keys.get(j));
            long nk; do { nk = rng.nextLong() & Long.MAX_VALUE; } while (!seen.add(nk));
            t.put(nk, 1); keys.set(j, nk);
            if (impl.tomb) eq(M, st.emptyCount() + st.tombCount() + t.size(), "EMPTY + TOMB + size == M (bước " + s + ")");
        }
        if (impl.open && !impl.tomb) eq(0, st.tombCount(), "cài đặt không tombstone phải có TOMB = 0");
        if (impl.tomb && impl.rebuild) check(st.rebuildCount() > 0, "có rebuild thì rebuildCount phải > 0 sau xóa-thêm dài");
        if (impl.tomb && !impl.rebuild) eq(0, st.rebuildCount(), "không rebuild thì rebuildCount == 0");
    }
}
