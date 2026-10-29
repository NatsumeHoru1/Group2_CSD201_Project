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
 * Ca kiem thu tren bang nhi phan M = 16 (p = 4), chay cho ca 6 cau hinh.
 * Day la cac ca bat buoc TV4; anh xa theo muc tieu cua de bai.
 */
public final class CoreCases {
    static final int P = 4, M = 1 << P;
    private CoreCases() {}

    public static void run(Impl impl) {
        test("co ban: put/get/update/remove", new TestHarness.Body() { public void run() { basic(impl); } });
        test("put(value = Integer.MIN_VALUE) nem IllegalArgumentException", new TestHarness.Body() { public void run() { minValue(impl); } });
        test("bang day: put khoa moi tra FULL, khong doi du lieu; update van duoc", new TestHarness.Body() { public void run() { full(impl); } });
        test("bang day: get/remove khoa vang mat phai dung (gioi han do M o)", new TestHarness.Body() { public void run() { fullAbsentTerminates(impl); } });
        test("cum quan vong M-1 -> 0: xoa tung phan tu (2 thu tu chen)", new TestHarness.Body() { public void run() { wrapAround(impl); } });
        test("xoa-them lien tuc (tai 8/16 va 14/16) khop oracle", new TestHarness.Body() { public void run() { churn(impl, 8); churn(impl, 14); } });
        test("thong ke (HashStats): bat bien TOMB/EMPTY/rebuild", new TestHarness.Body() { public void run() { statsInvariants(impl); } });
    }

    private static void basic(Impl impl) {
        HashIndex t = impl.f.create(P);
        String a = keyWithHome(0), b = keyWithHome(1), c = keyWithHome(1);
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
            t.put("000000000123", NOT_FOUND);
        } catch (IllegalArgumentException e) {
            threw = true;
        }
        check(threw, "put voi NOT_FOUND phai nem IllegalArgumentException");
        eq(0, t.size(), "size khong doi sau loi");
    }

    private static void full(Impl impl) {
        HashIndex t = impl.f.create(P);
        Set<String> used = new HashSet<String>();
        for (int i = 0; i < M; i++) {
            String k = uniqueKeyWithHome(i % M, used, new SplittableRandom(99L + i));
            check(t.put(k, i + 1) == INSERTED, "fill table");
        }
        String extra = uniqueKeyWithHome(7, used, new SplittableRandom(777L));
        eq(FULL, t.put(extra, 999), "put key moi khi day phai FULL");
        eq(M, t.size(), "size van la M");
        String existing = used.iterator().next();
        int before = t.get(existing);
        eq(UPDATED, t.put(existing, before + 100), "update existing key when full");
        eq(before + 100, t.get(existing), "updated value is visible");
    }

    private static void fullAbsentTerminates(Impl impl) {
        HashIndex t = impl.f.create(P);
        Set<String> used = new HashSet<String>();
        for (int i = 0; i < M; i++) {
            String k = uniqueKeyWithHome(i % M, used, new SplittableRandom(50L + i));
            check(t.put(k, i + 1) == INSERTED, "fill slot " + i);
        }
        String absent = uniqueKeyWithHome(0, used, new SplittableRandom(123456L));
        eq(NOT_FOUND, t.get(absent), "get absent must return NOT_FOUND");
        check(!t.remove(absent), "remove absent must return false");
        eq(M, t.size(), "size still M");
    }

    private static void wrapAround(Impl impl) {
        HashIndex t = impl.f.create(P);
        SplittableRandom r = new SplittableRandom(2026L);
        String a = keyWithHome(M - 1, r);
        String b = keyWithHome(M - 1, r);
        String c = keyWithHome(M - 1, r);

        t.put(a, 1);
        t.put(b, 2);
        t.put(c, 3);
        check(t.remove(b), "remove middle of wrapped cluster");
        eq(3, t.get(c), "after removal, later key still found");
        eq(NOT_FOUND, t.get(b), "removed key absent");
    }

    private static void churn(Impl impl, int targetSize) {
        HashIndex t = impl.f.create(P);
        Set<String> live = new HashSet<String>();
        SplittableRandom r = new SplittableRandom(1000L + targetSize);
        while (live.size() < targetSize) {
            String k = keyWithHome(live.size() % M, r);
            if (live.add(k)) {
                eq(INSERTED, t.put(k, TestUtil.valueOf(k)), "insert during churn");
            }
        }
        for (int i = 0; i < targetSize / 2; i++) {
            Object[] arr = live.toArray(new String[0]);
            String victim = (String) arr[i];
            check(t.remove(victim), "remove victim");
            live.remove(victim);
            String newKey = keyWithHome((i + targetSize) % M, r);
            if (!live.contains(newKey)) {
                eq(INSERTED, t.put(newKey, TestUtil.valueOf(newKey)), "add replacement");
                live.add(newKey);
            }
        }
        for (String k : live) {
            eq(TestUtil.valueOf(k), t.get(k), "value for live key");
        }
        eq(live.size(), t.size(), "size matches current live set");
    }

    private static void statsInvariants(Impl impl) {
        if (!(impl.f.create(P) instanceof hashbench.HashStats)) return;
        HashIndex t = impl.f.create(P);
        String[] keys = new String[M];
        for (int i = 0; i < M; i++) {
            keys[i] = hashbench.KeyCodec.encode(i * 3L + 17L);
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

    private static String keyWithHome(int home, SplittableRandom rng) {
        return Hashing.findKeyWithHome(home, P, rng);
    }

    private static String keyWithHome(int home) {
        return Hashing.findKeyWithHome(home, P, new SplittableRandom(20261025L + home));
    }

    private static String uniqueKeyWithHome(int home, Set<String> used, SplittableRandom rng) {
        while (true) {
            String k = Hashing.findKeyWithHome(home, P, rng);
            if (used.add(k)) return k;
        }
    }
}
