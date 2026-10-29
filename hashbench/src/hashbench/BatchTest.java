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

/** Kiem thu ma tran batch RQ1, tinh xen ke, tinh duy nhat va tinh nang resume cua batch. */
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

    /** (i) Kiem tra sinh 200 dong, phan bo 144/48/8, proc_id duy nhat, va khong co 2 dong lien tiep cung (cau hinh, thuat toan). */
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

        assertEquals(200, configs.size(), "tong so cau hinh trong ma tran");
        assertEquals(144, timeCount, "so dong thoi gian RQ1");
        assertEquals(48, countCount, "so dong count RQ1");
        assertEquals(8, clustCount, "so dong don cum rq1clust");
        assertTrue(Double.isInfinite(configs.get(0).tau), "tau batch mac dinh la inf");
        assertEquals(0.0, configs.get(0).load, "load batch mac dinh la 0");
        assertEquals(0, configs.get(0).c, "c batch mac dinh la 0");

        Set<String> procIds = new HashSet<String>();
        for (RunConfig cfg : configs) {
            assertTrue(procIds.add(cfg.procId()),
                    "proc_id phai duy nhat: " + cfg.procId());
        }
        for (int i = 0; i < configs.size() - 1; i++) {
            RunConfig first = configs.get(i);
            RunConfig second = configs.get(i + 1);
            boolean sameAlgorithm = first.algo.equals(second.algo);
            boolean sameConfig = first.p == second.p
                    && Double.compare(first.alpha, second.alpha) == 0
                    && first.dataset.equals(second.dataset);
            assertTrue(!(sameAlgorithm && sameConfig),
                    "Hai dong lien tiep khong duoc cung (cau hinh, thuat toan) tai dong "
                    + (i + 1) + ": " + first.algo + " p=" + first.p);
        }
    }

    /** Kiem tra ma tran RQ2/RQ3, so luot, proc_id, muc tai va thu tu xen ke. */
    private static void testRq23MatrixGeneration() {
            List<RunConfig> configs = new ArrayList<RunConfig>();
            for (String line : Batch.generateRq23BatchLines()) {
                RunConfig config = Batch.parseBatchLine(line, "2g", 60, "test");
                if (config != null) {
                    configs.add(config);
                }
            }
            assertEquals(360, configs.size(), "tong so cau hinh RQ2/RQ3");

            Set<String> procIds = new HashSet<String>();
            int rq2Count = 0;
            int rq3Count = 0;
            int timeCount = 0;
            int countCount = 0;
            boolean countGroupStarted = false;
            for (int i = 0; i < configs.size(); i++) {
                RunConfig config = configs.get(i);
                assertTrue(procIds.add(config.procId()),
                        "proc_id RQ2/RQ3 phai duy nhat: " + config.procId());
                assertEquals(20, config.p, "p RQ2/RQ3");
                assertEquals(1000000L, config.queries, "queries RQ2/RQ3");
                if (config.countMode) {
                    countGroupStarted = true;
                    countCount++;
                } else {
                    assertTrue(!countGroupStarted, "moi luot thoi gian phai dung truoc luot count");
                    timeCount++;
                }
                if ("rq2".equals(config.dataset)) {
                    rq2Count++;
                    assertEquals("lp-tomb", config.algo, "RQ2 chi dung lp-tomb");
                    assertEquals(0.5, config.load, "load RQ2");
                    assertTrue(Double.isInfinite(config.tau), "tau RQ2 la inf");
                } else {
                    rq3Count++;
                    assertTrue(config.load == 0.5 || config.load == 0.8,
                            "load RQ3 phai la 0.5 hoac 0.8");
                    assertTrue(config.c == 1048576 || config.c == 2097152
                            || config.c == 4194304, "C RQ3 khong hop le");
                    if ("lp-tomb".equals(config.algo)) {
                        assertEquals(config.load == 0.5 ? 0.2 : 0.1, config.tau,
                                "tau lp-tomb theo load");
                    } else {
                        assertTrue(Double.isInfinite(config.tau),
                                "tau thuat toan khong lp-tomb la inf");
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
                            "khong duoc lap config/algorithm lien ke");
                }
            }
            assertEquals(72, rq2Count, "so dong RQ2");
            assertEquals(288, rq3Count, "so dong RQ3");
            assertEquals(270, timeCount, "so luot thoi gian");
            assertEquals(90, countCount, "so luot count");
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
                    assertEquals(algorithms[i], config.algo, "thu tu thuat toan RQ3");
                    assertEquals(variants[i], config.variant, "thu tu variant RQ3");
                    assertEquals(first.load, config.load, "block RQ3 phai cung load");
                    assertEquals(first.c, config.c, "block RQ3 phai cung C");
                    assertEquals(first.seed, config.seed, "block RQ3 phai cung seed");
                    assertEquals(first.repeat, config.repeat, "block RQ3 phai cung repeat");
                }
            }
        }

    /** (ii) Chay batch nho 2 dong p=10 queries=20000 hai lan voi --resume. */
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

        // Chay lan 1: ca 2 dong deu RUN
        Batch.runBatch(batchFile, csvFile, logFile, "2g", 30, "dev", false, -1);
        List<String[]> rows1 = readCsv(csvFile);
        // Header + 3 pha sc + 3 pha lp-tomb = 7 dong
        assertEquals(7, rows1.size(), "so dong CSV sau lan 1");

        // Chay lan 2 voi cung file CSV: ca 2 dong phai SKIP
        Batch.runBatch(batchFile, csvFile, logFile, "2g", 30, "dev", false, -1);
        List<String[]> rows2 = readCsv(csvFile);
        assertEquals(rows1.size(), rows2.size(), "CSV khong duoc them dong moi khi resume");

        // Kiem tra log file
        List<String> logLines = Files.readAllLines(logFile, StandardCharsets.UTF_8);
        assertEquals(4, logLines.size(), "file log phai co 4 dong (2 RUN + 2 SKIP)");
        assertTrue(logLines.get(0).contains("\tRUN\t"), "dong log 1 la RUN");
        assertTrue(logLines.get(1).contains("\tRUN\t"), "dong log 2 la RUN");
        assertTrue(logLines.get(2).contains("\tSKIP\t"), "dong log 3 la SKIP");
        assertTrue(logLines.get(3).contains("\tSKIP\t"), "dong log 4 la SKIP");

        Batch.runBatch(batchFile, csvFile, logFile, "2g", 30, "dev", false, 1);
        logLines = Files.readAllLines(logFile, StandardCharsets.UTF_8);
        assertEquals(5, logLines.size(), "--limit 1 phai xu ly dung mot cau hinh SKIP");
        assertTrue(logLines.get(4).contains("\tSKIP\t"),
                "dong log cuoi cua luot --limit 1 la SKIP");
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
            throw new AssertionError(msg + ": mong doi " + exp + ", nhan " + act);
        }
    }
}
