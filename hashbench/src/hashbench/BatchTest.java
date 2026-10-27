package hashbench;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Kiểm thử ma trận batch RQ1, tính xen kẽ, tính duy nhất và tính năng resume của batch. */
public final class BatchTest {
    private BatchTest() {
    }

    public static void main(String[] args) throws Exception {
        Path root = Files.createTempDirectory("hashbench-batch-test-");
        try {
            testMatrixGeneration();
            testRq23MatrixGeneration();
            testBatchExecutionAndResume(root);
            System.out.println("BatchTest: ALL PASS");
        } finally {
            deleteTree(root);
        }
    }

    /** (i) Kiểm tra sinh 200 dòng, phân bổ 144/48/8, proc_id duy nhất, và không có 2 dòng liên tiếp cùng (cấu hình, thuật toán). */
    private static void testMatrixGeneration() {
        List<String> rawLines = Batch.generateRq1BatchLines();
        List<RunConfig> configs = new ArrayList<RunConfig>();
        int timeCount = 0;
        int countCount = 0;
        int clustCount = 0;

        for (String line : rawLines) {
            RunConfig cfg = Batch.parseBatchLine(line, "2g", 60, "test");
            if (cfg != null) {
                configs.add(cfg);
                if ("rq1clust".equals(cfg.dataset)) {
                    clustCount++;
                } else if (cfg.countMode) {
                    countCount++;
                } else {
                    timeCount++;
                }
            }
        }

        assertEquals(200, configs.size(), "tổng số cấu hình trong ma trận");
        assertEquals(144, timeCount, "số dòng thời gian RQ1");
        assertEquals(48, countCount, "số dòng count RQ1");
        assertEquals(8, clustCount, "số dòng dồn cụm rq1clust");
        assertTrue(Double.isInfinite(configs.get(0).tau), "tau batch mặc định là inf");
        assertEquals(0.0, configs.get(0).load, "load batch mặc định là 0");
        assertEquals(0, configs.get(0).c, "c batch mặc định là 0");

        Set<String> procIds = new HashSet<String>();
        for (RunConfig cfg : configs) {
            assertTrue(procIds.add(cfg.procId()),
                    "proc_id phải duy nhất: " + cfg.procId());
        }
        for (int i = 0; i < configs.size() - 1; i++) {
            RunConfig first = configs.get(i);
            RunConfig second = configs.get(i + 1);
            boolean sameAlgorithm = first.algo.equals(second.algo);
            boolean sameConfig = first.p == second.p
                    && Double.compare(first.alpha, second.alpha) == 0
                    && first.dataset.equals(second.dataset);
            assertTrue(!(sameAlgorithm && sameConfig),
                    "Hai dòng liên tiếp không được cùng (cấu hình, thuật toán) tại dòng "
                    + (i + 1) + ": " + first.algo + " p=" + first.p);
        }
    }

    /** Kiểm tra ma trận RQ2/RQ3, số lượt, proc_id, mức tải và thứ tự xen kẽ. */
    private static void testRq23MatrixGeneration() {
            List<RunConfig> configs = new ArrayList<RunConfig>();
            for (String line : Batch.generateRq23BatchLines()) {
                RunConfig config = Batch.parseBatchLine(line, "2g", 60, "test");
                if (config != null) {
                    configs.add(config);
                }
            }
            assertEquals(360, configs.size(), "tổng số cấu hình RQ2/RQ3");

            Set<String> procIds = new HashSet<String>();
            int rq2Count = 0;
            int rq3Count = 0;
            int timeCount = 0;
            int countCount = 0;
            boolean countGroupStarted = false;
            for (int i = 0; i < configs.size(); i++) {
                RunConfig config = configs.get(i);
                assertTrue(procIds.add(config.procId()),
                        "proc_id RQ2/RQ3 phải duy nhất: " + config.procId());
                assertEquals(20, config.p, "p RQ2/RQ3");
                assertEquals(1000000L, config.queries, "queries RQ2/RQ3");
                if (config.countMode) {
                    countGroupStarted = true;
                    countCount++;
                } else {
                    assertTrue(!countGroupStarted, "mọi lượt thời gian phải đứng trước lượt count");
                    timeCount++;
                }
                if ("rq2".equals(config.dataset)) {
                    rq2Count++;
                    assertEquals("lp-tomb", config.algo, "RQ2 chỉ dùng lp-tomb");
                    assertEquals(0.5, config.load, "load RQ2");
                    assertTrue(Double.isInfinite(config.tau), "tau RQ2 là inf");
                } else {
                    rq3Count++;
                    assertTrue(config.load == 0.5 || config.load == 0.8,
                            "load RQ3 phải là 0.5 hoặc 0.8");
                    assertTrue(config.c == 1048576 || config.c == 2097152
                            || config.c == 4194304, "C RQ3 không hợp lệ");
                    if ("lp-tomb".equals(config.algo)) {
                        assertEquals(config.load == 0.5 ? 0.2 : 0.1, config.tau,
                                "tau lp-tomb theo load");
                    } else {
                        assertTrue(Double.isInfinite(config.tau),
                                "tau thuật toán không lp-tomb là inf");
                    }
                }
                if (i > 0) {
                    RunConfig previous = configs.get(i - 1);
                    boolean sameConfiguration = previous.dataset.equals(config.dataset)
                            && previous.p == config.p
                            && Double.compare(previous.load, config.load) == 0
                            && previous.c == config.c
                            && previous.seed == config.seed
                            && previous.repeat == config.repeat
                            && previous.countMode == config.countMode;
                    assertTrue(!(sameConfiguration && previous.algo.equals(config.algo)
                            && previous.variant.equals(config.variant)),
                            "không được lặp config/algorithm liền kề");
                }
            }
            assertEquals(72, rq2Count, "số dòng RQ2");
            assertEquals(288, rq3Count, "số dòng RQ3");
            assertEquals(270, timeCount, "số lượt thời gian");
            assertEquals(90, countCount, "số lượt count");
            verifyRq3AlgorithmOrder(configs, 54, 216);
            verifyRq3AlgorithmOrder(configs, 288, 72);
        }

        private static void verifyRq3AlgorithmOrder(List<RunConfig> configs, int start,
                int count) {
            String[] normal = {"lp-tomb", "lp-backshift", "rh", "rh"};
            String[] reversed = {"rh", "rh", "lp-backshift", "lp-tomb"};
            String[] normalVariants = {"base", "base", "base", "noearly"};
            String[] reversedVariants = {"noearly", "base", "base", "base"};
            for (int offset = 0; offset < count; offset += 4) {
                RunConfig first = configs.get(start + offset);
                boolean reverse = ((first.repeat + first.seed) % 2 != 0);
                String[] algorithms = reverse ? reversed : normal;
                String[] variants = reverse ? reversedVariants : normalVariants;
                for (int i = 0; i < 4; i++) {
                    RunConfig config = configs.get(start + offset + i);
                    assertEquals(algorithms[i], config.algo, "thứ tự thuật toán RQ3");
                    assertEquals(variants[i], config.variant, "thứ tự variant RQ3");
                    assertEquals(first.load, config.load, "block RQ3 phải cùng load");
                    assertEquals(first.c, config.c, "block RQ3 phải cùng C");
                    assertEquals(first.seed, config.seed, "block RQ3 phải cùng seed");
                    assertEquals(first.repeat, config.repeat, "block RQ3 phải cùng repeat");
                }
            }
        }

    /** (ii) Chạy batch nhỏ 2 dòng p=10 queries=20000 hai lần với --resume. */
    private static void testBatchExecutionAndResume(Path root) throws Exception {
        Path batchFile = root.resolve("mini_batch.txt");
        Path csvFile = root.resolve("mini_results.csv");
        Path logFile = root.resolve("mini_log.txt");

        try (BufferedWriter bw = Files.newBufferedWriter(batchFile, StandardCharsets.UTF_8)) {
            bw.write("rq=RQ1 algo=sc variant=base dataset=rq1a p=10 alpha=0.75 seed=1 repeat=1 queries=20000 count=0");
            bw.newLine();
            bw.write("rq=RQ1 algo=lp-tomb variant=base dataset=rq1a p=10 alpha=0.75 seed=1 repeat=1 queries=20000 count=0");
            bw.newLine();
        }

        // Chạy lần 1: cả 2 dòng đều RUN
        Batch.runBatch(batchFile, csvFile, logFile, "2g", 30, "dev", false, -1);
        List<String[]> rows1 = readCsv(csvFile);
        // Header + 3 pha sc + 3 pha lp-tomb = 7 dòng
        assertEquals(7, rows1.size(), "số dòng CSV sau lần 1");

        // Chạy lần 2 với cùng file CSV: cả 2 dòng phải SKIP
        Batch.runBatch(batchFile, csvFile, logFile, "2g", 30, "dev", false, -1);
        List<String[]> rows2 = readCsv(csvFile);
        assertEquals(rows1.size(), rows2.size(), "CSV không được thêm dòng mới khi resume");

        // Kiểm tra log file
        List<String> logLines = Files.readAllLines(logFile, StandardCharsets.UTF_8);
        assertEquals(4, logLines.size(), "file log phải có 4 dòng (2 RUN + 2 SKIP)");
        assertTrue(logLines.get(0).contains("\tRUN\t"), "dòng log 1 là RUN");
        assertTrue(logLines.get(1).contains("\tRUN\t"), "dòng log 2 là RUN");
        assertTrue(logLines.get(2).contains("\tSKIP\t"), "dòng log 3 là SKIP");
        assertTrue(logLines.get(3).contains("\tSKIP\t"), "dòng log 4 là SKIP");
    }

    private static List<String[]> readCsv(Path file) throws IOException {
        List<String[]> rows = new ArrayList<String[]>();
        try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.trim().length() == 0) continue;
                rows.add(CsvValidator.splitCsvLine(line));
            }
        }
        return rows;
    }

    private static void deleteTree(Path root) throws IOException {
        if (!Files.exists(root)) return;
        Files.walkFileTree(root, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }
            @Override
            public FileVisitResult postVisitDirectory(Path directory, IOException exception) throws IOException {
                Files.delete(directory);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private static void assertTrue(boolean cond, String msg) {
        if (!cond) throw new AssertionError(msg);
    }

    private static void assertEquals(Object exp, Object act, String msg) {
        if (exp == null ? act != null : !exp.equals(act)) {
            throw new AssertionError(msg + ": mong đợi " + exp + ", nhận " + act);
        }
    }
}
