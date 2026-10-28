package hashbench;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Quản lý tạo file ma trận batch RQ1 và thực thi batch tuần tự với nhật ký và resume. */
public final class Batch {
    private Batch() {
    }

    /** Thông tin cấu hình cơ sở cho một điểm trong thí nghiệm RQ1. */
    public static final class BaseConfig {
        public final String dataset;
        public final int p;
        public final double alpha;

        public BaseConfig(String dataset, int p, double alpha) {
            this.dataset = dataset;
            this.p = p;
            this.alpha = alpha;
        }
    }

    /** Danh sách 8 cấu hình duy nhất của RQ1 (thí nghiệm A và B). */
    public static List<BaseConfig> get8Configs() {
        List<BaseConfig> list = new ArrayList<BaseConfig>();
        // Thí nghiệm A: dataset=rq1a, p=20, alpha thuộc {0.5, 0.75, 0.9, 0.95}
        list.add(new BaseConfig("rq1a", 20, 0.50));
        list.add(new BaseConfig("rq1a", 20, 0.75));
        list.add(new BaseConfig("rq1a", 20, 0.90));
        list.add(new BaseConfig("rq1a", 20, 0.95));
        // Thí nghiệm B: dataset=rq1b, alpha=0.75, p thuộc {18, 21, 22, 23} (p=20 dùng chung với A)
        list.add(new BaseConfig("rq1b", 18, 0.75));
        list.add(new BaseConfig("rq1b", 21, 0.75));
        list.add(new BaseConfig("rq1b", 22, 0.75));
        list.add(new BaseConfig("rq1b", 23, 0.75));
        return list;
    }

    /** Sinh toàn bộ 200 dòng của ma trận RQ1 theo quy tắc xen kẽ. */
    public static List<String> generateRq1BatchLines() {
        List<String> lines = new ArrayList<String>();
        lines.add("# Ma trận RQ1: 200 tiến trình tuần tự, xen kẽ cấu hình và thuật toán");
        lines.add("# Gồm 8 cấu hình duy nhất (A: 4 mức alpha tại p=20; B: 4 mức p tại alpha=0.75, điểm p=20 dùng chung với A)");
        lines.add("# Nhóm 1: 144 dòng thời gian (8 cấu hình x 2 thuật toán x 3 seed x 3 repeat)");
        lines.add("# Nhóm 2: 48 dòng count (8 cấu hình x 2 thuật toán x 3 seed, repeat=1)");
        lines.add("# Nhóm 3: 8 dòng dồn cụm rq1clust (p=18 và p=20, 2 thuật toán x {thời gian, count}, seed=1, repeat=1)");

        List<BaseConfig> configs = get8Configs();

        // Nhóm 1: 144 dòng thời gian
        for (int repeat = 1; repeat <= 3; repeat++) {
            for (int seed = 1; seed <= 3; seed++) {
                boolean reverseAlgo = ((repeat + seed) % 2 != 0);
                for (BaseConfig cfg : configs) {
                    String firstAlgo = reverseAlgo ? "lp-tomb" : "sc";
                    String secondAlgo = reverseAlgo ? "sc" : "lp-tomb";
                    lines.add(formatBatchLine("RQ1", firstAlgo, "base", cfg.dataset, cfg.p, cfg.alpha, seed, repeat, 5000000L, 0));
                    lines.add(formatBatchLine("RQ1", secondAlgo, "base", cfg.dataset, cfg.p, cfg.alpha, seed, repeat, 5000000L, 0));
                }
            }
        }

        // Nhóm 2: 48 dòng count
        for (int seed = 1; seed <= 3; seed++) {
            boolean reverseAlgo = ((1 + seed) % 2 != 0);
            for (BaseConfig cfg : configs) {
                String firstAlgo = reverseAlgo ? "lp-tomb" : "sc";
                String secondAlgo = reverseAlgo ? "sc" : "lp-tomb";
                lines.add(formatBatchLine("RQ1", firstAlgo, "base", cfg.dataset, cfg.p, cfg.alpha, seed, 1, 5000000L, 1));
                lines.add(formatBatchLine("RQ1", secondAlgo, "base", cfg.dataset, cfg.p, cfg.alpha, seed, 1, 5000000L, 1));
            }
        }

        // Nhóm 3: 8 dòng dồn cụm rq1clust
        lines.add(formatBatchLine("RQ1", "sc", "base", "rq1clust", 18, 0.75, 1, 1, 5000000L, 0));
        lines.add(formatBatchLine("RQ1", "lp-tomb", "base", "rq1clust", 18, 0.75, 1, 1, 5000000L, 0));
        lines.add(formatBatchLine("RQ1", "sc", "base", "rq1clust", 20, 0.75, 1, 1, 5000000L, 0));
        lines.add(formatBatchLine("RQ1", "lp-tomb", "base", "rq1clust", 20, 0.75, 1, 1, 5000000L, 0));
        lines.add(formatBatchLine("RQ1", "sc", "base", "rq1clust", 18, 0.75, 1, 1, 5000000L, 1));
        lines.add(formatBatchLine("RQ1", "lp-tomb", "base", "rq1clust", 18, 0.75, 1, 1, 5000000L, 1));
        lines.add(formatBatchLine("RQ1", "sc", "base", "rq1clust", 20, 0.75, 1, 1, 5000000L, 1));
        lines.add(formatBatchLine("RQ1", "lp-tomb", "base", "rq1clust", 20, 0.75, 1, 1, 5000000L, 1));

        return lines;
    }

    /** Sinh 360 tiến trình RQ2/RQ3: 270 lượt thời gian trước 90 lượt count. */
    public static List<String> generateRq23BatchLines() {
        List<String> lines = new ArrayList<String>();
        lines.add("# RQ2/RQ3: M=2^20, block=5000 chu ky (10^4 thao tac); moi moc C chay lai tu dau.");
        lines.add("# C_RQ2={0,262144,524288,1048576,2097152,4194304}; C_RQ3={1048576,2097152,4194304}.");
        lines.add("# N=round(load*2^p); query tren bang sau workload, RQ2 them bang doi chung.");

        int[] rq2Cycles = {0, 262144, 524288, 1048576, 2097152, 4194304};
        int[] rq3Cycles = {1048576, 2097152, 4194304};
        double[] loads = {0.5, 0.8};

        for (int repeat = 1; repeat <= 3; repeat++) {
            for (int seed = 1; seed <= 3; seed++) {
                for (int cycles : rq2Cycles) {
                    lines.add(formatRq23Line("RQ2", "lp-tomb", "base", "rq2",
                            0.5, cycles, Double.POSITIVE_INFINITY, seed, repeat, 0));
                }
            }
        }
        for (int repeat = 1; repeat <= 3; repeat++) {
            for (int seed = 1; seed <= 3; seed++) {
                for (double load : loads) {
                    for (int cycles : rq3Cycles) {
                        addRq3Algorithms(lines, load, cycles, seed, repeat, 0);
                    }
                }
            }
        }
        for (int seed = 1; seed <= 3; seed++) {
            for (int cycles : rq2Cycles) {
                lines.add(formatRq23Line("RQ2", "lp-tomb", "base", "rq2",
                        0.5, cycles, Double.POSITIVE_INFINITY, seed, 1, 1));
            }
        }
        for (int seed = 1; seed <= 3; seed++) {
            for (double load : loads) {
                for (int cycles : rq3Cycles) {
                    addRq3Algorithms(lines, load, cycles, seed, 1, 1);
                }
            }
        }
        return lines;
    }

    private static void addRq3Algorithms(List<String> lines, double load, int cycles,
            int seed, int repeat, int count) {
        String[] algos = {"lp-tomb", "lp-backshift", "rh", "rh"};
        String[] variants = {"base", "base", "base", "noearly"};
        double[] taus = {load == 0.5 ? 0.2 : 0.1,
            Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY,
            Double.POSITIVE_INFINITY};
        boolean reverse = ((repeat + seed) % 2 != 0);
        for (int i = 0; i < algos.length; i++) {
            int index = reverse ? algos.length - 1 - i : i;
            lines.add(formatRq23Line("RQ3", algos[index], variants[index], "rq3",
                    load, cycles, taus[index], seed, repeat, count));
        }
    }

    private static String formatRq23Line(String rq, String algo, String variant,
            String dataset, double load, int cycles, double tau, long seed,
            int repeat, int count) {
        return "rq=" + rq + " algo=" + algo + " variant=" + variant + " dataset=" + dataset
                + " p=20 alpha=0.00 load=" + String.format(Locale.US, "%.2f", load)
                + " c=" + cycles + " tau=" + (Double.isInfinite(tau)
                        ? "inf" : String.format(Locale.US, "%.1f", tau))
                + " seed=" + seed + " repeat=" + repeat
                + " queries=1000000 count=" + count;
    }

    private static String formatBatchLine(String rq, String algo, String variant, String dataset,
            int p, double alpha, long seed, int repeat, long queries, int count) {
        return "rq=" + rq + " algo=" + algo + " variant=" + variant + " dataset=" + dataset
                + " p=" + p + " alpha=" + String.format(Locale.US, "%.2f", alpha)
                + " seed=" + seed + " repeat=" + repeat + " queries=" + queries + " count=" + count;
    }

    /** Ghi file ma trận ra đĩa. */
    public static void writeBatchFile(Path outPath) throws IOException {
        Path parent = outPath.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        List<String> lines = generateRq1BatchLines();
        try (BufferedWriter writer = Files.newBufferedWriter(outPath, StandardCharsets.UTF_8)) {
            for (String line : lines) {
                writer.write(line);
                writer.newLine();
            }
        }
    }

    /** Ghi ma trận RQ2/RQ3 ra đĩa. */
    public static void writeRq23BatchFile(Path outPath) throws IOException {
        Path parent = outPath.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        try (BufferedWriter writer = Files.newBufferedWriter(outPath, StandardCharsets.UTF_8)) {
            for (String line : generateRq23BatchLines()) {
                writer.write(line);
                writer.newLine();
            }
        }
    }

    /** Phân tích một dòng batch thành RunConfig. */
    public static RunConfig parseBatchLine(String line, String defaultXmx, int defaultTimeoutSec, String defaultMachineId) {
        String trimmed = line.trim();
        if (trimmed.length() == 0 || trimmed.startsWith("#")) {
            return null;
        }
        String[] tokens = trimmed.split("\\s+");
        Map<String, String> kv = new HashMap<String, String>();
        for (String token : tokens) {
            int eq = token.indexOf('=');
            if (eq > 0) {
                kv.put(token.substring(0, eq), token.substring(eq + 1));
            }
        }

        String rq = kv.get("rq");
        String algo = kv.get("algo");
        String variant = kv.containsKey("variant") ? kv.get("variant") : "base";
        String dataset = kv.get("dataset");
        int p = Integer.parseInt(kv.get("p"));
        double alpha = kv.containsKey("alpha") ? Double.parseDouble(kv.get("alpha")) : 0.0;
        long seed = Long.parseLong(kv.get("seed"));
        int repeat = kv.containsKey("repeat") ? Integer.parseInt(kv.get("repeat")) : 1;
        long queries = kv.containsKey("queries") ? Long.parseLong(kv.get("queries")) : 1000000L;
        boolean count = "1".equals(kv.get("count"));
        double tau = kv.containsKey("tau") ? ("inf".equals(kv.get("tau")) ? Double.POSITIVE_INFINITY : Double.parseDouble(kv.get("tau"))) : Double.POSITIVE_INFINITY;
        double load = kv.containsKey("load") ? Double.parseDouble(kv.get("load")) : 0.0;
        int cycles = kv.containsKey("c") ? Integer.parseInt(kv.get("c")) : 0;
        String xmx = kv.containsKey("xmx") ? kv.get("xmx") : defaultXmx;
        int timeoutSec = kv.containsKey("timeout-sec") ? Integer.parseInt(kv.get("timeout-sec")) : defaultTimeoutSec;
        String machineId = kv.containsKey("machine-id") ? kv.get("machine-id") : defaultMachineId;

        return new RunConfig(rq, algo, variant, dataset, 0L, p, alpha, cycles,
                tau, load, queries, seed, repeat, xmx, machineId, timeoutSec, count);
    }

    /** Đọc file batch thành danh sách RunConfig. */
    public static List<RunConfig> readBatchFile(Path batchPath, String defaultXmx, int defaultTimeoutSec, String defaultMachineId) throws IOException {
        List<RunConfig> list = new ArrayList<RunConfig>();
        try (BufferedReader reader = Files.newBufferedReader(batchPath, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                RunConfig cfg = parseBatchLine(line, defaultXmx, defaultTimeoutSec, defaultMachineId);
                if (cfg != null) {
                    list.add(cfg);
                }
            }
        }
        return list;
    }

    /** Kiểm tra xem một cấu hình đã hoàn thành trong CSV đích chưa để resume. */
    public static boolean isConfigCompletedInCsv(RunConfig config, Path csvPath) throws IOException {
        if (!Files.exists(csvPath)) {
            return false;
        }
        List<String> requiredPhases = RunConfig.phasesForDataset(config.dataset, config.phaseNames);
        Map<String, String> phaseStatus = new HashMap<String, String>();
        try (BufferedReader reader = Files.newBufferedReader(csvPath, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.trim().length() == 0) continue;
                String[] cells = CsvValidator.splitCsvLine(line);
                if (cells.length > CsvSchema.PROC_ID_INDEX && cells[CsvSchema.PROC_ID_INDEX].equals(config.procId())) {
                    String ph = cells.length > CsvSchema.PHASE_INDEX ? cells[CsvSchema.PHASE_INDEX] : "";
                    String st = cells.length > CsvSchema.STATUS_INDEX ? cells[CsvSchema.STATUS_INDEX] : "";
                    if (ph.length() > 0) {
                        phaseStatus.put(ph, st);
                    }
                }
            }
        }
        for (String reqPhase : requiredPhases) {
            String st = phaseStatus.get(reqPhase);
            if (st == null || (!"ok".equals(st) && !"timeout".equals(st) && !"oom".equals(st))) {
                return false;
            }
        }
        return true;
    }

    /** Chạy batch các cấu hình từ file với resume, log và thống kê. */
    public static void runBatch(Path batchPath, Path csvPath, Path logPath, String defaultXmx,
            int defaultTimeoutSec, String defaultMachineId, boolean official, int limit) throws Exception {
        if (!Files.exists(batchPath)) {
            throw new IllegalArgumentException("Không tìm thấy file batch: " + batchPath);
        }

        List<RunConfig> configs = readBatchFile(batchPath, defaultXmx, defaultTimeoutSec, defaultMachineId);
        if (configs.isEmpty()) {
            System.out.println("File batch không có cấu hình nào cần chạy.");
            return;
        }

        // Kiểm tra đầu batch: đọc cả file, nếu có hai dòng cho cùng proc_id thì dừng và báo
        Set<String> seenProcIds = new HashSet<String>();
        for (RunConfig cfg : configs) {
            String pid = cfg.procId();
            if (!seenProcIds.add(pid)) {
                throw new IllegalStateException("Lỗi kiểm tra batch: phát hiện proc_id trùng lặp trong file batch: " + pid);
            }
        }

        // Kiểm tra official một lần ở đầu batch
        if (official) {
            RunConfig sample = configs.get(0);
            // Thử khởi tạo trạng thái git qua runWithCsv giả định
            try {
                // Runner.runWithCsv sẽ kiểm tra gitState nếu official = true
                System.out.println("[OFFICIAL] Kiểm tra tính hợp lệ của cây git trước batch...");
            } catch (Exception e) {
                throw e;
            }
        }

        Path parent = csvPath.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        if (logPath.getParent() != null) {
            Files.createDirectories(logPath.getParent());
        }

        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);
        int total = configs.size();
        int processedCount = 0;
        int runCount = 0;
        int skipCount = 0;
        int okCount = 0;
        int timeoutCount = 0;
        int crashCount = 0;
        int oomCount = 0;
        long totalRunWallNanos = 0L;

        long batchStartNanos = System.nanoTime();
        System.out.println("Bắt đầu thực thi batch: " + total + " tiến trình (limit=" + (limit > 0 ? limit : "none") + ")...");

        for (int i = 0; i < total; i++) {
            if (limit > 0 && processedCount >= limit) {
                System.out.println("Đã đạt giới hạn --limit " + limit + ". Dừng batch.");
                break;
            }
            processedCount++;

            RunConfig cfg = configs.get(i);
            String procId = cfg.procId();
            int index = i + 1;

            if (isConfigCompletedInCsv(cfg, csvPath)) {
                skipCount++;
                String now = dateFormat.format(new Date());
                String logLine = now + "\t" + index + "/" + total + "\t" + procId + "\tSKIP\talready_completed\t0.000";
                appendLog(logPath, logLine);
                System.out.printf(Locale.US, "[%d/%d] SKIP %s%n", index, total, procId);
                continue;
            }

            runCount++;
            long runStart = System.nanoTime();
            String phaseStatuses = "";
            String wallMsStr = "";
            boolean isOk = true;
            boolean isTimeout = false;
            boolean isCrash = false;
            boolean isOom = false;

            try {
                Runner.RunResult res = Runner.runWithCsv(cfg, csvPath, Paths.get("."), official);
                wallMsStr = res.wallMs;
                StringBuilder sb = new StringBuilder();
                for (int pIdx = 0; pIdx < res.phases.size(); pIdx++) {
                    Runner.PhaseResult pr = res.phases.get(pIdx);
                    if (pIdx > 0) sb.append(';');
                    sb.append(pr.phase).append('=').append(pr.status);
                    if ("timeout".equals(pr.status)) isTimeout = true;
                    else if ("crash".equals(pr.status)) isCrash = true;
                    else if ("oom".equals(pr.status)) isOom = true;
                    else if (!"ok".equals(pr.status)) isOk = false;
                }
                phaseStatuses = sb.toString();
                if (res.timedOut) isTimeout = true;
            } catch (Throwable t) {
                isCrash = true;
                phaseStatuses = "error=" + t.getMessage();
                wallMsStr = String.format(Locale.US, "%.3f", (System.nanoTime() - runStart) / 1e6);
                System.err.println("Lỗi tiến trình " + procId + ": " + t.getMessage());
            }

            long runWall = System.nanoTime() - runStart;
            totalRunWallNanos += runWall;

            if (isTimeout) timeoutCount++;
            else if (isOom) oomCount++;
            else if (isCrash) crashCount++;
            else if (isOk) okCount++;

            String now = dateFormat.format(new Date());
            String logLine = now + "\t" + index + "/" + total + "\t" + procId + "\tRUN\t" + phaseStatuses + "\t" + wallMsStr;
            appendLog(logPath, logLine);

            double avgWallSec = (double) totalRunWallNanos / runCount / 1e9;
            int remaining = total - index;
            double etaSec = avgWallSec * remaining;
            System.out.printf(Locale.US, "[%d/%d] RUN %s (%s, wall=%s ms, ETA ~%.1f s)%n",
                    index, total, procId, phaseStatuses, wallMsStr, etaSec);
        }

        long batchTotalNanos = System.nanoTime() - batchStartNanos;
        System.out.println("\n===== TỔNG KẾT BATCH =====");
        System.out.printf(Locale.US, "Tổng tiến trình: %d | RUN: %d | SKIP: %d%n", total, runCount, skipCount);
        System.out.printf(Locale.US, "Trạng thái: ok=%d | timeout=%d | crash=%d | oom=%d%n", okCount, timeoutCount, crashCount, oomCount);
        System.out.printf(Locale.US, "Tổng thời gian batch: %.2f s%n", batchTotalNanos / 1e9);
        System.out.println("==========================");
    }

    private static void appendLog(Path logPath, String line) {
        try (BufferedWriter writer = Files.newBufferedWriter(logPath, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.APPEND)) {
            writer.write(line);
            writer.newLine();
        } catch (IOException ignored) {
        }
    }
}
