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

/** Kiểm tra đo RQ1 thật qua tiến trình con và dữ liệu CSV, không dùng JUnit. */
public final class MeasureChildTest {
    private static final String[] PHASES = {"build", "present", "absent"};

    private MeasureChildTest() {
    }

    /** Chạy mọi ca đo thật; in một dòng tổng PASS khi tất cả đều đạt. */
    public static void main(String[] args) throws Exception {
        Path root = Files.createTempDirectory("hashbench-measure-test-");
        try {
            testMeasurementsAreDeterministic(root.resolve("determinism"));
            testIndependentOracle(root.resolve("oracle"));
            testVariantsAgree(root.resolve("variants"));
            testCounters(root.resolve("count"));
            testTauForwarding(root.resolve("tau"));
            testTimeoutHarvesting(root.resolve("timeout"));
            System.out.println("MeasureChildTest: ALL PASS");
        } finally {
            deleteTree(root);
        }
    }

    /** Kiểm tra ba pha, trường số, hash input và kết quả ổn định giữa hai lượt. */
    private static void testMeasurementsAreDeterministic(Path root) throws Exception {
        Files.createDirectories(root);
        RunConfig config = config("rh", "base", 10, 0.75, 20000, 42L, false, 30);
        Runner.RunResult first = run(config, root.resolve("first.csv"), root);
        Runner.RunResult second = run(config, root.resolve("second.csv"), root);
        List<String[]> firstRows = readCsv(root.resolve("first.csv"));
        List<String[]> secondRows = readCsv(root.resolve("second.csv"));
        assertEquals(4, firstRows.size(), "header và ba pha");
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

    /** So sánh output_check với oracle HashMap được tạo độc lập trong test. */
    private static void testIndependentOracle(Path root) throws Exception {
        Files.createDirectories(root);
        RunConfig config = config("rh", "base", 10, 0.75, 20000, 1L, false, 30);
        Measure.Data data = Measure.buildData(config.seed, config.p, config.alpha,
                (int) config.queries);
        Map<Long, Integer> oracle = new HashMap<Long, Integer>();
        for (int i = 0; i < data.present.length; i++) {
            oracle.put(Long.valueOf(data.present[i]), Integer.valueOf(i + 1));
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

    /** Xác nhận rh/base và rh/noearly tạo cùng output_check trên cùng input. */
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
                    "output_check giữa variant");
        }
    }

    /** Xác nhận count ghi probe count ở cả hai pha query và hợp lý trung bình. */
    private static void testCounters(Path root) throws Exception {
        Files.createDirectories(root);
        RunConfig config = config("rh", "base", 10, 0.75, 20000, 5L, true, 30);
        run(config, root.resolve("count.csv"), root);
        List<String[]> rows = readCsv(root.resolve("count.csv"));
        long presentOps = Long.parseLong(rows.get(2)[CsvSchema.OPS_COUNT_INDEX]);
        long absentOps = Long.parseLong(rows.get(3)[CsvSchema.OPS_COUNT_INDEX]);
        assertTrue(presentOps > 0L, "present ops_count phải dương");
        assertTrue(absentOps > 0L, "absent ops_count phải dương");
        double averagePresent = presentOps / (double) config.queries;
        assertTrue(averagePresent >= 1.0 && averagePresent <= 10.0,
                "present probes trung bình ngoài khoảng 1..10: " + averagePresent);
    }

    /** Xác nhận lp-tomb nhận tau qua tiến trình con mà không đổi kết quả truy vấn. */
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

    /** Dùng hook sleep để timeout luôn xảy ra trong pha chỉ định, không phụ thuộc tốc độ máy. */
    private static void testTimeoutHarvesting(Path root) throws Exception {
        Files.createDirectories(root);
        RunConfig config = config("rh", "base", 10, 0.75, 20000, 7L, false, 10)
                .withSleepPhase("present", 60L);
        Runner.RunResult result = run(config, root.resolve("timeout.csv"), root);
        assertTrue(result.timedOut, "sleep ở present phải timeout");
        List<String[]> rows = readCsv(root.resolve("timeout.csv"));
        assertEquals(PHASES.length + 1, rows.size(), "mọi pha phải được thu hoạch");
        String[] build = rows.get(1);
        String[] present = rows.get(2);
        String[] absent = rows.get(3);
        assertEquals("build", build[CsvSchema.PHASE_INDEX], "pha build");
        assertEquals("ok", build[CsvSchema.STATUS_INDEX], "build phải hoàn tất");
        assertMatches("[0-9]+\\.[0-9]{3}", build[CsvSchema.ELAPSED_MS_INDEX],
                "elapsed build");
        assertEquals("present", present[CsvSchema.PHASE_INDEX], "pha present");
        assertEquals("timeout", present[CsvSchema.STATUS_INDEX], "present phải timeout");
        assertEquals("", present[CsvSchema.ELAPSED_MS_INDEX], "elapsed present timeout");
        assertEquals("", present[CsvSchema.PHASE_NOTE_INDEX], "present phase_note");
        assertEquals("absent", absent[CsvSchema.PHASE_INDEX], "pha absent");
        assertEquals("timeout", absent[CsvSchema.STATUS_INDEX], "absent sau timeout");
        assertEquals("", absent[CsvSchema.ELAPSED_MS_INDEX], "elapsed absent");
        assertEquals("not_started", absent[CsvSchema.PHASE_NOTE_INDEX],
                "absent chưa bắt đầu");
    }

    /** Tạo cấu hình RQ1 riêng cho từng lượt test. */
    private static RunConfig config(String algo, String variant, int p, double alpha,
            long queries, long seed, boolean count, int timeoutSec) {
        return config(algo, variant, p, alpha, queries, seed, count, timeoutSec, 0.0);
    }

    /** Tạo cấu hình RQ1 với tau cụ thể. */
    private static RunConfig config(String algo, String variant, int p, double alpha,
            long queries, long seed, boolean count, int timeoutSec, double tau) {
        return new RunConfig("RQ1", algo, variant, "rq1a", 0L, p, alpha,
                0, tau, 0.0, queries, seed, 1, "2g", "", timeoutSec, count);
    }

    /** Chạy runner và ghi vào đúng file CSV riêng của ca test. */
    private static Runner.RunResult run(RunConfig config, Path csv, Path workingDirectory)
            throws Exception {
        return Runner.runWithCsv(config, csv, workingDirectory);
    }

    /** Tính oracle theo quy ước giá trị tìm thấy cộng một cho mỗi miss. */
    private static long expectedCheck(long[] queries, Map<Long, Integer> oracle) {
        long total = 0L;
        for (int i = 0; i < queries.length; i++) {
            Integer value = oracle.get(Long.valueOf(queries[i]));
            total += value == null ? 1L : value.intValue();
        }
        return total;
    }

    /** Kiểm tra header theo toàn bộ schema chuẩn. */
    private static void assertHeader(String[] header) {
        assertEquals(CsvSchema.COLUMNS.length, header.length, "số cột header");
        for (int i = 0; i < header.length; i++) {
            assertEquals(CsvSchema.COLUMNS[i], header[i], "tên cột header");
        }
    }

    /** Đọc CSV mà các trường test không chứa dấu phẩy. */
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

    /** Xóa cây thư mục tạm đã tạo bởi test. */
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

    /** Xác nhận điều kiện và báo lỗi rõ nếu không đạt. */
    private static void assertTrue(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    /** So sánh giá trị mong đợi và thực tế. */
    private static void assertEquals(Object expected, Object actual, String message) {
        if (expected == null ? actual != null : !expected.equals(actual)) {
            throw new AssertionError(message + ": expected=" + expected + ", actual=" + actual);
        }
    }

    /** Kiểm tra chuỗi theo regex. */
    private static void assertMatches(String regex, String value, String message) {
        if (value == null || !value.matches(regex)) {
            throw new AssertionError(message + " sai định dạng: " + value);
        }
    }
}
