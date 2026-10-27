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

        // Kiểm tra proc_id duy nhất cho toàn bộ 200 dòng
        Set<String> procIds = new HashSet<String>();
        for (RunConfig cfg : configs) {
            String pid = cfg.procId();
            assertTrue(procIds.add(pid), "proc_id phải duy nhất: " + pid);
        }

        // Kiểm tra không có hai dòng liên tiếp cùng (cấu hình, thuật toán)
        for (int i = 0; i < configs.size() - 1; i++) {
            RunConfig c1 = configs.get(i);
            RunConfig c2 = configs.get(i + 1);
            boolean sameAlgo = c1.algo.equals(c2.algo);
            boolean sameConfig = c1.p == c2.p && Double.compare(c1.alpha, c2.alpha) == 0 && c1.dataset.equals(c2.dataset);
            assertTrue(!(sameAlgo && sameConfig),
                    "Hai dòng liên tiếp không được cùng (cấu hình, thuật toán) tại dòng " + (i + 1) + ": " + c1.algo + " p=" + c1.p);
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
