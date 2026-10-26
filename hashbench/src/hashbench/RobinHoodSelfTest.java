package hashbench;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.SplittableRandom;

/**
 * Tu kiem tra cho RobinHoodTable (rh va rh-noearly). Phu trach: TV5.
 * Chay: java -cp hashbench\out hashbench.RobinHoodSelfTest
 * Gom: oracle so voi HashMap, cac ca tren bang M = 16, dem so o da xem, HashIndexFactory.
 * Neu chu ky HashIndexFactory.create doi (them tau) thi sua phan factory() o cuoi file.
 */
public final class RobinHoodSelfTest {
    private static final int CASE_P = 4; // M = 16
    private static int failures = 0;

    private RobinHoodSelfTest() {
    }

    private static void check(boolean ok, String name) {
        if (!ok) {
            failures++;
            System.out.println("FAIL " + name);
        }
    }

    private static void run(String name, Runnable body) {
        try {
            body.run();
        } catch (RuntimeException e) {
            failures++;
            System.out.println("FAIL " + name + ": " + e);
        }
    }

    /** Sinh khoa co o nha cho truoc, khong trung nhau (dung Hashing cua TV2). */
    private static final class KeyMaker {
        private final int p;
        private final SplittableRandom rng = new SplittableRandom(20261025L);
        private final Set<String> used = new HashSet<String>();

        KeyMaker(int p) {
            this.p = p;
        }

        String withHome(int home) {
            while (true) {
                String k = Hashing.findKeyWithHome(home, p, rng);
                if (used.add(k)) {
                    return k;
                }
            }
        }
    }

    private static String[] randomKeys(int n, long seed) {
        return Generator.generatePresentKeys(seed, n);
    }

    private static String slot(RobinHoodTable t, int i) {
        return t.dump().split("\n")[i];
    }

    private static String occupied(int i, String key, int dib) {
        return i + ": key=" + key + " dib=" + dib;
    }

    // ---------- Oracle 10^6 thao tac so voi HashMap ----------
    private static void oracle(int p, boolean early, int domain, long seed, int ops) {
        int m = 1 << p;
        String[] pool = randomKeys(domain, seed + 1000);
        RobinHoodTable t = new RobinHoodTable(p, early);
        Map<String, Integer> map = new HashMap<String, Integer>();
        SplittableRandom r = new SplittableRandom(seed);
        String tag = "oracle early=" + early + " domain=" + domain + " seed=" + seed;
        for (int i = 0; i < ops; i++) {
            String k = pool[r.nextInt(domain)];
            int op = r.nextInt(100);
            if (op < 40) {
                int v = r.nextInt();
                if (v == HashIndex.NOT_FOUND) {
                    v = 0;
                }
                int expected = map.containsKey(k) ? HashIndex.UPDATED
                        : (map.size() >= m ? HashIndex.FULL : HashIndex.INSERTED);
                int got = t.put(k, v);
                if (got != expected) {
                    throw new IllegalStateException(tag + " op " + i + ": put " + got + " != " + expected);
                }
                if (got != HashIndex.FULL) {
                    map.put(k, Integer.valueOf(v));
                }
            } else if (op < 80) {
                Integer e = map.get(k);
                int expected = e == null ? HashIndex.NOT_FOUND : e.intValue();
                int got = t.get(k);
                if (got != expected) {
                    throw new IllegalStateException(tag + " op " + i + ": get " + got + " != " + expected);
                }
            } else {
                boolean expected = map.remove(k) != null;
                boolean got = t.remove(k);
                if (got != expected) {
                    throw new IllegalStateException(tag + " op " + i + ": remove " + got + " != " + expected);
                }
            }
            if (t.size() != map.size()) {
                throw new IllegalStateException(tag + " op " + i + ": size " + t.size() + " != " + map.size());
            }
            if (i % 1000 == 0) {
                t.validate();
            }
        }
        t.validate();
    }

    // ---------- Cac ca tren bang M = 16 ----------
    private static void caseClusterRemove(boolean early) {
        KeyMaker km = new KeyMaker(CASE_P);
        RobinHoodTable t = new RobinHoodTable(CASE_P, early);
        String k1 = km.withHome(5), k2 = km.withHome(5), k3 = km.withHome(5);
        t.put(k1, 1);
        t.put(k2, 2);
        t.put(k3, 3);
        t.validate();
        check(t.remove(k2), "case1 remove middle key");
        t.validate();
        check(t.get(k3) == 3 && t.get(k1) == 1 && t.get(k2) == HashIndex.NOT_FOUND && t.size() == 2,
                "case1 keys around removed key");
    }

    private static void caseUpdate(boolean early) {
        KeyMaker km = new KeyMaker(CASE_P);
        RobinHoodTable t = new RobinHoodTable(CASE_P, early);
        String k = km.withHome(3);
        check(t.put(k, 10) == HashIndex.INSERTED, "case2 first put INSERTED");
        check(t.put(k, 20) == HashIndex.UPDATED, "case2 second put UPDATED");
        check(t.get(k) == 20 && t.size() == 1, "case2 value updated");
        String a = km.withHome(8), b = km.withHome(8);
        t.put(a, 1);
        t.put(b, 2);
        check(t.put(b, 99) == HashIndex.UPDATED, "case2 update key behind a collision");
        check(t.get(b) == 99 && t.get(a) == 1 && t.size() == 3, "case2 values and size");
        t.validate();
    }

    private static void caseRemoveAbsent(boolean early) {
        KeyMaker km = new KeyMaker(CASE_P);
        RobinHoodTable t = new RobinHoodTable(CASE_P, early);
        t.put(km.withHome(5), 1);
        t.put(km.withHome(5), 2);
        t.put(km.withHome(5), 3);
        String absent = km.withHome(5);
        String before = t.dump();
        check(!t.remove(absent), "case3 remove absent returns false");
        check(t.dump().equals(before) && t.size() == 3, "case3 table unchanged");
        t.validate();
    }

    private static void caseFull(boolean early) {
        int m = 1 << CASE_P;
        String[] keys = randomKeys(m + 1, 77L);
        RobinHoodTable t = new RobinHoodTable(CASE_P, early);
        boolean allInserted = true;
        for (int i = 0; i < m; i++) {
            if (t.put(keys[i], i + 1) != HashIndex.INSERTED) {
                allInserted = false;
            }
        }
        t.validate();
        check(allInserted && t.size() == m, "case4 fill table to M keys");
        String before = t.dump();
        check(t.put(keys[m], 777) == HashIndex.FULL, "case4 put new key when full returns FULL");
        check(t.dump().equals(before) && t.size() == m, "case4 data unchanged after FULL");
        check(t.put(keys[3], 555) == HashIndex.UPDATED, "case4 update existing key when full");
        boolean allFound = true;
        for (int i = 0; i < m; i++) {
            int expected = (i == 3) ? 555 : i + 1;
            if (t.get(keys[i]) != expected) {
                allFound = false;
            }
        }
        check(allFound, "case4 all keys found");
        t.validate();
    }

    private static void caseWrapAround(boolean early) {
        KeyMaker km = new KeyMaker(CASE_P);
        RobinHoodTable t = new RobinHoodTable(CASE_P, early);
        String w1 = km.withHome(15), w2 = km.withHome(15), w3 = km.withHome(15);
        t.put(w1, 1);
        t.put(w2, 2);
        t.put(w3, 3);
        t.validate();
        check(slot(t, 15).equals(occupied(15, w1, 0)), "case5 w1 at slot 15");
        check(slot(t, 0).equals(occupied(0, w2, 1)), "case5 w2 wraps to slot 0");
        check(slot(t, 1).equals(occupied(1, w3, 2)), "case5 w3 at slot 1");
        check(t.get(w1) == 1 && t.get(w2) == 2 && t.get(w3) == 3, "case5 all found across wrap");
        check(t.remove(w2), "case5b remove middle of wrapped cluster");
        t.validate();
        check(t.get(w3) == 3, "case5b key after removed one still found");
        check(slot(t, 0).equals(occupied(0, w3, 1)), "case5b w3 shifted back to slot 0");
        check(slot(t, 1).equals("1: -"), "case5b slot 1 empty");
        check(t.remove(w1), "case5 remove first of wrapped cluster");
        t.validate();
        check(slot(t, 15).equals(occupied(15, w3, 0)), "case5 w3 shifted back to slot 15");
        check(t.get(w3) == 3 && t.size() == 1, "case5 w3 found, size 1");
    }

    private static void caseWrapSwap(boolean early) {
        KeyMaker km = new KeyMaker(CASE_P);
        RobinHoodTable t = new RobinHoodTable(CASE_P, early);
        String a = km.withHome(14), c = km.withHome(14), b = km.withHome(15),
                e = km.withHome(14);
        t.put(a, 1);
        t.put(c, 2);
        t.put(b, 3);
        t.validate();
        check(slot(t, 0).equals(occupied(0, b, 1)), "case5 swap: B at slot 0 before E");
        t.put(e, 4);
        t.validate();
        check(slot(t, 0).equals(occupied(0, e, 2)), "case5 swap: E takes slot 0");
        check(slot(t, 1).equals(occupied(1, b, 2)), "case5 swap: B pushed to slot 1");
        check(t.get(a) == 1 && t.get(c) == 2 && t.get(b) == 3 && t.get(e) == 4, "case5 swap: all found");
    }

    private static void caseNotFoundValue(boolean early) {
        RobinHoodTable t = new RobinHoodTable(CASE_P, early);
        boolean threw = false;
        try {
            t.put("000000000005", HashIndex.NOT_FOUND);
        } catch (IllegalArgumentException ex) {
            threw = true;
        }
        check(threw && t.size() == 0, "put with NOT_FOUND throws IllegalArgumentException");
    }

    // ---------- Dem so o da xem ----------
    private static void probes(boolean early, long expected) {
        if (!Counters.ON) {
            failures++;
            System.out.println("FAIL probes: Counters.ON = false (Counters bi nap truoc khi dat countMode)");
            return;
        }
        KeyMaker km = new KeyMaker(3);
        String a = km.withHome(6), c = km.withHome(6), b = km.withHome(7),
                x = km.withHome(6);
        RobinHoodTable t = new RobinHoodTable(3, early);
        t.put(a, 100);
        t.put(c, 200);
        t.put(b, 300);
        Counters.reset();
        t.get(b);
        t.get(x);
        t.get(a);
        check(t.probeCount() == expected,
                "probeCount early=" + early + " got " + t.probeCount() + " expected " + expected);
    }

    // ---------- HashIndexFactory ----------
    private static void factory() {
        String[] variants = { "base", "noearly" };
        for (String v : variants) {
            HashIndex t = HashIndexFactory.create("rh", v, 4);
            t.put("000000000007", 70);
            check(t.get("000000000007") == 70 && t.capacity() == 16, "factory rh " + v);
        }
        HashIndex sc = HashIndexFactory.create("sc", "base", 4);
        check(sc instanceof SeparateChainingTable, "factory sc");
        check(sc.put("000000000007", 71) == HashIndex.INSERTED
                && sc.get("000000000007") == 71, "factory sc put/get");
        boolean threw = false;
        try {
            HashIndexFactory.create("no-such-algo", "base", 4);
        } catch (IllegalArgumentException e) {
            threw = true;
        }
        check(threw, "factory unknown algo throws IllegalArgumentException");
    }

    public static void main(String[] args) {
        Main.countMode = true; // phai dat truoc khi lop Counters duoc nap
        boolean[] variants = { true, false };
        for (boolean early : variants) {
            run("case1 early=" + early, () -> caseClusterRemove(early));
            run("case2 early=" + early, () -> caseUpdate(early));
            run("case3 early=" + early, () -> caseRemoveAbsent(early));
            run("case4 early=" + early, () -> caseFull(early));
            run("case5 early=" + early, () -> caseWrapAround(early));
            run("case5 swap early=" + early, () -> caseWrapSwap(early));
            run("not-found value early=" + early, () -> caseNotFoundValue(early));
        }
        run("probes early=true", () -> probes(true, 6L));
        run("probes early=false", () -> probes(false, 7L));
        run("factory", () -> factory());

        int p = 12;
        int m = 1 << p;
        int[] domains = { m / 2, m, 2 * m };
        long[] seeds = { 1L, 2L, 3L };
        for (int domain : domains) {
            for (long seed : seeds) {
                for (boolean early : variants) {
                    run("oracle domain=" + domain + " seed=" + seed + " early=" + early,
                            () -> oracle(p, early, domain, seed, 1000000));
                }
            }
        }

        if (failures > 0) {
            System.out.println("RobinHoodSelfTest: " + failures + " FAIL");
            System.exit(1);
        }
        System.out.println("RobinHoodSelfTest: ALL PASS");
    }
}