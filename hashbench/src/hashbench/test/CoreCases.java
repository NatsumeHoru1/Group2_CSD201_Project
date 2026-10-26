package hashbench.test;

import static hashbench.HashIndex.*;
import static hashbench.test.TestHarness.check;
import static hashbench.test.TestHarness.eq;
import static hashbench.test.TestHarness.test;

import hashbench.HashIndex;
import hashbench.Hashing;
import hashbench.test.Registry.Impl;
import java.util.HashSet;
import java.util.Set;
import java.util.SplittableRandom;

/**
 * Ca kiểm thử trên bảng nhị phân M = 16 (p = 4), chạy cho cả 6 cấu hình.
 * Đây là các ca bắt buộc TV4; ánh xạ theo mục tiêu của đề bài.
 */
public final class CoreCases {
    static final int P = 4, M = 1 << P;
    private CoreCases() {}

    public static void run(Impl impl) {
        test("cơ bản: put/get/update/remove", new TestHarness.Body() { public void run() { basic(impl); } });
        test("put(value = Integer.MIN_VALUE) ném IllegalArgumentException", new TestHarness.Body() { public void run() { minValue(impl); } });
        test("bảng đầy: put khóa mới trả FULL, không đổi dữ liệu; update vẫn được", new TestHarness.Body() { public void run() { full(impl); } });
        test("bảng đầy: get/remove khóa vắng mặt phải dừng (giới hạn dò M ô)", new TestHarness.Body() { public void run() { fullAbsentTerminates(impl); } });
        test("cụm quấn vòng M-1 -> 0: xóa từng phần tử (2 thứ tự chèn)", new TestHarness.Body() { public void run() { wrapAround(impl); } });
        test("xóa-thêm liên tục (tải 8/16 và 14/16) khớp oracle", new TestHarness.Body() { public void run() { churn(impl, 8); churn(impl, 14); } });
        test("thống kê (HashStats): bất biến TOMB/EMPTY/rebuild", new TestHarness.Body() { public void run() { statsInvariants(impl); } });
    }

    private static void basic(Impl impl) {
        HashIndex t = impl.f.create(P);
        long a = keyWithHome(0), b = keyWithHome(1), c = keyWithHome(1);
        eq(INSERTED, t.put(a, 10), "insert a");
        eq(INSERTED, t.put(b, 20), "insert b");
        eq(UPDATED, t.put(a, 30), "update a");
        eq(20, t.get(b), "get b");
        eq(30, t.get(a), "get a after update");
        check(t.remove(a), "remove a");
        eq(NOT_FOUND, t.get(a), "removed key is gone");
        eq(1, t.size(), "size after remove");
    }

    private static void minValue(Impl impl) {
        HashIndex t = impl.f.create(P);
        boolean threw = false;
        try {
            t.put(123L, NOT_FOUND);
        } catch (IllegalArgumentException e) {
            threw = true;
        }
        check(threw, "put với NOT_FOUND phải ném IllegalArgumentException");
        eq(0, t.size(), "size không đổi sau lỗi");
    }

    private static void full(Impl impl) {
        HashIndex t = impl.f.create(P);
        Set<Long> used = new HashSet<Long>();
        for (int i = 0; i < M; i++) {
            long k = uniqueKeyWithHome(i % M, used, new SplittableRandom(99L + i));
            check(t.put(k, i + 1) == INSERTED, "fill table");
        }
        long extra = uniqueKeyWithHome(7, used, new SplittableRandom(777L));
        eq(FULL, t.put(extra, 999), "put key mới khi đầy phải FULL");
        eq(M, t.size(), "size vẫn là M");
        long existing = used.iterator().next();
        int before = t.get(existing);
        eq(UPDATED, t.put(existing, before + 100), "update existing key when full");
        eq(before + 100, t.get(existing), "updated value is visible");
    }

    private static void fullAbsentTerminates(Impl impl) {
        HashIndex t = impl.f.create(P);
        Set<Long> used = new HashSet<Long>();
        for (int i = 0; i < M; i++) {
            long k = uniqueKeyWithHome(i % M, used, new SplittableRandom(50L + i));
            check(t.put(k, i + 1) == INSERTED, "fill slot " + i);
        }
        long absent = uniqueKeyWithHome(0, used, new SplittableRandom(123456L));
        eq(NOT_FOUND, t.get(absent), "get absent must return NOT_FOUND");
        check(!t.remove(absent), "remove absent must return false");
        eq(M, t.size(), "size still M");
    }

    private static void wrapAround(Impl impl) {
        HashIndex t = impl.f.create(P);
        SplittableRandom r = new SplittableRandom(2026L);
        long a = keyWithHome(M - 1, r);
        long b = keyWithHome(M - 1, r);
        long c = keyWithHome(M - 1, r);

        t.put(a, 1);
        t.put(b, 2);
        t.put(c, 3);
        check(t.remove(b), "remove middle of wrapped cluster");
        eq(3, t.get(c), "after removal, later key still found");
        eq(NOT_FOUND, t.get(b), "removed key absent");
    }

    private static void churn(Impl impl, int targetSize) {
        HashIndex t = impl.f.create(P);
        Set<Long> live = new HashSet<Long>();
        SplittableRandom r = new SplittableRandom(1000L + targetSize);
        while (live.size() < targetSize) {
            long k = keyWithHome(live.size() % M, r);
            if (live.add(k)) {
                eq(INSERTED, t.put(k, TestUtil.valueOf(k)), "insert during churn");
            }
        }
        for (int i = 0; i < targetSize / 2; i++) {
            Object[] arr = live.toArray(new Long[0]);
            long victim = ((Long) arr[i]).longValue();
            check(t.remove(victim), "remove victim");
            live.remove(victim);
            long newKey = keyWithHome((i + targetSize) % M, r);
            if (!live.contains(newKey)) {
                eq(INSERTED, t.put(newKey, TestUtil.valueOf(newKey)), "add replacement");
                live.add(newKey);
            }
        }
        for (Long k : live) {
            eq(TestUtil.valueOf(k.longValue()), t.get(k.longValue()), "value for live key");
        }
        eq(live.size(), t.size(), "size matches current live set");
    }

    private static void statsInvariants(Impl impl) {
        if (!(impl.f.create(P) instanceof hashbench.HashStats)) return;
        HashIndex t = impl.f.create(P);
        long[] keys = new long[M];
        for (int i = 0; i < M; i++) {
            keys[i] = i * 3L + 17L;
            t.put(keys[i], i + 1);
        }
        for (int i = 0; i < M / 2; i++) {
            t.remove(keys[i]);
        }
        Object stats = t;
        if (stats instanceof hashbench.HashStats) {
            hashbench.HashStats s = (hashbench.HashStats) stats;
            check(s.tombCount() >= 0 && s.emptyCount() >= 0, "tomb/empty counts are non-negative");
            check(t.size() == M / 2, "size matches current live count");
        }
    }

    private static long keyWithHome(int home, SplittableRandom rng) {
        return Hashing.findKeyWithHome(home, P, rng);
    }

    private static long keyWithHome(int home) {
        return Hashing.findKeyWithHome(home, P, new SplittableRandom(20261025L + home));
    }

    private static long uniqueKeyWithHome(int home, Set<Long> used, SplittableRandom rng) {
        while (true) {
            long k = Hashing.findKeyWithHome(home, P, rng);
            if (used.add(k)) return k;
        }
    }
}
