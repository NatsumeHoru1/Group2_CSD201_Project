package hashbench;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.SplittableRandom;

/**
 * Tu kiem tra cho LinearProbingBackshiftTable (lp-backshift) va CsvValidator.
 * Phu trach: TV6.
 *
 * Chay: java -cp hashbench\out hashbench.LpBackshiftSelfTest
 *
 * Gom:
 *  - Oracle 10^6 thao tac so voi HashMap tren ca 6 cau hinh (3 domain x 3 seed).
 *  - 6 ca bat buoc cua de (theo ma tran), duoc chuyen sang bang M = 16.
 *  - Ca bo sung: xoa giua cum dang quac vong.
 *  - Kiem tra co backshift (khong tombstone) va co that la Algorithm R.
 *  - Kiem tra bo dem so o da xem.
 *  - Kiem tra HashIndexFactory tao duoc lp-backshift.
 *  - Kiem tra validator CSV: phat hien loi cu va bo qua file sach.
 */
public final class LpBackshiftSelfTest {
    private static final int CASE_P = 4; // M = 16
    private static int failures = 0;

    private LpBackshiftSelfTest() {
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

    /** Doc mot o tu dump() cua bang. */
    private static String slot(LinearProbingBackshiftTable t, int i) {
        return t.dump().split("\n")[i];
    }

    private static String occupied(int i, String key, int dib) {
        return i + ": key=" + key + " dib=" + dib;
    }

    // ---------- Oracle 10^6 thao tac so voi HashMap ----------

    /**
     * Chay oracle 10^6 thao tac: 40% put, 40% get, 20% remove.
     * Moi cau hinh: M = 2^12, domain khac nhau, 3 seed.
     */
    private static void oracle(int p, int domain, long seed, int ops) {
        int m = 1 << p;
        String[] pool = randomKeys(domain, seed + 1000);
        LinearProbingBackshiftTable t = new LinearProbingBackshiftTable(p);
        Map<String, Integer> map = new HashMap<String, Integer>();
        SplittableRandom r = new SplittableRandom(seed);
        String tag = "oracle domain=" + domain + " seed=" + seed;
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
                    throw new IllegalStateException(tag + " op " + i + ": put " + got
                            + " != " + expected);
                }
                if (got != HashIndex.FULL) {
                    map.put(k, Integer.valueOf(v));
                }
            } else if (op < 80) {
                Integer e = map.get(k);
                int expected = e == null ? HashIndex.NOT_FOUND : e.intValue();
                int got = t.get(k);
                if (got != expected) {
                    throw new IllegalStateException(tag + " op " + i + ": get " + got
                            + " != " + expected);
                }
            } else {
                boolean expected = map.remove(k) != null;
                boolean got = t.remove(k);
                if (got != expected) {
                    throw new IllegalStateException(tag + " op " + i + ": remove " + got
                            + " != " + expected);
                }
            }
            if (t.size() != map.size()) {
                throw new IllegalStateException(tag + " op " + i + ": size " + t.size()
                        + " != " + map.size());
            }
            if (i % 1000 == 0) {
                t.validate();
            }
        }
        t.validate();
    }

    // ---------- Cac ca tren bang M = 16 ----------

    /** Ca 1: xoa phan tu nam giua cum, cac phan tu sau van tim thay duoc. */
    private static void caseClusterRemove() {
        KeyMaker km = new KeyMaker(CASE_P);
        LinearProbingBackshiftTable t = new LinearProbingBackshiftTable(CASE_P);
        String k1 = km.withHome(5), k2 = km.withHome(5), k3 = km.withHome(5);
        t.put(k1, 1);
        t.put(k2, 2);
        t.put(k3, 3);
        t.validate();
        check(t.remove(k2), "case1 remove middle key");
        t.validate();
        check(t.get(k3) == 3 && t.get(k1) == 1 && t.get(k2) == HashIndex.NOT_FOUND
                && t.size() == 2, "case1 keys around removed key");
        // Khong con tombstone: o trong phai dung ngay sau o da xoa
        check(slot(t, 5).equals(occupied(5, k1, 0)), "case1 k1 van o o 5");
        check(slot(t, 6).equals(occupied(6, k3, 1)), "case1 k3 dich ve o 6");
        check(slot(t, 7).equals("7: -"), "case1 o 7 phai trong");
    }

    /** Ca 2: cap nhat gia tri cua khoa da co, ke ca khoa nam sau va cham. */
    private static void caseUpdate() {
        KeyMaker km = new KeyMaker(CASE_P);
        LinearProbingBackshiftTable t = new LinearProbingBackshiftTable(CASE_P);
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

    /** Ca 3: xoa khoa khong ton tai thi du lieu khong doi. */
    private static void caseRemoveAbsent() {
        KeyMaker km = new KeyMaker(CASE_P);
        LinearProbingBackshiftTable t = new LinearProbingBackshiftTable(CASE_P);
        t.put(km.withHome(5), 1);
        t.put(km.withHome(5), 2);
        t.put(km.withHome(5), 3);
        String absent = km.withHome(5);
        String before = t.dump();
        check(!t.remove(absent), "case3 remove absent returns false");
        check(t.dump().equals(before) && t.size() == 3, "case3 table unchanged");
        t.validate();
    }

    /** Ca 4: bang day M khoa; put khoa moi tra FULL va du lieu khong doi. */
    private static void caseFull() {
        int m = 1 << CASE_P;
        String[] keys = randomKeys(m + 1, 77L);
        LinearProbingBackshiftTable t = new LinearProbingBackshiftTable(CASE_P);
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

    /** Ca 5: cum quac vong qua cuoi mang ve dau mang. */
    private static void caseWrapAround() {
        KeyMaker km = new KeyMaker(CASE_P);
        LinearProbingBackshiftTable t = new LinearProbingBackshiftTable(CASE_P);
        String w1 = km.withHome(15), w2 = km.withHome(15), w3 = km.withHome(15);
        t.put(w1, 1);
        t.put(w2, 2);
        t.put(w3, 3);
        t.validate();
        check(slot(t, 15).equals(occupied(15, w1, 0)), "case5 w1 at slot 15");
        check(slot(t, 0).equals(occupied(0, w2, 1)), "case5 w2 wraps to slot 0");
        check(slot(t, 1).equals(occupied(1, w3, 2)), "case5 w3 wraps to slot 1");
        check(t.get(w1) == 1 && t.get(w2) == 2 && t.get(w3) == 3,
                "case5 all found across wrap");
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

    /** Ca 5 (phan hoan doi): them khoa thuoc nha khac vao cum dang quac vong. */
    private static void caseWrapSwap() {
        KeyMaker km = new KeyMaker(CASE_P);
        LinearProbingBackshiftTable t = new LinearProbingBackshiftTable(CASE_P);
        String a = km.withHome(14), c = km.withHome(14), b = km.withHome(15),
                e = km.withHome(14);
        t.put(a, 1);
        t.put(c, 2);
        t.put(b, 3);
        t.validate();
        // Dò mở tuyến tính không hoán đổi: b chi đẩy tới ô 0.
        check(slot(t, 0).equals(occupied(0, b, 1)), "case5 swap: B at slot 0 before E");
        t.put(e, 4);
        t.validate();
        // e nhà 14 bị chặn bởi a, c, b nên dồn tới ô 1.
        check(slot(t, 1).equals(occupied(1, e, 3)), "case5 swap: E pushed to slot 1");
        check(t.get(a) == 1 && t.get(c) == 2 && t.get(b) == 3 && t.get(e) == 4,
                "case5 swap: all found");
    }

    /** Ca 6: gia tri NOT_FOUND bi cam khi put. */
    private static void caseNotFoundValue() {
        LinearProbingBackshiftTable t = new LinearProbingBackshiftTable(CASE_P);
        boolean threw = false;
        try {
            t.put("000000000005", HashIndex.NOT_FOUND);
        } catch (IllegalArgumentException ex) {
            threw = true;
        }
        check(threw && t.size() == 0, "put with NOT_FOUND throws IllegalArgumentException");
    }

    /** Test bo sung: xoa giua cum dang quac vong tu o M-1 ve o 0. */
    private static void caseRemoveMiddleOfWrappedCluster() {
        KeyMaker km = new KeyMaker(CASE_P);
        LinearProbingBackshiftTable t = new LinearProbingBackshiftTable(CASE_P);
        String w1 = km.withHome(15), w2 = km.withHome(15), w3 = km.withHome(15);
        t.put(w1, 1);
        t.put(w2, 2);
        t.put(w3, 3);
        t.validate();
        check(t.remove(w2), "case7 remove middle of wrapped cluster");
        t.validate();
        check(t.get(w3) == 3 && t.get(w1) == 1, "case7 neighbors still found");
        check(t.get(w2) == HashIndex.NOT_FOUND, "case7 removed key gone");
        // w3 phai dich ve o 0, w1 van o 15
        check(slot(t, 0).equals(occupied(0, w3, 1)), "case7 w3 moved back to slot 0");
        check(slot(t, 15).equals(occupied(15, w1, 0)), "case7 w1 stays at slot 15");
        check(slot(t, 1).equals("1: -"), "case7 slot 1 empty, no tombstone");
    }

    /** Kiem tra bang khong sinh tombstone va co dung Algorithm R. */
    private static void caseBackshiftLeavesNoTombstone() {
        KeyMaker km = new KeyMaker(CASE_P);
        LinearProbingBackshiftTable t = new LinearProbingBackshiftTable(CASE_P);
        String a = km.withHome(4), b = km.withHome(4), c = km.withHome(5);
        t.put(a, 1);
        t.put(b, 2);
        t.put(c, 3);
        t.validate();
        check(t.tombCount() == 0, "backshift khong tao tombstone");
        check(t.remove(a), "case8 remove first of cluster");
        t.validate();
        check(t.tombCount() == 0, "case8 still no tombstone after remove");
        // Sau khi xoa a (o 4), b (nha 4) dich sang o 4 va c (nha 5) dich sang o 5.
        check(slot(t, 4).equals(occupied(4, b, 0)), "case8 b shifted to slot 4");
        check(slot(t, 5).equals(occupied(5, c, 0)), "case8 c shifted to slot 5");
        check(slot(t, 6).equals("6: -"), "case8 slot 6 empty");
        // Tim kiem van dung: b phai tim thay o o 4
        check(t.get(b) == 2, "case8 b found at new slot");
        check(t.get(c) == 3, "case8 c found at new slot");
    }

    // ---------- Dem so o da xem ----------

    /** Kiem tra ops_count quy uoc: so o da xem ke ca o EMPTY cuoi. */
    private static void probes() {
        if (!Counters.ON) {
            failures++;
            System.out.println("FAIL probes: Counters.ON = false (Counters bi nap truoc khi dat countMode)");
            return;
        }
        KeyMaker km = new KeyMaker(3); // M = 8
        String a = km.withHome(6), c = km.withHome(6), x = km.withHome(6);
        LinearProbingBackshiftTable t = new LinearProbingBackshiftTable(3);
        t.put(a, 100);
        t.put(c, 200);
        Counters.reset();
        t.get(c);   // o 6, 7 -> 2 o da xem
        t.get(x);   // o 6, 7, 0 -> 3 o da xem (o 0 rong)
        t.get(a);   // o 6 -> 1 o da xem
        check(t.probeCount() == 6L, "probeCount got " + t.probeCount() + " expected 6");
    }

    // ---------- HashIndexFactory ----------

    private static void factory() {
        HashIndex t = HashIndexFactory.create("lp-backshift", "base", 4);
        check(t.capacity() == 16, "factory lp-backshift capacity");
        t.put("000000000007", 70);
        check(t.get("000000000007") == 70, "factory lp-backshift put/get");
        boolean threw = false;
        try {
            HashIndexFactory.create("no-such-algo", "base", 4);
        } catch (IllegalArgumentException e) {
            threw = true;
        }
        check(threw, "factory unknown algo throws IllegalArgumentException");
    }

    // ---------- Validator CSV ----------

    /** Validator phai bao loi khi CSV sai va khong bao loi khi CSV dung. */
    private static void caseCsvValidator() throws Exception {
        java.nio.file.Path root = java.nio.file.Files.createTempDirectory("lp-backshift-validator-");
        try {
            // CSV dung: ca ba pha RQ1 cua mot nhom, tat ca deu ok.
            java.nio.file.Path good = root.resolve("good.csv");
            writeCsv(good, new String[][] {
                {"run_id", "proc1_build", "proc_id", "proc1", "phase", "build"},
                {"run_id", "proc1_present", "proc_id", "proc1", "phase", "present"},
                {"run_id", "proc1_absent", "proc_id", "proc1", "phase", "absent"},
            });
            CsvValidator.Report goodReport = CsvValidator.validate(good, true);
            check(goodReport.isValid(), "validator: CSV dung phai khong co loi");

            // CSV sai: elapsed_ms sai dinh dang, status wrong, not_started khong phu hop.
            java.nio.file.Path bad = root.resolve("bad.csv");
            writeCsv(bad, new String[][] {
                {"run_id", "proc1_build", "proc_id", "proc1", "phase", "build",
                        "elapsed_ms", "1.5"},
                {"run_id", "proc1_present", "proc_id", "proc1", "phase", "present",
                        "status", "wrong", "elapsed_ms", ""},
                {"run_id", "proc1_absent", "proc_id", "proc1", "phase", "absent",
                        "status", "ok", "phase_note", "not_started"},
            });
            CsvValidator.Report badReport = CsvValidator.validate(bad, true);
            check(!badReport.isValid(), "validator: CSV sai phai bao loi");
            boolean sawElapsedFormat = false;
            boolean sawWrong = false;
            boolean sawNotStarted = false;
            for (CsvValidator.Issue issue : badReport.issues) {
                if (issue.message.contains("elapsed_ms")) {
                    sawElapsedFormat = true;
                }
                if (issue.message.contains("wrong")) {
                    sawWrong = true;
                }
                if (issue.message.contains("not_started")) {
                    sawNotStarted = true;
                }
            }
            check(sawElapsedFormat, "validator: bao loi dinh dang elapsed_ms");
            check(sawWrong, "validator: bao loi status wrong");
            check(sawNotStarted, "validator: bao loi phase_note khong phu hop");
        } finally {
            deleteTree(root);
        }
    }

    /**
     * Viet CSV theo dung 32 cot cua CsvSchema.
     * Moi phan tu cua rows la cac cap ten cot / gia tri gan nhau.
     */
    private static void writeCsv(java.nio.file.Path file, String[][] rows) throws Exception {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < CsvSchema.COLUMNS.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(CsvSchema.COLUMNS[i]);
        }
        sb.append('\n');
        for (String[] overrides : rows) {
            String[] cells = new String[CsvSchema.COLUMNS.length];
            for (int i = 0; i < cells.length; i++) {
                cells[i] = "";
            }
            cells[CsvSchema.RQ_INDEX] = "RQ1";
            cells[CsvSchema.ALGORITHM_INDEX] = "lp-backshift";
            cells[CsvSchema.VARIANT_INDEX] = "base";
            cells[CsvSchema.DATASET_INDEX] = "rq1a";
            cells[CsvSchema.N_INDEX] = "12";
            cells[CsvSchema.PARAMS_INDEX] = "p=4;alpha=0.75;backshift=knuth-r";
            cells[CsvSchema.SEED_INDEX] = "42";
            cells[CsvSchema.REPEAT_INDEX] = "1";
            cells[CsvSchema.GENERATOR_VERSION_INDEX] = "1.0";
            cells[CsvSchema.STATUS_INDEX] = "ok";
            cells[CsvSchema.XMX_INDEX] = "2g";
            cells[CsvSchema.MACHINE_ID_INDEX] = "dev";
            cells[CsvSchema.WALL_MS_INDEX] = "2.000";
            cells[CsvSchema.ELAPSED_MS_INDEX] = "1.500";
            for (int i = 0; i + 1 < overrides.length; i += 2) {
                int index = -1;
                for (int j = 0; j < CsvSchema.COLUMNS.length; j++) {
                    if (CsvSchema.COLUMNS[j].equals(overrides[i])) {
                        index = j;
                        break;
                    }
                }
                if (index >= 0) {
                    cells[index] = overrides[i + 1];
                }
            }
            for (int i = 0; i < cells.length; i++) {
                if (i > 0) {
                    sb.append(',');
                }
                sb.append(cells[i]);
            }
            sb.append('\n');
        }
        java.nio.file.Files.write(file, sb.toString().getBytes("UTF-8"));
    }

    /** Xoa cay thu muc tam do test tao. */
    private static void deleteTree(java.nio.file.Path root) throws java.io.IOException {
        if (!java.nio.file.Files.exists(root)) {
            return;
        }
        java.nio.file.Files.walkFileTree(root, new java.nio.file.SimpleFileVisitor<java.nio.file.Path>() {
            @Override
            public java.nio.file.FileVisitResult visitFile(
                    java.nio.file.Path file, java.nio.file.attribute.BasicFileAttributes attributes)
                    throws java.io.IOException {
                java.nio.file.Files.delete(file);
                return java.nio.file.FileVisitResult.CONTINUE;
            }

            @Override
            public java.nio.file.FileVisitResult postVisitDirectory(
                    java.nio.file.Path dir, java.io.IOException exception) throws java.io.IOException {
                if (exception != null) {
                    throw exception;
                }
                java.nio.file.Files.delete(dir);
                return java.nio.file.FileVisitResult.CONTINUE;
            }
        });
    }

    public static void main(String[] args) throws Exception {
        Main.countMode = true; // phai dat truoc khi lop Counters duoc nap
        run("case1 cluster remove", () -> caseClusterRemove());
        run("case2 update", () -> caseUpdate());
        run("case3 remove absent", () -> caseRemoveAbsent());
        run("case4 full", () -> caseFull());
        run("case5 wrap around", () -> caseWrapAround());
        run("case5 swap wrap", () -> caseWrapSwap());
        run("case6 not-found value", () -> caseNotFoundValue());
        run("case7 remove middle of wrapped cluster", () -> caseRemoveMiddleOfWrappedCluster());
        run("case8 backshift leaves no tombstone", () -> caseBackshiftLeavesNoTombstone());
        run("probes", () -> probes());
        run("factory", () -> factory());
        run("csv validator", () -> {
            try {
                caseCsvValidator();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        int p = 12;
        int m = 1 << p;
        int[] domains = { m / 2, m, 2 * m };
        long[] seeds = { 1L, 2L, 3L };
        for (int domain : domains) {
            for (long seed : seeds) {
                run("oracle domain=" + domain + " seed=" + seed,
                        () -> oracle(p, domain, seed, 1000000));
            }
        }

        if (failures > 0) {
            System.out.println("LpBackshiftSelfTest: " + failures + " FAIL");
            System.exit(1);
        }
        System.out.println("LpBackshiftSelfTest: ALL PASS");
    }
}
