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

/** Kiem tra phase log, tien trinh con, CSV va append ma khong dung JUnit. */
public final class RunnerPhaseTest {
    private static final String[] PHASES = {"build", "present", "absent", "control"};

    private RunnerPhaseTest() {
    }

    /** Chay tat ca ca doc lap, in PASS/FAIL va bao loi neu co ca that bai. */
    public static void main(String[] args) throws Exception {
        int failures = 0;
        failures += runCase("normal-phases", new TestCase() {
            @Override
            public void run(Path root) throws Exception {
                testNormalPhases(root);
            }
        });
        failures += runCase("git-commit-metadata", new TestCase() {
            @Override
            public void run(Path root) throws Exception {
                testGitCommitMetadata(root);
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
        failures += runCase("deterministic-proc-id", new TestCase() {
            @Override
            public void run(Path root) throws Exception {
                testDeterministicProcId(root);
            }
        });
        failures += runCase("truncated-phase-log", new TestCase() {
            @Override
            public void run(Path root) throws Exception {
                testTruncatedLog(root);
            }
        });
        failures += runCase("validator-latest", new TestCase() {
            @Override
            public void run(Path root) throws Exception {
                testValidatorLatestOption(root);
            }
        });
        if (failures != 0) {
            throw new AssertionError(failures + " ca kiem thu that bai");
        }
        System.out.println("RunnerPhaseTest: ALL PASS");
    }

    /** Tao thu muc rieng va don dung thu muc tam sau tung ca. */
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

    /** Kiem tra bon pha binh thuong trong bo nho va khi doc lai CSV. */
    private static void testNormalPhases(Path root) throws Exception {
        RunConfig config = config(10, false).withSleepPhase(null, 0L);
        Runner.RunResult result = run(config, root);
        assertEquals(4, result.phases.size(), "so pha");
        assertTrue(!result.childCountEnabled, "count mode cua con phai tat");
        assertExpectedPhases(result, new String[] {"ok", "ok", "ok", "ok"},
                new String[] {"", "", "", ""});
        assertCsv(root.resolve("results.csv"), result, config,
                new String[] {"ok", "ok", "ok", "ok"},
                new String[] {"", "", "", ""});
    }

    /** Ghi hash sach, hau to dirty va chan official khi source scope thay doi. */
    private static void testGitCommitMetadata(Path root) throws Exception {
        try {
            runGit(root, "--version");
        } catch (IOException exception) {
            System.out.println("SKIP git-commit-metadata: git khong co tren PATH");
            return;
        }
        Path source = root.resolve("hashbench").resolve("x.txt");
        Files.createDirectories(source.getParent());
        Files.write(source, "baseline".getBytes(StandardCharsets.UTF_8));
        runGit(root, "init");
        runGit(root, "add", "--", "hashbench/x.txt");
        runGit(root, "-c", "user.name=Runner Test", "-c",
                "user.email=runner-test@example.invalid", "commit", "-m", "baseline");
        String expectedHash = runGit(root, "rev-parse", "--short", "HEAD").trim();

        RunConfig config = config(10, false);
        Runner.runWithCsv(config, root.resolve("clean.csv"), root);
        List<String[]> cleanRows = readCsv(root.resolve("clean.csv"));
        for (int i = 1; i < cleanRows.size(); i++) {
            assertMatches("[0-9a-f]{7}", cleanRows.get(i)[CsvSchema.GIT_COMMIT_INDEX],
                    "git_commit clean");
            assertEquals(expectedHash, cleanRows.get(i)[CsvSchema.GIT_COMMIT_INDEX],
                    "hash clean");
        }

        Files.write(source, "modified".getBytes(StandardCharsets.UTF_8));
        Runner.runWithCsv(config, root.resolve("dirty.csv"), root);
        List<String[]> dirtyRows = readCsv(root.resolve("dirty.csv"));
        for (int i = 1; i < dirtyRows.size(); i++) {
            assertMatches("[0-9a-f]{7}-dirty",
                    dirtyRows.get(i)[CsvSchema.GIT_COMMIT_INDEX], "git_commit dirty");
        }

        Path officialCsv = root.resolve("official.csv");
        boolean refused = false;
        try {
            Runner.runWithCsv(config, officialCsv, root, true);
        } catch (Runner.OfficialRunException exception) {
            refused = true;
            assertTrue(exception.getMessage().contains("Official benchmark bi tu choi"),
                    "official phai bao ro ly do");
        }
        assertTrue(refused, "official phai tu choi cay ban");
        assertTrue(!Files.exists(officialCsv), "official bi tu choi khong duoc ghi CSV");
    }

    /** Kiem tra timeout khi absent dang chay va control chua bat dau. */
    private static void testTimeout(Path root) throws Exception {
        RunConfig config = config(5, false).withSleepPhase("absent", 30L);
        Runner.RunResult result = run(config, root);
        assertTrue(result.timedOut, "tien trinh phai timeout");
        assertExpectedPhases(result, new String[] {"ok", "ok", "timeout", "timeout"},
                new String[] {"", "", "", "not_started"});
        assertCsv(root.resolve("results.csv"), result, config,
                new String[] {"ok", "ok", "timeout", "timeout"},
                new String[] {"", "", "", "not_started"});
    }

    /** Kiem tra crash giua present va status crash cua cac pha chua chay. */
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

    /** Kiem tra OOM duoc ghi cho pha dang chay va truyen cho cac pha sau. */
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

    /** Kiem tra tien trinh con nhan va xac nhan trang thai --count. */
    private static void testCountForwarding(Path root) throws Exception {
        RunConfig config = config(10, true);
        Runner.RunResult result = run(config, root);
        assertTrue(result.childCountEnabled, "Counters.ON cua con phai bat");
        assertExpectedPhases(result, new String[] {"ok", "ok", "ok", "ok"},
                new String[] {"", "", "", ""});
        assertCsv(root.resolve("results.csv"), result, config,
                new String[] {"ok", "ok", "ok", "ok"},
                new String[] {"", "", "", ""});
    }

    /** Chay hai tien trinh vao cung CSV va xac nhan header chi xuat hien mot lan. */
    private static void testCsvAppend(Path root) throws Exception {
        Path csv = root.resolve("append.csv");
        RunConfig config1 = config(10, false);
        RunConfig config2 = new RunConfig(config1.rq, config1.algo, config1.variant,
                config1.dataset, config1.n, config1.p, config1.alpha, config1.c,
                config1.tau, config1.load, config1.queries, config1.seed, 2,
                config1.xmx, config1.machineId, config1.timeoutSec, config1.countMode);
        Runner.RunResult first = Runner.runWithCsv(config1, csv, root);
        Runner.RunResult second = Runner.runWithCsv(config2, csv, root);
        List<String[]> rows = readCsv(csv);
        assertEquals(CsvSchema.COLUMNS.length, rows.get(0).length, "so cot header");
        assertEquals(1 + first.phases.size() + second.phases.size(), rows.size(),
                "header duy nhat va so hang append");
        assertAllRowsValid(rows);
    }

    /** Kiem tra proc_id tat dinh va khong trung lap giua cac cau hinh khac nhau. */
    private static void testDeterministicProcId(Path root) throws Exception {
        RunConfig c1 = new RunConfig("RQ1", "rh", "base", "rq1a", 0L, 18, 0.75, 0,
                Double.POSITIVE_INFINITY, 0.0, 1000000L, 1L, 1, "2g", "m1", 60, false);
        assertEquals("RQ1_rh_base_rq1a_p18_a0p75_tinf_s1_r1", c1.procId(), "procId base");
        assertTrue(c1.procId().matches("[A-Za-z0-9_-]+"), "procId regex");
        assertEquals(c1.procId(), c1.procId(), "procId tat dinh");

        RunConfig cCount = c1.withCountMode(true);
        assertEquals("RQ1_rh_base_rq1a_p18_a0p75_tinf_s1_r1-count", cCount.procId(), "procId count");

        RunConfig cTau = new RunConfig("RQ1", "lp-tomb", "base", "rq1a", 0L, 18, 0.75, 0,
                0.2, 0.0, 1000000L, 1L, 1, "2g", "m1", 60, false);
        assertEquals("RQ1_lp-tomb_base_rq1a_p18_a0p75_t0p2_s1_r1", cTau.procId(), "procId tau huu han");

        Set<String> seen = new HashSet<String>();
        assertTrue(seen.add(c1.procId()), "c1 duy nhat");
        assertTrue(seen.add(cCount.procId()), "cCount duy nhat");
        assertTrue(seen.add(cTau.procId()), "cTau duy nhat");
        assertTrue(seen.add(new RunConfig("RQ1", "rh", "base", "rq1a", 0L, 20, 0.75, 0,
                Double.POSITIVE_INFINITY, 0.0, 1000000L, 1L, 1, "2g", "m1", 60, false).procId()), "p khac");
        assertTrue(seen.add(new RunConfig("RQ1", "rh", "base", "rq1a", 0L, 18, 0.5, 0,
                Double.POSITIVE_INFINITY, 0.0, 1000000L, 1L, 1, "2g", "m1", 60, false).procId()), "alpha khac");
        assertTrue(seen.add(new RunConfig("RQ1", "rh", "base", "rq1a", 0L, 18, 0.75, 0,
                Double.POSITIVE_INFINITY, 0.0, 1000000L, 2L, 1, "2g", "m1", 60, false).procId()), "seed khac");
        assertTrue(seen.add(new RunConfig("RQ1", "rh", "base", "rq1a", 0L, 18, 0.75, 0,
                Double.POSITIVE_INFINITY, 0.0, 1000000L, 1L, 2, "2g", "m1", 60, false).procId()), "repeat khac");
        RunConfig rq2 = new RunConfig("RQ2", "lp-tomb", "base", "rq2", 0L,
                20, 0.0, 262144, Double.POSITIVE_INFINITY, 0.5, 1000000L,
                1L, 1, "2g", "m1", 60, false);
        assertEquals(524288L, rq2.n, "n RQ2 tu load");
        assertEquals("RQ2_lp-tomb_base_rq2_p20_a0p0_tinf_l0p5_c262144_s1_r1",
                rq2.procId(), "procId RQ2 co load/c truoc seed");
        assertEquals(Arrays.asList("build", "workload", "present", "absent",
                "ctl_present", "ctl_absent"), RunConfig.phasesForDataset("rq2"),
                "thu tu pha RQ2");
        assertEquals(Arrays.asList("build", "workload", "present", "absent"),
                RunConfig.phasesForDataset("rq3"), "thu tu pha RQ3");
    }

    /** Xac nhan parser bo qua dong cuoi phase log khong co newline ket thuc. */
    private static void testTruncatedLog(Path root) throws Exception {
        PhaseLog log = new PhaseLog("truncated_test", root);
        log.append("build", "ok", "1.250", "10", "20", "3", "4",
                "0123456789abcdef", "1.0");
        log.append("workload", "ok", "3.000", "12", "22", "", "",
                "fedcba9876543210", "1.0", "0.1", "0.2", "3", "4.5",
                "5.5", "6.5", "7.5", "8");
        Files.write(log.path(),
                "present\tok\t2.500\t11\t21\t4\t5\tfedcba9876543210\t1.0"
                        .getBytes(StandardCharsets.UTF_8),
                java.nio.file.StandardOpenOption.APPEND);
        List<PhaseLog.Entry> entries = log.readCompleted();
        assertEquals(2, entries.size(), "so dong log hoan chinh");
        assertEquals("build", entries.get(0).phase, "pha hoan chinh");
        assertEquals("10", entries.get(0).heapBefore, "heap_before trong TSV");
        assertEquals("3", entries.get(0).opsCount, "ops_count trong TSV");
        assertEquals("4", entries.get(0).outputCheck, "output_check trong TSV");
        assertEquals("0123456789abcdef", entries.get(0).inputSha256, "hash trong TSV");
        assertEquals("", entries.get(0).tombRatio, "log cu thieu thong ke mo rong");
        assertEquals("0.1", entries.get(1).tombRatio, "tomb_ratio trong TSV mo rong");
        assertEquals("8", entries.get(1).dibMax, "dib_max trong TSV mo rong");
    }

    /** Kiem tra validator: CSV co 2 dong trung run_id => mac dinh FAIL, voi latest=true => PASS. */
    private static void testValidatorLatestOption(Path root) throws Exception {
        Path csv = root.resolve("dup.csv");
        RunConfig config = new RunConfig("RQ1", "rh", "base", "rq1a", 0L, 10, 0.75,
                0, Double.POSITIVE_INFINITY, 0.0, 20000L, 1L, 1, "2g", "test-machine", 10, false);
        Runner.runWithCsv(config, csv, root);
        Runner.runWithCsv(config, csv, root);

        CsvValidator.Report defaultReport = CsvValidator.validate(csv, false, false);
        assertTrue(!defaultReport.isValid(), "Mac dinh phai FAIL khi co run_id trung");

        CsvValidator.Report latestReport = CsvValidator.validate(csv, false, true);
        assertTrue(latestReport.isValid(), "Voi latest=true phai PASS khi co run_id trung: " + latestReport.issues);
    }

    /** Chay runner vao CSV rieng cua test va xac nhan ket qua tien trinh. */
    private static Runner.RunResult run(RunConfig config, Path root) throws Exception {
        return Runner.runWithCsv(config, root.resolve("results.csv"), root);
    }

    /** Chay git khong qua pager va tra stdout; moi command test co timeout huu han. */
    private static String runGit(Path directory, String... arguments) throws Exception {
        List<String> command = new ArrayList<String>();
        command.add("git");
        command.add("--no-pager");
        command.addAll(Arrays.asList(arguments));
        Path output = Files.createTempFile("runner-phase-git-", ".out");
        try {
            ProcessBuilder builder = new ProcessBuilder(command);
            builder.directory(directory.toFile());
            builder.redirectErrorStream(true);
            builder.redirectOutput(output.toFile());
            Process process;
            try {
                process = builder.start();
            } catch (IOException exception) {
                throw new IOException("git executable unavailable", exception);
            }
            if (!process.waitFor(10L, java.util.concurrent.TimeUnit.SECONDS)) {
                process.destroyForcibly();
                process.waitFor();
                throw new IOException("git command timed out: " + command);
            }
            String result = new String(Files.readAllBytes(output), StandardCharsets.UTF_8);
            if (process.exitValue() != 0) {
                throw new IOException("git command failed: " + command + "\n" + result);
            }
            return result;
        } finally {
            Files.deleteIfExists(output);
        }
    }

    /** Tao cau hinh test voi metadata hop le, khong chay workload benchmark. */
    private static RunConfig config(int timeoutSec, boolean countMode) {
        return new RunConfig("TEST", "rh", "base", "test", 12L, 4,
                0.75, 7, 0.2, 0.5, 3L, 42L, 1,
                "2g", "test-machine", timeoutSec, countMode);
    }

    /** Kiem tra danh sach pha, status, elapsed_ms va ghi chu ngay trong bo nho. */
    private static void assertExpectedPhases(Runner.RunResult result,
            String[] statuses, String[] notes) {
        assertEquals(PHASES.length, result.phases.size(), "so pha trong bo nho");
        for (int i = 0; i < PHASES.length; i++) {
            Runner.PhaseResult phase = result.phases.get(i);
            assertEquals(PHASES[i], phase.phase, "thu tu pha");
            assertEquals(statuses[i], phase.status, "status " + phase.phase);
            assertEquals(notes[i], phase.phaseNote, "phase_note " + phase.phase);
            if ("ok".equals(statuses[i])) {
                assertMatches("[0-9]+\\.[0-9]{3}", phase.elapsedMs, "elapsed_ms");
            } else {
                assertEquals("", phase.elapsedMs, "elapsed_ms phai rong khi status != ok");
            }
        }
    }

    /** Doc file CSV va kiem tra schema, metadata, trang thai va run_id duy nhat. */
    private static void assertCsv(Path csv, Runner.RunResult result, RunConfig config,
            String[] statuses, String[] notes) throws IOException {
        List<String[]> rows = readCsv(csv);
        assertEquals(CsvSchema.COLUMNS.length, rows.get(0).length, "so cot header");
        assertTrue(Arrays.equals(CsvSchema.COLUMNS, rows.get(0)), "header CSV sai schema");
        assertEquals(PHASES.length + 1, rows.size(), "so hang CSV");
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
            assertEquals(config.params + ";keytype=str12",
                    columns[CsvSchema.PARAMS_INDEX], "params");
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
                        "CSV elapsed_ms phai rong khi status != ok");
            }
        }
    }

    /** Xac nhan cac truong chua co du lieu that khong bi dien gia tri gia. */
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
            assertEquals("", columns[index], "cot chua co du lieu phai de trong");
        }
    }

    /** Doc CSV dang cac truong khong co dau phay trong du lieu test. */
    private static List<String[]> readCsv(Path csv) throws IOException {
        List<String[]> rows = new ArrayList<String[]>();
        try (BufferedReader reader = Files.newBufferedReader(csv, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                rows.add(line.split(",", -1));
            }
        }
        assertTrue(!rows.isEmpty(), "CSV phai co header");
        return rows;
    }

    /** Kiem tra so cot, run_id duy nhat, trang thai hop le va elapsed_ms. */
    private static void assertAllRowsValid(List<String[]> rows) {
        assertEquals(CsvSchema.COLUMNS.length, rows.get(0).length, "header dung so cot");
        Set<String> runIds = new HashSet<String>();
        Set<String> statuses = new HashSet<String>(Arrays.asList(
                "ok", "timeout", "crash", "oom", "wrong"));
        for (int i = 1; i < rows.size(); i++) {
            String[] columns = rows.get(i);
            assertEquals(CsvSchema.COLUMNS.length, columns.length, "so cot hang CSV");
            assertTrue(runIds.add(columns[CsvSchema.RUN_ID_INDEX]), "run_id bi trung");
            String status = columns[CsvSchema.STATUS_INDEX];
            assertTrue(statuses.contains(status), "status ngoai danh sach cho phep: " + status);
            if (!"ok".equals(status)) {
                assertEquals("", columns[CsvSchema.ELAPSED_MS_INDEX],
                        "elapsed_ms phai rong khi status != ok");
            }
            String phaseNote = columns[CsvSchema.PHASE_NOTE_INDEX];
            if ("not_started".equals(phaseNote)) {
                assertTrue("timeout".equals(status) || "crash".equals(status)
                        || "oom".equals(status), "not_started khong phu hop voi status");
            }
        }
    }

    /** Xoa rieng thu muc tam ma test da tao. */
    private static void deleteTree(Path root) throws IOException {
        Files.walkFileTree(root, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attributes)
                    throws IOException {
                file.toFile().setWritable(true);
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

    /** Xac nhan dieu kien dung va nem loi mo ta khi sai. */
    private static void assertTrue(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    /** So sanh gia tri ky vong va gia tri thuc te trong test. */
    private static void assertEquals(Object expected, Object actual, String message) {
        if (expected == null ? actual != null : !expected.equals(actual)) {
            throw new AssertionError(message + ": expected=" + expected + ", actual=" + actual);
        }
    }

    /** Xac nhan chuoi khop dinh dang bieu thuc chinh quy yeu cau. */
    private static void assertMatches(String regex, String value, String message) {
        if (value == null || !value.matches(regex)) {
            throw new AssertionError(message + " sai dinh dang: " + value);
        }
    }

    /** Chu ky cho mot ca kiem thu doc lap. */
    private interface TestCase {
        void run(Path root) throws Exception;
    }
}
