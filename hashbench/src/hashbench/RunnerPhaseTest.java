package hashbench;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Kiểm tra phase log, tiến trình con, CSV và append mà không dùng JUnit. */
public final class RunnerPhaseTest {
    private static final String[] PHASES = {"build", "present", "absent", "control"};

    private RunnerPhaseTest() {
    }

    /** Chạy tất cả ca độc lập, in PASS/FAIL và báo lỗi nếu có ca thất bại. */
    public static void main(String[] args) throws Exception {
        int failures = 0;
        failures += runCase("normal-phases", new TestCase() {
            @Override
            public void run(Path root) throws Exception {
                testNormalPhases(root);
            }
        });
        failures += runCase("timeout-absent", new TestCase() {
            @Override
            public void run(Path root) throws Exception {
                testTimeout(root);
            }
        });
        failures += runCase("crash-present", new TestCase() {
            @Override
            public void run(Path root) throws Exception {
                testCrash(root);
            }
        });
        failures += runCase("oom-present", new TestCase() {
            @Override
            public void run(Path root) throws Exception {
                testOutOfMemory(root);
            }
        });
        failures += runCase("count-forwarding", new TestCase() {
            @Override
            public void run(Path root) throws Exception {
                testCountForwarding(root);
            }
        });
        failures += runCase("csv-append", new TestCase() {
            @Override
            public void run(Path root) throws Exception {
                testCsvAppend(root);
            }
        });
        failures += runCase("truncated-phase-log", new TestCase() {
            @Override
            public void run(Path root) throws Exception {
                testTruncatedLog(root);
            }
        });
        if (failures != 0) {
            throw new AssertionError(failures + " ca kiểm thử thất bại");
        }
        System.out.println("RunnerPhaseTest: ALL PASS");
    }

    /** Tạo thư mục riêng và dọn đúng thư mục tạm sau từng ca. */
    private static int runCase(String name, TestCase test) throws Exception {
        Path root = Files.createTempDirectory("hashbench-" + name + "-");
        try {
            test.run(root);
            System.out.println("PASS " + name);
            return 0;
        } catch (Throwable failure) {
            System.out.println("FAIL " + name + ": " + failure.getMessage());
            failure.printStackTrace(System.out);
            return 1;
        } finally {
            deleteTree(root);
        }
    }

    /** Kiểm tra bốn pha bình thường trong bộ nhớ và khi đọc lại CSV. */
    private static void testNormalPhases(Path root) throws Exception {
        RunConfig config = config(10, false).withSleepPhase(null, 0L);
        Runner.RunResult result = run(config, root);
        assertEquals(4, result.phases.size(), "số pha");
        assertTrue(!result.childCountEnabled, "count mode của con phải tắt");
        assertExpectedPhases(result, new String[] {"ok", "ok", "ok", "ok"},
                new String[] {"", "", "", ""});
        assertCsv(root.resolve("results.csv"), result, config,
                new String[] {"ok", "ok", "ok", "ok"},
                new String[] {"", "", "", ""});
    }

    /** Kiểm tra timeout khi absent đang chạy và control chưa bắt đầu. */
    private static void testTimeout(Path root) throws Exception {
        RunConfig config = config(5, false).withSleepPhase("absent", 30L);
        Runner.RunResult result = run(config, root);
        assertTrue(result.timedOut, "tiến trình phải timeout");
        assertExpectedPhases(result, new String[] {"ok", "ok", "timeout", "timeout"},
                new String[] {"", "", "", "not_started"});
        assertCsv(root.resolve("results.csv"), result, config,
                new String[] {"ok", "ok", "timeout", "timeout"},
                new String[] {"", "", "", "not_started"});
    }

    /** Kiểm tra crash giữa present và status crash của các pha chưa chạy. */
    private static void testCrash(Path root) throws Exception {
        RunConfig config = config(10, false).withExitPhase("present", 1);
        Runner.RunResult result = run(config, root);
        assertEquals(1, result.exitCode, "exit code con");
        assertExpectedPhases(result, new String[] {"ok", "crash", "crash", "crash"},
                new String[] {"", "", "not_started", "not_started"});
        assertCsv(root.resolve("results.csv"), result, config,
                new String[] {"ok", "crash", "crash", "crash"},
                new String[] {"", "", "not_started", "not_started"});
    }

    /** Kiểm tra OOM được ghi cho pha đang chạy và truyền cho các pha sau. */
    private static void testOutOfMemory(Path root) throws Exception {
        RunConfig config = config(10, false).withOomPhase("present");
        Runner.RunResult result = run(config, root);
        assertEquals(3, result.exitCode, "exit code OOM");
        assertExpectedPhases(result, new String[] {"ok", "oom", "oom", "oom"},
                new String[] {"", "", "not_started", "not_started"});
        assertCsv(root.resolve("results.csv"), result, config,
                new String[] {"ok", "oom", "oom", "oom"},
                new String[] {"", "", "not_started", "not_started"});
    }

    /** Kiểm tra tiến trình con nhận và xác nhận trạng thái --count. */
    private static void testCountForwarding(Path root) throws Exception {
        RunConfig config = config(10, true);
        Runner.RunResult result = run(config, root);
        assertTrue(result.childCountEnabled, "Counters.ON của con phải bật");
        assertExpectedPhases(result, new String[] {"ok", "ok", "ok", "ok"},
                new String[] {"", "", "", ""});
        assertCsv(root.resolve("results.csv"), result, config,
                new String[] {"ok", "ok", "ok", "ok"},
                new String[] {"", "", "", ""});
    }

    /** Chạy hai tiến trình vào cùng CSV và xác nhận header chỉ xuất hiện một lần. */
    private static void testCsvAppend(Path root) throws Exception {
        Path csv = root.resolve("append.csv");
        RunConfig config = config(10, false);
        Runner.RunResult first = Runner.runWithCsv(config, csv, root);
        Runner.RunResult second = Runner.runWithCsv(config, csv, root);
        List<String[]> rows = readCsv(csv);
        assertEquals(CsvSchema.COLUMNS.length, rows.get(0).length, "số cột header");
        assertEquals(1 + first.phases.size() + second.phases.size(), rows.size(),
                "header duy nhất và số hàng append");
        assertAllRowsValid(rows);
    }

    /** Xác nhận parser bỏ qua dòng cuối phase log không có newline kết thúc. */
    private static void testTruncatedLog(Path root) throws Exception {
        PhaseLog log = new PhaseLog("truncated_test", root);
        log.append("build", "ok", "1.250", "10", "20", "3", "4",
                "0123456789abcdef", "1.0");
        Files.write(log.path(),
                "present\tok\t2.500\t11\t21\t4\t5\tfedcba9876543210\t1.0"
                        .getBytes(StandardCharsets.UTF_8),
                java.nio.file.StandardOpenOption.APPEND);
        List<PhaseLog.Entry> entries = log.readCompleted();
        assertEquals(1, entries.size(), "số dòng log hoàn chỉnh");
        assertEquals("build", entries.get(0).phase, "pha hoàn chỉnh");
        assertEquals("10", entries.get(0).heapBefore, "heap_before trong TSV");
        assertEquals("3", entries.get(0).opsCount, "ops_count trong TSV");
        assertEquals("4", entries.get(0).outputCheck, "output_check trong TSV");
        assertEquals("0123456789abcdef", entries.get(0).inputSha256, "hash trong TSV");
    }

    /** Chạy runner vào CSV riêng của test và xác nhận kết quả tiến trình. */
    private static Runner.RunResult run(RunConfig config, Path root) throws Exception {
        return Runner.runWithCsv(config, root.resolve("results.csv"), root);
    }

    /** Tạo cấu hình test với metadata hợp lệ, không chạy workload benchmark. */
    private static RunConfig config(int timeoutSec, boolean countMode) {
        return new RunConfig("TEST", "rh", "base", "test", 12L, 4,
                0.75, 7, 0.2, 0.5, 3L, 42L, 1,
                "2g", "test-machine", timeoutSec, countMode);
    }

    /** Kiểm tra danh sách pha, status, elapsed_ms và ghi chú ngay trong bộ nhớ. */
    private static void assertExpectedPhases(Runner.RunResult result,
            String[] statuses, String[] notes) {
        assertEquals(PHASES.length, result.phases.size(), "số pha trong bộ nhớ");
        for (int i = 0; i < PHASES.length; i++) {
            Runner.PhaseResult phase = result.phases.get(i);
            assertEquals(PHASES[i], phase.phase, "thứ tự pha");
            assertEquals(statuses[i], phase.status, "status " + phase.phase);
            assertEquals(notes[i], phase.phaseNote, "phase_note " + phase.phase);
            if ("ok".equals(statuses[i])) {
                assertMatches("[0-9]+\\.[0-9]{3}", phase.elapsedMs, "elapsed_ms");
            } else {
                assertEquals("", phase.elapsedMs, "elapsed_ms phải rỗng khi status != ok");
            }
        }
    }

    /** Đọc file CSV và kiểm tra schema, metadata, trạng thái và run_id duy nhất. */
    private static void assertCsv(Path csv, Runner.RunResult result, RunConfig config,
            String[] statuses, String[] notes) throws IOException {
        List<String[]> rows = readCsv(csv);
        assertEquals(CsvSchema.COLUMNS.length, rows.get(0).length, "số cột header");
        assertTrue(Arrays.equals(CsvSchema.COLUMNS, rows.get(0)), "header CSV sai schema");
        assertEquals(PHASES.length + 1, rows.size(), "số hàng CSV");
        assertAllRowsValid(rows);

        for (int i = 0; i < PHASES.length; i++) {
            String[] columns = rows.get(i + 1);
            assertEquals(result.procId + "_" + PHASES[i],
                    columns[CsvSchema.RUN_ID_INDEX], "run_id");
            assertEquals(config.rq, columns[CsvSchema.RQ_INDEX], "rq");
            assertEquals(config.algo, columns[CsvSchema.ALGORITHM_INDEX], "algorithm");
            assertEquals(config.variant, columns[CsvSchema.VARIANT_INDEX], "variant");
            assertEquals(config.dataset, columns[CsvSchema.DATASET_INDEX], "dataset");
            assertEquals(Long.toString(config.n), columns[CsvSchema.N_INDEX], "n");
            assertEquals(config.params, columns[CsvSchema.PARAMS_INDEX], "params");
            assertEquals(Long.toString(config.seed), columns[CsvSchema.SEED_INDEX], "seed");
            assertEquals(Integer.toString(config.repeat), columns[CsvSchema.REPEAT_INDEX],
                    "repeat");
            assertEquals(statuses[i], columns[CsvSchema.STATUS_INDEX], "CSV status");
            assertEquals(config.xmx, columns[CsvSchema.XMX_INDEX], "xmx");
            assertEquals(config.machineId, columns[CsvSchema.MACHINE_ID_INDEX], "machine_id");
            assertEquals(result.procId, columns[CsvSchema.PROC_ID_INDEX], "proc_id");
            assertEquals(PHASES[i], columns[CsvSchema.PHASE_INDEX], "phase");
            assertUnmeasuredColumnsEmpty(columns);
            assertMatches("[0-9]+\\.[0-9]{3}",
                    columns[CsvSchema.WALL_MS_INDEX], "wall_ms");
            assertEquals(notes[i], columns[CsvSchema.PHASE_NOTE_INDEX], "phase_note");
            if ("ok".equals(statuses[i])) {
                assertMatches("[0-9]+\\.[0-9]{3}",
                        columns[CsvSchema.ELAPSED_MS_INDEX], "elapsed_ms");
            } else {
                assertEquals("", columns[CsvSchema.ELAPSED_MS_INDEX],
                        "CSV elapsed_ms phải rỗng khi status != ok");
            }
        }
    }

    /** Xác nhận các trường chưa có dữ liệu thật không bị điền giá trị giả. */
    private static void assertUnmeasuredColumnsEmpty(String[] columns) {
        int[] indexes = {
            CsvSchema.INPUT_SHA256_INDEX,
            CsvSchema.GENERATOR_VERSION_INDEX,
            CsvSchema.HEAP_BEFORE_BYTES_INDEX,
            CsvSchema.HEAP_PEAK_BYTES_INDEX,
            CsvSchema.OPS_COUNT_INDEX,
            CsvSchema.OUTPUT_CHECK_INDEX,
            CsvSchema.GIT_COMMIT_INDEX,
            CsvSchema.TOMB_RATIO_INDEX,
            CsvSchema.EMPTY_RATIO_INDEX,
            CsvSchema.REBUILD_COUNT_INDEX,
            CsvSchema.REBUILD_MS_INDEX,
            CsvSchema.BLOCK_MEDIAN_MS_INDEX,
            CsvSchema.BLOCK_MAX_MS_INDEX,
            CsvSchema.DIB_MEAN_INDEX,
            CsvSchema.DIB_MAX_INDEX
        };
        for (int index : indexes) {
            assertEquals("", columns[index], "cột chưa có dữ liệu phải để trống");
        }
    }

    /** Đọc CSV dạng các trường không có dấu phẩy trong dữ liệu test. */
    private static List<String[]> readCsv(Path csv) throws IOException {
        List<String[]> rows = new ArrayList<String[]>();
        try (BufferedReader reader = Files.newBufferedReader(csv, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                rows.add(line.split(",", -1));
            }
        }
        assertTrue(!rows.isEmpty(), "CSV phải có header");
        return rows;
    }

    /** Kiểm tra số cột, run_id duy nhất, trạng thái hợp lệ và elapsed_ms. */
    private static void assertAllRowsValid(List<String[]> rows) {
        assertEquals(CsvSchema.COLUMNS.length, rows.get(0).length, "header đúng số cột");
        Set<String> runIds = new HashSet<String>();
        Set<String> statuses = new HashSet<String>(Arrays.asList(
                "ok", "timeout", "crash", "oom", "wrong"));
        for (int i = 1; i < rows.size(); i++) {
            String[] columns = rows.get(i);
            assertEquals(CsvSchema.COLUMNS.length, columns.length, "số cột hàng CSV");
            assertTrue(runIds.add(columns[CsvSchema.RUN_ID_INDEX]), "run_id bị trùng");
            String status = columns[CsvSchema.STATUS_INDEX];
            assertTrue(statuses.contains(status), "status ngoài danh sách cho phép: " + status);
            if (!"ok".equals(status)) {
                assertEquals("", columns[CsvSchema.ELAPSED_MS_INDEX],
                        "elapsed_ms phải rỗng khi status != ok");
            }
            String phaseNote = columns[CsvSchema.PHASE_NOTE_INDEX];
            if ("not_started".equals(phaseNote)) {
                assertTrue("timeout".equals(status) || "crash".equals(status)
                        || "oom".equals(status), "not_started không phù hợp với status");
            }
        }
    }

    /** Xóa riêng thư mục tạm mà test đã tạo. */
    private static void deleteTree(Path root) throws IOException {
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

    /** Xác nhận điều kiện đúng và ném lỗi mô tả khi sai. */
    private static void assertTrue(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    /** So sánh giá trị kỳ vọng và giá trị thực tế trong test. */
    private static void assertEquals(Object expected, Object actual, String message) {
        if (expected == null ? actual != null : !expected.equals(actual)) {
            throw new AssertionError(message + ": expected=" + expected + ", actual=" + actual);
        }
    }

    /** Xác nhận chuỗi khớp định dạng biểu thức chính quy yêu cầu. */
    private static void assertMatches(String regex, String value, String message) {
        if (value == null || !value.matches(regex)) {
            throw new AssertionError(message + " sai định dạng: " + value);
        }
    }

    /** Chữ ký cho một ca kiểm thử độc lập. */
    private interface TestCase {
        void run(Path root) throws Exception;
    }
}
