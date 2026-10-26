package hashbench;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.SplittableRandom;

/**
 * Tu kiem tra cho LinearProbingTombTable (lp-tomb). Phu trach: TV3.
 * Chay: java -cp hashbench\out hashbench.LpTombSelfTest
 */
public final class LpTombSelfTest {
    private static final int CASE_P = 4; // M = 16
    private static int failures = 0;

    private LpTombSelfTest() {
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

    private static String slot(LinearProbingTombTable t, int i) {
        return t.dump().split("\n")[i];
    }

    // ---------- Oracle so voi HashMap ----------
    private static void oracle(int p, double tau, int domain, long seed, int ops) {
        int m = 1 << p;
        String[] pool = randomKeys(domain, seed + 1000);
        LinearProbingTombTable t = new LinearProbingTombTable(p, tau);
        Map<String, Integer> map = new HashMap<String, Integer>();
        SplittableRandom r = new SplittableRandom(seed);
        String tag = "oracle tau=" + tau + " domain=" + domain + " seed=" + seed;
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
    /** Ca 1: xoa giua cum, khoa phia sau van phai tim thay (get phai di qua TOMB). */
    private static void caseSkipTomb() {
        KeyMaker km = new KeyMaker(CASE_P);
        LinearProbingTombTable t = new LinearProbingTombTable(CASE_P);
        String a = km.withHome(5), b = km.withHome(5), c = km.withHome(5);
        t.put(a, 1);
        t.put(b, 2);
        t.put(c, 3);
        check(t.remove(b), "case1 remove middle");
        check(slot(t, 6).equals("6: TOMB"), "case1 slot 6 is TOMB");
        check(t.get(c) == 3, "case1 get passes through TOMB");
        check(t.get(b) == HashIndex.NOT_FOUND, "case1 removed key not found");
        check(t.size() == 2 && t.tombCount() == 1, "case1 size 2, tomb 1");
        t.validate();
    }

    /** Ca 2: put nho TOMB dau tien va tai su dung no, nhung phai do het cum de tim khoa trung. */
    private static void caseReuseTomb() {
        KeyMaker km = new KeyMaker(CASE_P);
        LinearProbingTombTable t = new LinearProbingTombTable(CASE_P);
        String a = km.withHome(5), b = km.withHome(5), c = km.withHome(5),
                d = km.withHome(5);
        t.put(a, 1);
        t.put(b, 2);
        t.put(c, 3);
        t.remove(a);                                           // o 5 thanh TOMB
        check(t.put(c, 33) == HashIndex.UPDATED, "case2 put existing key behind TOMB returns UPDATED");
        check(t.size() == 2 && t.tombCount() == 1, "case2 no duplicate, TOMB still there");
        check(slot(t, 5).equals("5: TOMB"), "case2 slot 5 still TOMB after update");
        check(t.get(c) == 33, "case2 updated value");
        check(t.put(d, 4) == HashIndex.INSERTED, "case2 new key inserted");
        check(slot(t, 5).equals("5: key=" + d), "case2 new key reuses first TOMB (slot 5)");
        check(t.size() == 3 && t.tombCount() == 0, "case2 tomb count back to 0");
        t.validate();
    }

    private static void caseUpdate() {
        LinearProbingTombTable t = new LinearProbingTombTable(CASE_P);
        check(t.put("000000000007", 70) == HashIndex.INSERTED, "case3 insert");
        check(t.put("000000000007", 71) == HashIndex.UPDATED, "case3 update");
        check(t.get("000000000007") == 71 && t.size() == 1, "case3 value updated, size 1");
    }

    private static void caseRemoveAbsent() {
        KeyMaker km = new KeyMaker(CASE_P);
        LinearProbingTombTable t = new LinearProbingTombTable(CASE_P);
        t.put(km.withHome(5), 1);
        t.put(km.withHome(5), 2);
        String before = t.dump();
        check(!t.remove(km.withHome(5)), "case4 remove absent returns false");
        check(t.dump().equals(before) && t.size() == 2 && t.tombCount() == 0, "case4 table unchanged");
    }

    /** Ca 5: day bang, FULL, roi bang chi con TOMB (khong con EMPTY): get phai dung sau M o. */
    private static void caseFullAndAllTomb() {
        int m = 1 << CASE_P;
        String[] keys = randomKeys(m + 2, 77L);
        LinearProbingTombTable t = new LinearProbingTombTable(CASE_P);
        for (int i = 0; i < m; i++) {
            check(t.put(keys[i], i + 1) == HashIndex.INSERTED, "case5 fill " + i);
        }
        String before = t.dump();
        check(t.put(keys[m], 9) == HashIndex.FULL, "case5 put new key when full returns FULL");
        check(t.dump().equals(before) && t.size() == m, "case5 data unchanged after FULL");
        check(t.put(keys[3], 555) == HashIndex.UPDATED, "case5 update existing key when full");
        t.validate();
        for (int i = 0; i < m; i++) {
            check(t.remove(keys[i]), "case5 remove " + i);
        }
        check(t.size() == 0 && t.tombCount() == m && t.emptyCount() == 0, "case5 all cells TOMB, none EMPTY");
        check(t.get(keys[m]) == HashIndex.NOT_FOUND, "case5 get absent terminates on all-TOMB table");
        check(!t.remove(keys[m]), "case5 remove absent terminates on all-TOMB table");
        check(t.put(keys[m], 1) == HashIndex.INSERTED, "case5 put into all-TOMB table");
        check(t.get(keys[m]) == 1 && t.tombCount() == m - 1, "case5 inserted key found, tomb m-1");
        t.validate();
    }

    private static void caseWrapAround() {
        KeyMaker km = new KeyMaker(CASE_P);
        LinearProbingTombTable t = new LinearProbingTombTable(CASE_P);
        String w1 = km.withHome(15), w2 = km.withHome(15), w3 = km.withHome(15);
        t.put(w1, 1);
        t.put(w2, 2);
        t.put(w3, 3);
        check(slot(t, 15).equals("15: key=" + w1), "case6 w1 at slot 15");
        check(slot(t, 0).equals("0: key=" + w2), "case6 w2 wraps to slot 0");
        check(slot(t, 1).equals("1: key=" + w3), "case6 w3 at slot 1");
        check(t.remove(w2), "case6 remove middle of wrapped cluster");
        check(t.get(w3) == 3, "case6 w3 found across TOMB at slot 0");
        t.validate();
    }

    private static void caseNotFoundValue() {
        LinearProbingTombTable t = new LinearProbingTombTable(CASE_P);
        boolean threw = false;
        try {
            t.put("000000000005", HashIndex.NOT_FOUND);
        } catch (IllegalArgumentException ex) {
            threw = true;
        }
        check(threw && t.size() == 0, "put with NOT_FOUND throws IllegalArgumentException");
    }

    /** Rebuild: tau = 0.25, M = 16 => rebuild khi tombs >= 4, tuc o lan xoa thu 4. */
    private static void caseRebuild() {
        String[] keys = randomKeys(8, 5L);
        LinearProbingTombTable t = new LinearProbingTombTable(CASE_P, 0.25);
        for (int i = 0; i < 8; i++) {
            t.put(keys[i], i + 1);
        }
        for (int i = 0; i < 3; i++) {
            t.remove(keys[i]);
        }
        check(t.tombCount() == 3 && t.rebuildCount() == 0,
                "case7 3 tombs: threshold not reached");
        t.remove(keys[4]);
        check(t.tombCount() == 0 && t.rebuildCount() == 1,
                "case7 4th tomb triggers rebuild with >= threshold");
        check(t.size() == 4 && t.emptyCount() == 12,
                "case7 size 4, empty 12 after rebuild");
        t.remove(keys[3]);
        check(t.tombCount() == 1 && t.rebuildCount() == 1,
                "case7 later removal does not rebuild below threshold");
        check(t.size() == 3 && t.emptyCount() == 12, "case7 size 3 after later removal");
        boolean ok = true;
        for (int i = 5; i < 8; i++) {
            ok &= t.get(keys[i]) == i + 1;
        }
        check(ok, "case7 live keys survive rebuild");
        for (int i = 0; i < 5; i++) {
            check(t.get(keys[i]) == HashIndex.NOT_FOUND, "case7 removed key " + i + " stays removed");
        }
        t.validate();

        LinearProbingTombTable never = new LinearProbingTombTable(CASE_P);   // tau vo han
        for (int i = 0; i < 8; i++) {
            never.put(keys[i], i + 1);
        }
        for (int i = 0; i < 8; i++) {
            never.remove(keys[i]);
        }
        check(never.rebuildCount() == 0 && never.tombCount() == 8, "case7 tau infinite never rebuilds");
    }

    private static void probes() {
        if (!Counters.ON) {
            failures++;
            System.out.println("FAIL probes: Counters.ON = false (Counters bi nap truoc khi dat countMode)");
            return;
        }
        KeyMaker km = new KeyMaker(3);
        String a = km.withHome(6), c = km.withHome(6), b = km.withHome(7),
                x = km.withHome(6);
        LinearProbingTombTable t = new LinearProbingTombTable(3);
        t.put(a, 100);                  // o 6
        t.put(c, 200);                  // o 7
        t.put(b, 300);                  // o 0 (cuon vong)
        Counters.reset();
        t.get(b);                       // xem o 7, o 0 = 2
        t.get(x);                       // xem o 6, 7, 0, 1(EMPTY) = 4
        t.get(a);                       // 1
        check(t.probeCount() == 7L, "probeCount got " + t.probeCount() + " expected 7");
        t.remove(c);                    // o 7 thanh TOMB
        Counters.reset();
        t.get(b);                       // van xem o 7 (TOMB), o 0 = 2
        check(t.probeCount() == 2L, "probeCount through TOMB got " + t.probeCount() + " expected 2");
    }

    private static void factory() {
        HashIndex t = HashIndexFactory.create("lp-tomb", "base", 4, 0.2);
        t.put("000000000007", 70);
        check(t.get("000000000007") == 70 && t.capacity() == 16 && t instanceof HashStats,
                "factory lp-tomb 4 args");
        HashIndex u = HashIndexFactory.create("lp-tomb", "base", 4);
        check(u instanceof LinearProbingTombTable, "factory lp-tomb 3 args (tau infinite)");
        HashIndex r = HashIndexFactory.create("rh", "base", 4);
        check(r instanceof RobinHoodTable, "factory rh still works");
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
        run("case1 skip tomb", () -> caseSkipTomb());
        run("case2 reuse tomb", () -> caseReuseTomb());
        run("case3 update", () -> caseUpdate());
        run("case4 remove absent", () -> caseRemoveAbsent());
        run("case5 full and all-tomb", () -> caseFullAndAllTomb());
        run("case6 wrap-around", () -> caseWrapAround());
        run("not-found value", () -> caseNotFoundValue());
        run("case7 rebuild", () -> caseRebuild());
        run("probes", () -> probes());
        run("factory", () -> factory());

        int p = 12;
        int m = 1 << p;
        int[] domains = { m / 2, m, 2 * m };
        long[] seeds = { 1L, 2L, 3L };
        double[] taus = { Double.POSITIVE_INFINITY, 0.2, 0.1, 0.0 };
        for (int domain : domains) {
            for (long seed : seeds) {
                for (double tau : taus) {
                    run("oracle domain=" + domain + " seed=" + seed + " tau=" + tau,
                            () -> oracle(p, tau, domain, seed, 100000));
                }
            }
        }

        if (failures > 0) {
            System.out.println("LpTombSelfTest: " + failures + " FAIL");
            System.exit(1);
        }
        System.out.println("LpTombSelfTest: ALL PASS");
    }
}