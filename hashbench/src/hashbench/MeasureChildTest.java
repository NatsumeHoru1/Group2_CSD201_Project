package hashbench;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Kiem tra do RQ1 that qua tien trinh con va du lieu CSV, khong dung JUnit. */
public final class MeasureChildTest {
    private static final String[] PHASES = {"build", "present", "absent"};

    private MeasureChildTest() {
    }

    /** Chay moi ca do that; in mot dong tong PASS khi tat ca deu dat. */
    public static void main(String[] args) throws Exception {
        Path root = Files.createTempDirectory("hashbench-measure-test-");
        try {
            testMeasurementsAreDeterministic(root.resolve("determinism"));
            testIndependentOracle(root.resolve("oracle"));
            testVariantsAgree(root.resolve("variants"));
            testCounters(root.resolve("count"));
            testTauForwarding(root.resolve("tau"));
            testTimeoutHarvesting(root.resolve("timeout"));
            testClusteredDataset(root.resolve("clustered"));
            testRq2Workload(root.resolve("rq2"));
            testRq3VariantsAndDeterminism(root.resolve("rq3"));
            testRq2ZeroCycles(root.resolve("rq2-zero"));
            System.out.println("MeasureChildTest: ALL PASS");
        } finally {
            deleteTree(root);
        }
    }

    /** Kiem tra ba pha, truong so, hash input va ket qua on dinh giua hai luot. */
    private static void testMeasurementsAreDeterministic(Path root) throws Exception {
        Files.createDirectories(root);
        RunConfig config = config("rh", "base", 10, 0.75, 20000, 42L, false, 30);
        Runner.RunResult first = run(config, root.resolve("first.csv"), root);
        Runner.RunResult second = run(config, root.resolve("second.csv"), root);
        List<String[]> firstRows = readCsv(root.resolve("first.csv"));
        List<String[]> secondRows = readCsv(root.resolve("second.csv"));
        assertEquals(4, firstRows.size(), "header va ba pha");
        assertHeader(firstRows.get(0));
        for (int i = 0; i < PHASES.length; i++) {
            String[] row = firstRows.get(i + 1);
            String[] again = secondRows.get(i + 1);
            assertEquals(PHASES[i], row[CsvSchema.PHASE_INDEX], "pha");
            assertEquals("ok", row[CsvSchema.STATUS_INDEX], "status");
            assertMatches("[0-9]+\\.[0-9]{3}", row[CsvSchema.ELAPSED_MS_INDEX],
                    "elapsed_ms");
            assertMatches("[0-9]+", row[CsvSchema.HEAP_BEFORE_BYTES_INDEX],
                    "heap_before_bytes");
            assertMatches("[0-9]+", row[CsvSchema.HEAP_PEAK_BYTES_INDEX],
                    "heap_peak_bytes");
            assertEquals("", row[CsvSchema.OPS_COUNT_INDEX], "ops_count timing");
            assertMatches("[0-9a-f]{64}", row[CsvSchema.INPUT_SHA256_INDEX],
                    "input_sha256");
            assertEquals(Measure.GENERATOR_VERSION,
                    row[CsvSchema.GENERATOR_VERSION_INDEX], "generator_version");
            assertEquals(row[CsvSchema.OUTPUT_CHECK_INDEX],
                    again[CsvSchema.OUTPUT_CHECK_INDEX], "output_check determinism");
            assertEquals(row[CsvSchema.INPUT_SHA256_INDEX],
                    again[CsvSchema.INPUT_SHA256_INDEX], "input hash determinism");
        }
    }

    /** So sanh output_check voi oracle HashMap duoc tao doc lap trong test. */
    private static void testIndependentOracle(Path root) throws Exception {
        Files.createDirectories(root);
        RunConfig config = config("rh", "base", 10, 0.75, 20000, 1L, false, 30);
        Measure.Data data = Measure.buildData(config.seed, config.p, config.alpha,
                (int) config.queries);
        Map<String, Integer> oracle = new HashMap<String, Integer>();
        Map<String, String> presentObjects = new HashMap<String, String>();
        for (int i = 0; i < data.present.length; i++) {
            oracle.put(data.present[i], Integer.valueOf(i + 1));
            presentObjects.put(data.present[i], data.present[i]);
        }
        for (int i = 0; i < data.presentQueries.length; i++) {
            String query = data.presentQueries[i];
            assertTrue(oracle.containsKey(query), "present query phai nam trong present");
            assertTrue(query != presentObjects.get(query),
                    "present query phai la object String doc lap");
        }
        long expectedPresent = expectedCheck(data.presentQueries, oracle);
        long expectedAbsent = expectedCheck(data.absentQueries, oracle);

        run(config, root.resolve("oracle.csv"), root);
        List<String[]> rows = readCsv(root.resolve("oracle.csv"));
        assertEquals(Long.toString(expectedPresent),
                rows.get(2)[CsvSchema.OUTPUT_CHECK_INDEX], "oracle present");
        assertEquals(Long.toString(expectedAbsent),
                rows.get(3)[CsvSchema.OUTPUT_CHECK_INDEX], "oracle absent");
    }

    /** Xac nhan rh/base va rh/noearly tao cung output_check tren cung input. */
    private static void testVariantsAgree(Path root) throws Exception {
        Files.createDirectories(root);
        RunConfig base = config("rh", "base", 10, 0.75, 20000, 9L, false, 30);
        RunConfig noearly = config("rh", "noearly", 10, 0.75, 20000, 9L, false, 30);
        run(base, root.resolve("base.csv"), root);
        run(noearly, root.resolve("noearly.csv"), root);
        List<String[]> baseRows = readCsv(root.resolve("base.csv"));
        List<String[]> noearlyRows = readCsv(root.resolve("noearly.csv"));
        for (int i = 2; i < 4; i++) {
            assertEquals(baseRows.get(i)[CsvSchema.OUTPUT_CHECK_INDEX],
                    noearlyRows.get(i)[CsvSchema.OUTPUT_CHECK_INDEX],
                    "output_check giua variant");
        }
    }

    /** Xac nhan count ghi probe count o ca hai pha query va hop ly trung binh. */
    private static void testCounters(Path root) throws Exception {
        Files.createDirectories(root);
        RunConfig config = config("rh", "base", 10, 0.75, 20000, 5L, true, 30);
        run(config, root.resolve("count.csv"), root);
        List<String[]> rows = readCsv(root.resolve("count.csv"));
        long presentOps = Long.parseLong(rows.get(2)[CsvSchema.OPS_COUNT_INDEX]);
        long absentOps = Long.parseLong(rows.get(3)[CsvSchema.OPS_COUNT_INDEX]);
        assertTrue(presentOps > 0L, "present ops_count phai duong");
        assertTrue(absentOps > 0L, "absent ops_count phai duong");
        double averagePresent = presentOps / (double) config.queries;
        assertTrue(averagePresent >= 1.0 && averagePresent <= 10.0,
                "present probes trung binh ngoai khoang 1..10: " + averagePresent);
    }

    /** Xac nhan lp-tomb nhan tau qua tien trinh con ma khong doi ket qua truy van. */
    private static void testTauForwarding(Path root) throws Exception {
        Files.createDirectories(root);
        RunConfig infinite = config("lp-tomb", "base", 10, 0.75, 20000, 1L,
                false, 30, Double.POSITIVE_INFINITY);
        RunConfig finite = config("lp-tomb", "base", 10, 0.75, 20000, 1L,
                false, 30, 0.2);
        RunConfig rh = config("rh", "base", 10, 0.75, 20000, 1L,
                false, 30, Double.POSITIVE_INFINITY);
        run(infinite, root.resolve("infinite.csv"), root);
        run(finite, root.resolve("finite.csv"), root);
        run(rh, root.resolve("rh.csv"), root);
        List<String[]> infiniteRows = readCsv(root.resolve("infinite.csv"));
        List<String[]> finiteRows = readCsv(root.resolve("finite.csv"));
        List<String[]> rhRows = readCsv(root.resolve("rh.csv"));
        for (int i = 1; i <= PHASES.length; i++) {
            assertEquals("ok", infiniteRows.get(i)[CsvSchema.STATUS_INDEX],
                    "tau=inf status " + PHASES[i - 1]);
            assertEquals("ok", finiteRows.get(i)[CsvSchema.STATUS_INDEX],
                    "tau=0.2 status " + PHASES[i - 1]);
            assertEquals("inf", CsvParams.value(
                    infiniteRows.get(i)[CsvSchema.PARAMS_INDEX], "tau"), "params tau=inf");
            assertEquals("0.2", CsvParams.value(
                    finiteRows.get(i)[CsvSchema.PARAMS_INDEX], "tau"), "params tau=0.2");
            assertEquals(rhRows.get(i)[CsvSchema.OUTPUT_CHECK_INDEX],
                    infiniteRows.get(i)[CsvSchema.OUTPUT_CHECK_INDEX],
                    "output_check tau=inf vs rh " + PHASES[i - 1]);
            assertEquals(rhRows.get(i)[CsvSchema.OUTPUT_CHECK_INDEX],
                    finiteRows.get(i)[CsvSchema.OUTPUT_CHECK_INDEX],
                    "output_check tau=0.2 vs rh " + PHASES[i - 1]);
            assertEquals(infiniteRows.get(i)[CsvSchema.OUTPUT_CHECK_INDEX],
                    finiteRows.get(i)[CsvSchema.OUTPUT_CHECK_INDEX],
                    "output_check tau variants " + PHASES[i - 1]);
        }
    }

    /** Dung hook sleep de timeout luon xay ra trong pha chi dinh, khong phu thuoc toc do may. */
    private static void testTimeoutHarvesting(Path root) throws Exception {
        Files.createDirectories(root);
        RunConfig config = config("rh", "base", 10, 0.75, 20000, 7L, false, 10)
                .withSleepPhase("present", 60L);
        Runner.RunResult result = run(config, root.resolve("timeout.csv"), root);
        assertTrue(result.timedOut, "sleep o present phai timeout");
        List<String[]> rows = readCsv(root.resolve("timeout.csv"));
        assertEquals(PHASES.length + 1, rows.size(), "moi pha phai duoc thu hoach");
        String[] build = rows.get(1);
        String[] present = rows.get(2);
        String[] absent = rows.get(3);
        assertEquals("build", build[CsvSchema.PHASE_INDEX], "pha build");
        assertEquals("ok", build[CsvSchema.STATUS_INDEX], "build phai hoan tat");
        assertMatches("[0-9]+\\.[0-9]{3}", build[CsvSchema.ELAPSED_MS_INDEX],
                "elapsed build");
        assertEquals("present", present[CsvSchema.PHASE_INDEX], "pha present");
        assertEquals("timeout", present[CsvSchema.STATUS_INDEX], "present phai timeout");
        assertEquals("", present[CsvSchema.ELAPSED_MS_INDEX], "elapsed present timeout");
        assertEquals("", present[CsvSchema.PHASE_NOTE_INDEX], "present phase_note");
        assertEquals("absent", absent[CsvSchema.PHASE_INDEX], "pha absent");
        assertEquals("timeout", absent[CsvSchema.STATUS_INDEX], "absent sau timeout");
        assertEquals("", absent[CsvSchema.ELAPSED_MS_INDEX], "elapsed absent");
        assertEquals("not_started", absent[CsvSchema.PHASE_NOTE_INDEX],
                "absent chua bat dau");
    }

    /** Kiem tra dataset rq1clust don cum voi sc va lp-tomb. */
    private static void testClusteredDataset(Path root) throws Exception {
        Files.createDirectories(root);
        int p = 10;
        double alpha = 0.75;
        long queries = 20000L;
        long seed = 1L;

        // Kiem tra tap khoa don cum sinh ra co >= 10% hash < max(1, M/64)
        int n = (int) Math.round(alpha * (1 << p));
        String[] clusteredKeys = Generator.generateClusteredKeys(seed, n, p);
        int clusterLimit = Math.max(1, (1 << p) / 64);
        int inCluster = 0;
        for (String k : clusteredKeys) {
            if (Hashing.hash(k, p) < clusterLimit) {
                inCluster++;
            }
        }
        assertTrue(inCluster >= (n + 9) / 10, "rq1clust phai co >= 10% khoa don cum");

        RunConfig scConfig = new RunConfig("RQ1", "sc", "base", "rq1clust", 0L, p, alpha,
                0, Double.POSITIVE_INFINITY, 0.0, queries, seed, 1, "2g", "", 30, false);
        RunConfig lpConfig = new RunConfig("RQ1", "lp-tomb", "base", "rq1clust", 0L, p, alpha,
                0, Double.POSITIVE_INFINITY, 0.0, queries, seed, 1, "2g", "", 30, false);

        run(scConfig, root.resolve("sc1.csv"), root);
        run(scConfig, root.resolve("sc2.csv"), root);
        run(lpConfig, root.resolve("lp.csv"), root);

        List<String[]> sc1Rows = readCsv(root.resolve("sc1.csv"));
        List<String[]> sc2Rows = readCsv(root.resolve("sc2.csv"));
        List<String[]> lpRows = readCsv(root.resolve("lp.csv"));

        assertEquals(4, sc1Rows.size(), "sc hang");
        assertEquals(4, lpRows.size(), "lp hang");

        for (int i = 1; i <= 3; i++) {
            assertEquals("ok", sc1Rows.get(i)[CsvSchema.STATUS_INDEX], "sc status");
            assertEquals("ok", lpRows.get(i)[CsvSchema.STATUS_INDEX], "lp status");
            // Hai lan chay cung seed cho output_check y het
            assertEquals(sc1Rows.get(i)[CsvSchema.OUTPUT_CHECK_INDEX],
                    sc2Rows.get(i)[CsvSchema.OUTPUT_CHECK_INDEX], "sc seed determinism");
            // output_check present/absent giong nhau giua sc va lp-tomb
            assertEquals(sc1Rows.get(i)[CsvSchema.OUTPUT_CHECK_INDEX],
                    lpRows.get(i)[CsvSchema.OUTPUT_CHECK_INDEX], "output_check sc vs lp");
        }
    }

    private static void testRq2Workload(Path root) throws Exception {
        Files.createDirectories(root);
        RunConfig config = workloadConfig("lp-tomb", "base", "rq2", 10,
                0.5, 2048, Double.POSITIVE_INFINITY, 20000, 13L, false);
        run(config, root.resolve("rq2.csv"), root);
        List<String[]> rows = readCsv(root.resolve("rq2.csv"));
        String[] phases = {"build", "workload", "present", "absent",
            "ctl_present", "ctl_absent"};
        assertEquals(phases.length + 1, rows.size(), "RQ2 header va sau pha");
        for (int i = 0; i < phases.length; i++) {
            assertEquals(phases[i], rows.get(i + 1)[CsvSchema.PHASE_INDEX],
                    "thu tu pha RQ2");
            assertEquals("ok", rows.get(i + 1)[CsvSchema.STATUS_INDEX],
                    "status pha RQ2 " + phases[i]);
        }
        assertEquals(rows.get(3)[CsvSchema.OUTPUT_CHECK_INDEX],
                rows.get(5)[CsvSchema.OUTPUT_CHECK_INDEX], "ctl_present checksum");
        assertEquals(rows.get(4)[CsvSchema.OUTPUT_CHECK_INDEX],
                rows.get(6)[CsvSchema.OUTPUT_CHECK_INDEX], "ctl_absent checksum");
        assertMatches("[0-9]+\\.[0-9]{3}",
                rows.get(2)[CsvSchema.BLOCK_MEDIAN_MS_INDEX], "block median RQ2");
        assertMatches("[0-9]+\\.[0-9]{3}",
                rows.get(2)[CsvSchema.BLOCK_MAX_MS_INDEX], "block max RQ2");
    }

    private static void testRq3VariantsAndDeterminism(Path root) throws Exception {
        Files.createDirectories(root);
        String[] algos = {"lp-tomb", "lp-backshift", "rh", "rh"};
        String[] variants = {"base", "base", "base", "noearly"};
        double[] taus = {0.1, Double.POSITIVE_INFINITY,
            Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY};
        String[] reference = null;
        String[] referenceHash = null;
        String[] phases = {"build", "workload", "present", "absent"};
        for (int i = 0; i < algos.length; i++) {
            RunConfig config = workloadConfig(algos[i], variants[i], "rq3", 10,
                    0.8, 1024, taus[i], 20000, 29L, false);
            Path firstCsv = root.resolve("variant-" + i + "-first.csv");
            Path secondCsv = root.resolve("variant-" + i + "-second.csv");
            run(config, firstCsv, root);
            run(config, secondCsv, root);
            List<String[]> firstRows = readCsv(firstCsv);
            List<String[]> secondRows = readCsv(secondCsv);
            assertEquals(phases.length + 1, firstRows.size(), "RQ3 header va bon pha");
            String[] outputs = new String[2];
            String[] hashes = new String[2];
            for (int phase = 0; phase < phases.length; phase++) {
                String[] row = firstRows.get(phase + 1);
                String[] again = secondRows.get(phase + 1);
                assertEquals(phases[phase], row[CsvSchema.PHASE_INDEX], "thu tu pha RQ3");
                assertEquals("ok", row[CsvSchema.STATUS_INDEX],
                        "status pha RQ3 " + phases[phase]);
                assertEquals(row[CsvSchema.OUTPUT_CHECK_INDEX],
                        again[CsvSchema.OUTPUT_CHECK_INDEX],
                        "output_check cung seed/luot chay " + phases[phase]);
                assertEquals(row[CsvSchema.INPUT_SHA256_INDEX],
                        again[CsvSchema.INPUT_SHA256_INDEX], "SHA cung seed");
                if (phase == 2 || phase == 3) {
                    outputs[phase - 2] = row[CsvSchema.OUTPUT_CHECK_INDEX];
                    hashes[phase - 2] = row[CsvSchema.INPUT_SHA256_INDEX];
                }
            }
            if (reference == null) {
                reference = outputs;
                referenceHash = hashes;
            } else {
                assertEquals(reference[0], outputs[0], "RQ3 present giua thuat toan");
                assertEquals(reference[1], outputs[1], "RQ3 absent giua thuat toan");
                assertEquals(referenceHash[0], hashes[0], "RQ3 present input SHA");
                assertEquals(referenceHash[1], hashes[1], "RQ3 absent input SHA");
            }
        }
    }

    private static void testRq2ZeroCycles(Path root) throws Exception {
        Files.createDirectories(root);
        RunConfig config = workloadConfig("lp-tomb", "base", "rq2", 10,
                0.5, 0, Double.POSITIVE_INFINITY, 20000, 37L, true);
        run(config, root.resolve("rq2-zero.csv"), root);
        List<String[]> rows = readCsv(root.resolve("rq2-zero.csv"));
        assertEquals(7, rows.size(), "C=0 van co du sau pha RQ2");
        for (int i = 1; i < rows.size(); i++) {
            assertEquals("ok", rows.get(i)[CsvSchema.STATUS_INDEX], "status RQ2 C=0");
        }
        String[] workload = rows.get(2);
        assertEquals("", workload[CsvSchema.BLOCK_MEDIAN_MS_INDEX],
                "C=0 khong co block median");
        assertEquals("", workload[CsvSchema.BLOCK_MAX_MS_INDEX],
                "C=0 khong co block max");
        assertEquals(rows.get(3)[CsvSchema.OUTPUT_CHECK_INDEX],
                rows.get(5)[CsvSchema.OUTPUT_CHECK_INDEX],
                "ctl_present checksum C=0");
        assertEquals(rows.get(4)[CsvSchema.OUTPUT_CHECK_INDEX],
                rows.get(6)[CsvSchema.OUTPUT_CHECK_INDEX],
                "ctl_absent checksum C=0");
        assertMatches("[0-9]+", rows.get(3)[CsvSchema.OPS_COUNT_INDEX],
                "count ops_count RQ2");
    }

    private static RunConfig workloadConfig(String algo, String variant, String dataset,
            int p, double load, int cycles, double tau, long queries, long seed,
            boolean countMode) {
        return new RunConfig("rq2".equals(dataset) ? "RQ2" : "RQ3", algo, variant,
                dataset, 0L, p, 0.0, cycles, tau, load, queries, seed, 1,
                "2g", "test", 60, countMode);
    }

    /** Tao cau hinh RQ1 rieng cho tung luot test. */
    private static RunConfig config(String algo, String variant, int p, double alpha,
            long queries, long seed, boolean count, int timeoutSec) {
        return config(algo, variant, p, alpha, queries, seed, count, timeoutSec, 0.0);
    }

    /** Tao cau hinh RQ1 voi tau cu the. */
    private static RunConfig config(String algo, String variant, int p, double alpha,
            long queries, long seed, boolean count, int timeoutSec, double tau) {
        return new RunConfig("RQ1", algo, variant, "rq1a", 0L, p, alpha,
                0, tau, 0.0, queries, seed, 1, "2g", "", timeoutSec, count);
    }

    /** Chay runner va ghi vao dung file CSV rieng cua ca test. */
    private static Runner.RunResult run(RunConfig config, Path csv, Path workingDirectory)
            throws Exception {
        return Runner.runWithCsv(config, csv, workingDirectory);
    }

    /** Tinh oracle theo quy uoc gia tri tim thay cong mot cho moi miss. */
    private static long expectedCheck(String[] queries, Map<String, Integer> oracle) {
        long total = 0L;
        for (int i = 0; i < queries.length; i++) {
            Integer value = oracle.get(queries[i]);
            total += value == null ? 1L : value.intValue();
        }
        return total;
    }

    /** Kiem tra header theo toan bo schema chuan. */
    private static void assertHeader(String[] header) {
        assertEquals(CsvSchema.COLUMNS.length, header.length, "so cot header");
        for (int i = 0; i < header.length; i++) {
            assertEquals(CsvSchema.COLUMNS[i], header[i], "ten cot header");
        }
    }

    /** Doc CSV ma cac truong test khong chua dau phay. */
    private static List<String[]> readCsv(Path file) throws IOException {
        java.util.ArrayList<String[]> rows = new java.util.ArrayList<String[]>();
        try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                rows.add(line.split(",", -1));
            }
        }
        return rows;
    }

    /** Xoa cay thu muc tam da tao boi test. */
    private static void deleteTree(Path root) throws IOException {
        if (!Files.exists(root)) {
            return;
        }
        Files.walkFileTree(root, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attributes)
                    throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path directory, IOException exception)
                    throws IOException {
                if (exception != null) {
                    throw exception;
                }
                Files.delete(directory);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    /** Xac nhan dieu kien va bao loi ro neu khong dat. */
    private static void assertTrue(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    /** So sanh gia tri mong doi va thuc te. */
    private static void assertEquals(Object expected, Object actual, String message) {
        if (expected == null ? actual != null : !expected.equals(actual)) {
            throw new AssertionError(message + ": expected=" + expected + ", actual=" + actual);
        }
    }

    /** Kiem tra chuoi theo regex. */
    private static void assertMatches(String regex, String value, String message) {
        if (value == null || !value.matches(regex)) {
            throw new AssertionError(message + " sai dinh dang: " + value);
        }
    }
}
