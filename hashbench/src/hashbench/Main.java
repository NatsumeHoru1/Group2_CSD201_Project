package hashbench;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.nio.file.Paths;

/** Điểm vào CLI cho runner, chế độ đếm và tự kiểm tra. */
public final class Main {
    public static boolean countMode = false;

    private Main() {
    }

    /** Chọn chế độ trước khi lớp Counters được khởi tạo. */
    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            printUsage();
            System.exit(2);
            return;
        }

        if (hasFlag(args, "--rerun")) {
            runRerun(args);
            return;
        }

        if (hasFlag(args, "--batch-gen-rq1")) {
            runBatchGenRq1(args);
            return;
        }

        if (hasFlag(args, "--batch")) {
            runBatch(args);
            return;
        }

        countMode = "--count".equals(args[0])
                || ("--bench".equals(args[0]) && hasFlag(args, "--count"));
        if ("--selftest".equals(args[0])) {
            selfTest();
        } else if ("--run".equals(args[0]) || "--count".equals(args[0])) {
            runStub(args);
        } else if ("--bench".equals(args[0])) {
            runBench(args);
        } else if ("--phase-child".equals(args[0])) {
            runPhaseChild(args);
        } else if ("--measure-child".equals(args[0])) {
            runMeasureChild(args);
        } else {
            printUsage();
            System.exit(2);
        }
    }

    /** In thông tin JVM và xác nhận thao tác cơ bản trên bảng stub. */
    private static void selfTest() {
        System.out.println("java.version=" + System.getProperty("java.version"));
        System.out.println("java.home=" + System.getProperty("java.home"));
        runStubCheck();
    }

    /** Chạy các pha giả với timeout mặc định hoặc giá trị lấy từ CLI. */
    private static void runStub(String[] args) throws Exception {
        int timeoutSec = parseTimeout(args);
        String machineId = parseMachineId(args);
        Counters.reset();
        RunConfig config = new RunConfig("TEST", "rh", "base", "test", 0L,
                1, 0.0, 0, 0.0, 0.0, 0L, 0L, 1, "2g", machineId,
                timeoutSec, countMode);
        String status = Runner.run(config);
        System.out.println("status=" + status);
        if (!"ok".equals(status)) {
            System.exit(1);
        }
    }

    /** Phân tích --timeout-sec và dùng 60 giây nếu người chạy không chỉ định. */
    private static int parseTimeout(String[] args) {
        int timeoutSec = 60;
        for (int i = 1; i < args.length; i++) {
            if ("--timeout-sec".equals(args[i])) {
                if (i + 1 >= args.length) {
                    throw new IllegalArgumentException("--timeout-sec cần số giây");
                }
                timeoutSec = Integer.parseInt(args[++i]);
                if (timeoutSec <= 0) {
                    throw new IllegalArgumentException("--timeout-sec phải lớn hơn 0");
                }
            } else if ("--machine-id".equals(args[i])) {
                if (i + 1 >= args.length) {
                    throw new IllegalArgumentException("--machine-id cần một giá trị");
                }
                i++;
            } else {
                throw new IllegalArgumentException("Tham số không hỗ trợ: " + args[i]);
            }
        }
        return timeoutSec;
    }

    /** Đọc machine id từ đối số CLI; mặc định là chuỗi rỗng. */
    private static String parseMachineId(String[] args) {
        String machineId = "";
        for (int i = 1; i < args.length; i++) {
            if ("--machine-id".equals(args[i])) {
                if (i + 1 >= args.length) {
                    throw new IllegalArgumentException("--machine-id cần một giá trị");
                }
                machineId = args[++i];
            }
        }
        return machineId;
    }

    /** Chạy cấu hình RQ1 từ CLI và in kết quả từng pha đã thu hoạch. */
    private static void runBench(String[] args) throws Exception {
        Map<String, String> options = parseOptions(args, 1);
        boolean count = hasFlag(args, "--count");
        boolean profile = hasFlag(args, "--profile");
        boolean resume = hasFlag(args, "--resume");
        String rq = required(options, "--rq");
        String algo = required(options, "--algo");
        String variant = required(options, "--variant");
        String dataset = required(options, "--dataset");
        int p = Integer.parseInt(required(options, "--p"));
        double alpha = options.containsKey("--alpha")
                ? Double.parseDouble(options.get("--alpha"))
                : ("rq2".equals(dataset) || "rq3".equals(dataset)
                        ? 0.0 : Double.parseDouble(required(options, "--alpha")));
        double tau = options.containsKey("--tau")
                ? parseTau(options.get("--tau")) : Double.POSITIVE_INFINITY;
        long seed = Long.parseLong(required(options, "--seed"));
        long queries = Long.parseLong(required(options, "--queries"));
        int repeat = options.containsKey("--repeat")
                ? Integer.parseInt(options.get("--repeat")) : 1;
        String xmx = options.containsKey("--xmx") ? options.get("--xmx") : "2g";
        int timeoutSec = options.containsKey("--timeout-sec")
                ? Integer.parseInt(options.get("--timeout-sec")) : 60;
        String machineId = options.containsKey("--machine-id")
                ? options.get("--machine-id") : "";
        String out = options.containsKey("--out")
                ? options.get("--out") : "results/raw/dev/bench.csv";
        String phases = options.get("--phases");
        double load = options.containsKey("--load")
                ? Double.parseDouble(options.get("--load")) : 0.0;
        int c = options.containsKey("--c") ? Integer.parseInt(options.get("--c")) : 0;
        RunConfig config = new RunConfig(rq, algo, variant, dataset, 0L, p, alpha,
                c, tau, load, queries, seed, repeat, xmx, machineId, timeoutSec,
                count, phases).withProfile(profile
                        && !"rq2".equals(dataset) && !"rq3".equals(dataset));

        java.nio.file.Path outPath = Paths.get(out);
        if (resume && java.nio.file.Files.exists(outPath)) {
            List<String> requiredPhases = RunConfig.phasesForDataset(config.dataset, config.phaseNames);
            Map<String, String> phaseStatus = new HashMap<String, String>();
            try (java.io.BufferedReader reader = java.nio.file.Files.newBufferedReader(
                    outPath, java.nio.charset.StandardCharsets.UTF_8)) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.trim().length() == 0) continue;
                    String[] cells = CsvValidator.splitCsvLine(line);
                    if (cells.length > CsvSchema.PROC_ID_INDEX
                            && cells[CsvSchema.PROC_ID_INDEX].equals(config.procId())) {
                        String ph = cells.length > CsvSchema.PHASE_INDEX ? cells[CsvSchema.PHASE_INDEX] : "";
                        String st = cells.length > CsvSchema.STATUS_INDEX ? cells[CsvSchema.STATUS_INDEX] : "";
                        if (ph.length() > 0) {
                            phaseStatus.put(ph, st);
                        }
                    }
                }
            }
            boolean allCompleted = true;
            for (String reqPhase : requiredPhases) {
                String st = phaseStatus.get(reqPhase);
                if (st == null || (!"ok".equals(st) && !"timeout".equals(st) && !"oom".equals(st))) {
                    allCompleted = false;
                    break;
                }
            }
            if (allCompleted) {
                System.out.println("SKIP " + config.procId());
                return;
            }
            if (!phaseStatus.isEmpty()) {
                System.err.println("CẢNH BÁO: run_id trùng do chạy lại proc_id " + config.procId());
            }
        }

        Runner.RunResult result;
        try {
            result = Runner.runWithCsv(config, outPath, Paths.get("."),
                    options.containsKey("--official"));
        } catch (Runner.OfficialRunException exception) {
            System.err.println(exception.getMessage());
            System.exit(2);
            return;
        }
        for (Runner.PhaseResult phase : result.phases) {
            System.out.println("phase=" + phase.phase
                    + " status=" + phase.status
                    + " elapsed_ms=" + phase.elapsedMs
                    + " output_check=" + phase.outputCheck);
        }
    }

    /** Phân tích cờ có hoặc không có giá trị theo dạng --name value. */
    private static Map<String, String> parseOptions(String[] args, int start) {
        Map<String, String> options = new HashMap<String, String>();
        for (int i = start; i < args.length; i++) {
            String name = args[i];
            if ("--count".equals(name) || "--official".equals(name) || "--profile".equals(name)
                    || "--resume".equals(name)) {
                options.put(name, "true");
            } else if (name.startsWith("--")) {
                if (i + 1 >= args.length || args[i + 1].startsWith("--")) {
                    throw new IllegalArgumentException(name + " cần giá trị");
                }
                options.put(name, args[++i]);
            } else {
                throw new IllegalArgumentException("Tham số không hợp lệ: " + name);
            }
        }
        return options;
    }

    /** Trả về tham số bắt buộc hoặc báo rõ tên tham số bị thiếu. */
    private static String required(Map<String, String> options, String name) {
        String value = options.get(name);
        if (value == null) {
            throw new IllegalArgumentException("Thiếu tham số " + name);
        }
        return value;
    }

    /** Đọc tau từ CLI; "inf" là ký hiệu duy nhất cho vô hạn dương. */
    private static double parseTau(String value) {
        if ("inf".equals(value)) {
            return Double.POSITIVE_INFINITY;
        }
        double tau = Double.parseDouble(value);
        if (Double.isNaN(tau) || Double.isInfinite(tau) || tau < 0.0) {
            throw new IllegalArgumentException("--tau phải là inf hoặc số hữu hạn >= 0");
        }
        return tau;
    }

    /** Kiểm tra một cờ boolean có xuất hiện trong dòng lệnh hay không. */
    private static boolean hasFlag(String[] args, String flag) {
        for (int i = 0; i < args.length; i++) {
            if (flag.equals(args[i])) {
                return true;
            }
        }
        return false;
    }

    /** Chạy danh sách pha trong tiến trình con và ghi log sau từng pha. */
    private static void runPhaseChild(String[] args) throws Exception {
        if (args.length < 4) {
            throw new IllegalArgumentException(
                    "Cú pháp: --phase-child <proc_id> <phase...> [tùy chọn test]");
        }
        String procId = args[1];
        List<String> phases = new ArrayList<String>();
        String sleepPhase = null;
        long sleepSeconds = 0L;
        String exitPhase = null;
        int exitCode = 1;
        String oomPhase = null;
        boolean childCountMode = false;

        int i = 2;
        while (i < args.length && !args[i].startsWith("--")) {
            phases.add(args[i++]);
        }
        while (i < args.length) {
            String option = args[i++];
            if ("--sleep-phase".equals(option)) {
                if (i + 1 >= args.length) {
                    throw new IllegalArgumentException("--sleep-phase cần pha và số giây");
                }
                sleepPhase = args[i++];
                sleepSeconds = Long.parseLong(args[i++]);
                if (sleepSeconds < 0L || sleepSeconds > Long.MAX_VALUE / 1000L) {
                    throw new IllegalArgumentException("Số giây sleep không hợp lệ");
                }
            } else if ("--exit-phase".equals(option)) {
                if (i + 1 >= args.length) {
                    throw new IllegalArgumentException("--exit-phase cần pha và exit code");
                }
                exitPhase = args[i++];
                exitCode = Integer.parseInt(args[i++]);
            } else if ("--oom-phase".equals(option)) {
                if (i >= args.length) {
                    throw new IllegalArgumentException("--oom-phase cần tên pha");
                }
                oomPhase = args[i++];
            } else if ("--count".equals(option)) {
                childCountMode = true;
            } else {
                throw new IllegalArgumentException("Tùy chọn con không hợp lệ: " + option);
            }
        }
        if (phases.isEmpty()) {
            throw new IllegalArgumentException("Danh sách pha không được rỗng");
        }

        Main.countMode = childCountMode;
        System.out.println("Counters.ON=" + Counters.ON);
        PhaseLog log = new PhaseLog(procId);
        for (String phase : phases) {
            long phaseStart = System.nanoTime();
            if (phase.equals(exitPhase)) {
                // Thoát giữa pha chỉ dùng để kiểm thử trạng thái crash.
                System.err.println("Test exit at phase " + phase);
                System.exit(exitCode);
            }
            try {
                if (phase.equals(oomPhase)) {
                    throw new OutOfMemoryError("Test OOM at phase " + phase);
                }
                if (phase.equals(sleepPhase)) {
                    // Sleep pha chỉ dùng để test timeout, không dùng cho benchmark.
                    Thread.sleep(sleepSeconds * 1000L);
                }
            } catch (OutOfMemoryError error) {
                double elapsedMs = (System.nanoTime() - phaseStart) / 1000000.0;
                log.append(phase, "oom", elapsedMs);
                System.err.println(error.getMessage());
                System.exit(3);
                return;
            }
            double elapsedMs = (System.nanoTime() - phaseStart) / 1000000.0;
            log.append(phase, "ok", elapsedMs);
        }
    }

    /** Sinh dữ liệu, làm nóng thuật toán rồi đo và log từng pha RQ1. */
    private static void runMeasureChild(String[] args) throws Exception {
        if (args.length < 3) {
            throw new IllegalArgumentException(
                    "Cú pháp: --measure-child <proc_id> --key value ... [--count]");
        }
        String procId = args[1];
        Map<String, String> options = parseOptions(args, 2);
        boolean childCount = options.containsKey("--count");
        boolean profile = options.containsKey("--profile");
        Main.countMode = childCount;
        System.out.println("Counters.ON=" + Counters.ON);

        String rq = required(options, "--rq");
        String algo = required(options, "--algo");
        String variant = required(options, "--variant");
        String dataset = required(options, "--dataset");
        int p = Integer.parseInt(required(options, "--p"));
        double alpha = Double.parseDouble(required(options, "--alpha"));
        double load = options.containsKey("--load")
                ? Double.parseDouble(options.get("--load")) : 0.0;
        int cycles = options.containsKey("--c")
                ? Integer.parseInt(options.get("--c")) : 0;
        double tau = options.containsKey("--tau")
                ? parseTau(options.get("--tau")) : Double.POSITIVE_INFINITY;
        long seed = Long.parseLong(required(options, "--seed"));
        long queriesValue = Long.parseLong(required(options, "--queries"));
        String sleepPhase = null;
        long sleepSeconds = 0L;
        if (options.containsKey("--sleep-phase")) {
            String[] sleep = options.get("--sleep-phase").split(":", -1);
            if (sleep.length != 2 || sleep[0].length() == 0) {
                throw new IllegalArgumentException(
                        "--sleep-phase phải có dạng phase:seconds");
            }
            sleepPhase = sleep[0];
            sleepSeconds = Long.parseLong(sleep[1]);
            if (sleepSeconds < 0L || sleepSeconds > Long.MAX_VALUE / 1000L) {
                throw new IllegalArgumentException("Số giây sleep không hợp lệ");
            }
        }
        if (!"rq1a".equals(dataset) && !"rq1b".equals(dataset) && !"rq1clust".equals(dataset)) {
            if (!"rq2".equals(dataset) && !"rq3".equals(dataset)) {
                throw new IllegalArgumentException(
                        "Measure chỉ hỗ trợ rq1a/rq1b/rq1clust/rq2/rq3");
            }
        }
        if (queriesValue < 0L || queriesValue > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("queries phải nằm trong giới hạn mảng Java");
        }
        int queries = (int) queriesValue;
        if ("rq2".equals(dataset) || "rq3".equals(dataset)) {
            if (cycles < 0 || !Double.isFinite(load) || load <= 0.0 || load > 1.0) {
                throw new IllegalArgumentException("--load phải trong (0,1] và --c >= 0");
            }
            List<String> workloadPhases = RunConfig.phasesForDataset(
                    dataset, required(options, "--phases"));
            PhaseLog workloadLog = new PhaseLog(procId);
            int exitCode = MeasureWorkload.run(algo, variant, dataset, p, load,
                    cycles, tau, seed, queries, childCount, workloadPhases, workloadLog);
            if (exitCode != 0) {
                System.exit(exitCode);
            }
            return;
        }
        List<String> phases = parsePhaseList(required(options, "--phases"));
        PhaseLog log = new PhaseLog(procId);
        Measure.Data data;
        try {
            if (profile && !"rq1clust".equals(dataset)) {
                Map<String, Double> prof = new LinkedHashMap<String, Double>();
                long tWarm0 = System.nanoTime();
                Measure.warmUp(algo, variant, p, alpha, tau,
                        seed ^ 0x6A09E667F3BCC909L, queries);
                long tWarm1 = System.nanoTime();
                double warmUpMs = (tWarm1 - tWarm0) / 1e6;

                data = Measure.buildDataProfiled(seed, p, alpha, queries, prof);
                prof.put("warmUp", warmUpMs);
                Counters.reset();

                long totalGcTime = 0L;
                for (java.lang.management.GarbageCollectorMXBean gc
                        : java.lang.management.ManagementFactory.getGarbageCollectorMXBeans()) {
                    long colTime = gc.getCollectionTime();
                    if (colTime > 0) {
                        totalGcTime += colTime;
                    }
                }
                prof.put("tổng thời gian GC", (double) totalGcTime);

                java.nio.file.Path profPath = java.nio.file.Paths.get("results", "raw", "tmp", procId + ".prof");
                if (profPath.getParent() != null) {
                    java.nio.file.Files.createDirectories(profPath.getParent());
                }
                try (java.io.BufferedWriter bw = java.nio.file.Files.newBufferedWriter(
                        profPath, java.nio.charset.StandardCharsets.UTF_8)) {
                    for (Map.Entry<String, Double> entry : prof.entrySet()) {
                        bw.write(String.format(java.util.Locale.US, "%s\t%.3f%n",
                                entry.getKey(), entry.getValue()));
                    }
                }
            } else {
                Measure.warmUp(algo, variant, p, alpha, tau,
                        seed ^ 0x6A09E667F3BCC909L, queries);
                data = Measure.buildData(dataset, seed, p, alpha, queries);
                Counters.reset();
            }
        } catch (OutOfMemoryError error) {
            appendFailure(log, phases.get(0), "oom", "", "");
            System.err.println("OutOfMemoryError while preparing input: " + error.getMessage());
            System.exit(3);
            return;
        } catch (Exception exception) {
            System.err.println("Measure setup failed: " + exception.getMessage());
            System.exit(1);
            return;
        }

        HashIndex index = null;
        for (String phase : phases) {
            try {
                if (phase.equals(sleepPhase)) {
                    // chỉ dùng cho test, không dùng khi đo
                    Thread.sleep(sleepSeconds * 1000L);
                }
                if ("build".equals(phase)) {
                    index = HashIndexFactory.create(algo, variant, p, tau);
                }
                if (index == null) {
                    throw new IllegalStateException("Pha build phải chạy trước query phase");
                }
                Measure.PhaseResult measured = Measure.runPhase(phase, index, data, childCount);
                log.append(phase, "ok", measured.elapsedMs, measured.heapBefore,
                        measured.heapPeak, measured.opsCount, measured.outputCheck,
                        measured.inputSha256, measured.generatorVersion);
            } catch (OutOfMemoryError error) {
                appendFailure(log, phase, "oom", data.inputSha256, Measure.GENERATOR_VERSION);
                System.err.println("OutOfMemoryError in phase " + phase + ": "
                        + error.getMessage());
                System.exit(3);
                return;
            } catch (Exception exception) {
                appendFailure(log, phase, "crash", data.inputSha256, Measure.GENERATOR_VERSION);
                System.err.println("Measure failed in phase " + phase + ": "
                        + exception.getMessage());
                System.exit(1);
                return;
            }
        }
    }

    /** Ghi trạng thái pha lỗi với toàn bộ trường đo để trống. */
    private static void appendFailure(PhaseLog log, String phase, String status,
            String inputSha256, String generatorVersion) throws Exception {
        log.append(phase, status, "", "", "", "", "", inputSha256, generatorVersion);
    }

    /** Tách pha CSV từ CLI và giới hạn chúng vào ba pha RQ1. */
    private static List<String> parsePhaseList(String names) {
        List<String> phases = new ArrayList<String>();
        String[] parts = names.split(",", -1);
        for (String phase : parts) {
            if (!"build".equals(phase) && !"present".equals(phase) && !"absent".equals(phase)) {
                throw new IllegalArgumentException("Pha RQ1 không hợp lệ: " + phase);
            }
            phases.add(phase);
        }
        if (phases.isEmpty()) {
            throw new IllegalArgumentException("Danh sách pha không được rỗng");
        }
        return phases;
    }

    /** Kiểm tra thêm, tìm, xóa và kiểm tra lại khóa trong bảng stub. */
    private static void runStubCheck() {
        HashIndex index = new StubHashIndex(2);
        String key = "00000000000A";
        if (index.put(key, 25) != HashIndex.INSERTED
                || index.get(key) != 25
                || !index.remove(key)
                || index.get(key) != HashIndex.NOT_FOUND) {
            throw new IllegalStateException("StubHashIndex self-check thất bại");
        }
        System.out.println("StubHashIndex put/get/remove: PASS");
    }

    /** Chạy lại cấu hình từ một dòng run_id trong CSV và so khớp kết quả theo tiêu chí mục 10.1. */
    private static void runRerun(String[] args) throws Exception {
        String targetRunId = null;
        String csvPath = null;
        for (int i = 0; i < args.length; i++) {
            if ("--rerun".equals(args[i])) {
                if (i + 1 < args.length && !args[i + 1].startsWith("--")) {
                    targetRunId = args[++i];
                }
            } else if ("--csv".equals(args[i])) {
                if (i + 1 < args.length && !args[i + 1].startsWith("--")) {
                    csvPath = args[++i];
                }
            }
        }
        if (targetRunId == null || csvPath == null) {
            System.err.println("Cú pháp: java -jar hashbench.jar --rerun <run_id> --csv <file>");
            System.exit(2);
            return;
        }

        java.nio.file.Path file = Paths.get(csvPath);
        if (!java.nio.file.Files.exists(file)) {
            System.err.println("Lỗi: Không tìm thấy file CSV " + csvPath);
            System.exit(1);
            return;
        }

        List<String[]> allRows = new ArrayList<String[]>();
        try (java.io.BufferedReader reader = java.nio.file.Files.newBufferedReader(
                file, java.nio.charset.StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.trim().length() == 0) continue;
                allRows.add(CsvValidator.splitCsvLine(line));
            }
        }

        String[] targetRow = null;
        for (int i = 1; i < allRows.size(); i++) {
            String[] row = allRows.get(i);
            if (row.length > CsvSchema.RUN_ID_INDEX && targetRunId.equals(row[CsvSchema.RUN_ID_INDEX])) {
                targetRow = row;
                break;
            }
        }

        if (targetRow == null) {
            System.err.println("Lỗi: Không tìm thấy run_id '" + targetRunId + "' trong " + csvPath);
            System.exit(1);
            return;
        }

        // Lấy cấu hình từ các cột của dòng
        String rq = targetRow[CsvSchema.RQ_INDEX];
        String algo = targetRow[CsvSchema.ALGORITHM_INDEX];
        String variant = targetRow[CsvSchema.VARIANT_INDEX];
        String dataset = targetRow[CsvSchema.DATASET_INDEX];
        String params = targetRow[CsvSchema.PARAMS_INDEX];
        long seed = Long.parseLong(targetRow[CsvSchema.SEED_INDEX]);
        int repeat = Integer.parseInt(targetRow[CsvSchema.REPEAT_INDEX]);
        String xmx = targetRow[CsvSchema.XMX_INDEX];
        String targetPhase = targetRow[CsvSchema.PHASE_INDEX];
        String origStatus = targetRow[CsvSchema.STATUS_INDEX];
        String origOutputCheck = targetRow[CsvSchema.OUTPUT_CHECK_INDEX];
        String origElapsedMs = targetRow[CsvSchema.ELAPSED_MS_INDEX];
        boolean countMode = targetRow[CsvSchema.OPS_COUNT_INDEX].trim().length() > 0;

        int p = Integer.parseInt(CsvParams.value(params, "p"));
        double alpha = Double.parseDouble(CsvParams.value(params, "alpha"));
        String tauStr = CsvParams.value(params, "tau");
        double tau = (tauStr == null || "inf".equals(tauStr))
                ? Double.POSITIVE_INFINITY : Double.parseDouble(tauStr);
        String expectedTau = tauStr == null ? "inf" : tauStr;
        String queriesStr = CsvParams.value(params, "queries");
        long queries = queriesStr != null ? Long.parseLong(queriesStr) : 0L;
        String cStr = CsvParams.value(params, "c");
        int c = cStr != null ? Integer.parseInt(cStr) : 0;
        String loadStr = CsvParams.value(params, "load");
        double load = loadStr != null ? Double.parseDouble(loadStr) : 0.0;

        // Thu thập các dòng cùng cấu hình cùng pha để tính median
        List<Double> sameConfigElapsed = new ArrayList<Double>();
        for (int i = 1; i < allRows.size(); i++) {
            String[] row = allRows.get(i);
            if (row.length <= CsvSchema.STATUS_INDEX) continue;
            if (algo.equals(row[CsvSchema.ALGORITHM_INDEX])
                    && variant.equals(row[CsvSchema.VARIANT_INDEX])
                    && dataset.equals(row[CsvSchema.DATASET_INDEX])
                    && targetPhase.equals(row[CsvSchema.PHASE_INDEX])
                    && "ok".equals(row[CsvSchema.STATUS_INDEX])) {
                String pStr = CsvParams.value(row[CsvSchema.PARAMS_INDEX], "p");
                String aStr = CsvParams.value(row[CsvSchema.PARAMS_INDEX], "alpha");
                String tStr = CsvParams.value(row[CsvSchema.PARAMS_INDEX], "tau");
                String rowCStr = CsvParams.value(row[CsvSchema.PARAMS_INDEX], "c");
                String rowLoadStr = CsvParams.value(row[CsvSchema.PARAMS_INDEX], "load");
                boolean cMode = row[CsvSchema.OPS_COUNT_INDEX].trim().length() > 0;
                if (pStr != null && Integer.parseInt(pStr) == p
                        && aStr != null && Double.parseDouble(aStr) == alpha
                        && expectedTau.equals(tStr == null ? "inf" : tStr)
                        && rowCStr != null && Integer.parseInt(rowCStr) == c
                        && rowLoadStr != null
                        && Double.compare(Double.parseDouble(rowLoadStr), load) == 0
                        && cMode == countMode) {
                    String el = row[CsvSchema.ELAPSED_MS_INDEX];
                    if (el.length() > 0) {
                        try {
                            sameConfigElapsed.add(Double.parseDouble(el));
                        } catch (NumberFormatException ignored) {}
                    }
                }
            }
        }

        Collections.sort(sameConfigElapsed);
        double refElapsed;
        String refDesc;
        if (sameConfigElapsed.isEmpty()) {
            refElapsed = origElapsedMs.length() > 0 ? Double.parseDouble(origElapsedMs) : 0.0;
            refDesc = origElapsedMs + " ms (dòng gốc)";
        } else {
            int sz = sameConfigElapsed.size();
            refElapsed = sz % 2 == 1
                    ? sameConfigElapsed.get(sz / 2)
                    : (sameConfigElapsed.get(sz / 2 - 1) + sameConfigElapsed.get(sz / 2)) / 2.0;
            refDesc = String.format(java.util.Locale.US, "%.3f ms (median %d lượt)", refElapsed, sz);
        }

        RunConfig rerunConfig = new RunConfig(rq, algo, variant, dataset, 0L, p, alpha,
                c, tau, load, queries, seed, repeat, xmx, "", 60, countMode);

        java.nio.file.Path rerunCsv = Paths.get("results", "raw", "dev", "rerun.csv");
        if (rerunCsv.getParent() != null) {
            java.nio.file.Files.createDirectories(rerunCsv.getParent());
        }
        java.nio.file.Files.deleteIfExists(rerunCsv);

        Runner.RunResult runResult = Runner.runWithCsv(rerunConfig, rerunCsv, Paths.get("."));

        Runner.PhaseResult matchedPhase = null;
        for (Runner.PhaseResult pr : runResult.phases) {
            if (targetPhase.equals(pr.phase)) {
                matchedPhase = pr;
                break;
            }
        }

        if (matchedPhase == null) {
            System.err.println("Lỗi: Pha " + targetPhase + " không có trong kết quả chạy lại");
            System.exit(1);
            return;
        }

        String newStatus = matchedPhase.status;
        String newOutputCheck = matchedPhase.outputCheck;
        String newElapsedMs = matchedPhase.elapsedMs;
        double newElapsed = newElapsedMs.length() > 0 ? Double.parseDouble(newElapsedMs) : 0.0;

        boolean statusMatch = origStatus.equals(newStatus);
        boolean outputMatch = origOutputCheck.equals(newOutputCheck);
        double ratio = refElapsed > 0.0 ? newElapsed / refElapsed : 1.0;
        boolean timeMatch = "ok".equals(newStatus) && (refElapsed <= 0.0 || (ratio >= 0.5 && ratio <= 2.0));

        boolean matched = statusMatch && outputMatch && timeMatch;

        System.out.println("===== KẾT QUẢ CHẠY LẠI (RERUN) =====");
        System.out.println("run_id:        " + targetRunId);
        System.out.println("Pha:           " + targetPhase);
        System.out.println("Cấu hình:      algo=" + algo + " variant=" + variant + " p=" + p
                + " alpha=" + alpha + " seed=" + seed + " repeat=" + repeat
                + (countMode ? " (chế độ --count)" : ""));
        System.out.println("----------------------------------------------------------------------");
        System.out.printf(java.util.Locale.US, "%-15s | %-25s | %-18s | %s%n",
                "Chỉ số", "Dòng gốc / Tham chiếu", "Chạy lại mới", "Đánh giá");
        System.out.println("----------------------------------------------------------------------");
        System.out.printf(java.util.Locale.US, "%-15s | %-25s | %-18s | %s%n",
                "Status", origStatus, newStatus, (statusMatch ? "GIỐNG" : "KHÁC"));
        System.out.printf(java.util.Locale.US, "%-15s | %-25s | %-18s | %s%n",
                "Output Check", origOutputCheck, newOutputCheck, (outputMatch ? "GIỐNG" : "KHÁC"));
        System.out.printf(java.util.Locale.US, "%-15s | %-25s | %-18s | %s%n",
                "Thời gian (ms)", refDesc,
                String.format(java.util.Locale.US, "%.3f (%.3fx)", newElapsed, ratio),
                (timeMatch ? "TRONG KHOẢNG (0.5 - 2.0x)" : "NGOÀI KHOẢNG"));
        System.out.println("----------------------------------------------------------------------");
        System.out.println("KẾT LUẬN: " + (matched ? "KHỚP" : "KHÔNG KHỚP")
                + " (theo tiêu chí mục 10.1: status và output_check giống, thời gian trong 0.5-2.0x)");
        System.out.println("======================================================================");

        if (!matched) {
            System.exit(1);
        }
    }

    /** Sinh ma trận RQ1 200 tiến trình ra file batch. */
    private static void runBatchGenRq1(String[] args) throws Exception {
        String out = "bench/batch_rq1.txt";
        for (int i = 0; i < args.length; i++) {
            if ("--out".equals(args[i]) && i + 1 < args.length) {
                out = args[++i];
            }
        }
        java.nio.file.Path outPath = Paths.get(out);
        Batch.writeBatchFile(outPath);
        System.out.println("Đã sinh ma trận RQ1 (200 tiến trình) ra: " + outPath);
    }

    /** Thực thi batch tuần tự từ file batch với resume và nhật ký. */
    private static void runBatch(String[] args) throws Exception {
        String batchFile = null;
        String out = "results/raw/batch.csv";
        String log = "results/raw/batch_log.txt";
        String machineId = "";
        String xmx = "2g";
        int timeoutSec = 60;
        boolean official = false;
        int limit = -1;

        for (int i = 0; i < args.length; i++) {
            if ("--batch".equals(args[i])) {
                if (i + 1 < args.length && !args[i + 1].startsWith("--")) {
                    batchFile = args[++i];
                }
            } else if ("--out".equals(args[i]) && i + 1 < args.length) {
                out = args[++i];
            } else if ("--log".equals(args[i]) && i + 1 < args.length) {
                log = args[++i];
            } else if ("--machine-id".equals(args[i]) && i + 1 < args.length) {
                machineId = args[++i];
            } else if ("--xmx".equals(args[i]) && i + 1 < args.length) {
                xmx = args[++i];
            } else if ("--timeout-sec".equals(args[i]) && i + 1 < args.length) {
                timeoutSec = Integer.parseInt(args[++i]);
            } else if ("--official".equals(args[i])) {
                official = true;
            } else if ("--limit".equals(args[i]) && i + 1 < args.length) {
                limit = Integer.parseInt(args[++i]);
            }
        }

        if (batchFile == null) {
            System.err.println("Cú pháp: java -jar hashbench.jar --batch <file.txt> --out <csv> [--log <file>] [--machine-id ID] [--xmx 2g] [--timeout-sec 60] [--official] [--limit N]");
            System.exit(2);
            return;
        }

        Batch.runBatch(Paths.get(batchFile), Paths.get(out), Paths.get(log), xmx, timeoutSec, machineId, official, limit);
    }

    /** In cú pháp các chế độ CLI hỗ trợ. */
    private static void printUsage() {
        System.err.println("Usage:\n"
                + "  java -jar hashbench.jar --bench --rq <RQ> --algo <algo> --variant <var> ... [--resume] [--profile]\n"
                + "  java -jar hashbench.jar --batch-gen-rq1 --out <file.txt>\n"
                + "  java -jar hashbench.jar --batch <file.txt> --out <csv> [--log <file>] [--machine-id ID] [--xmx 2g] [--timeout-sec 60] [--official] [--limit N]\n"
                + "  java -jar hashbench.jar --rerun <run_id> --csv <file>\n"
                + "  java -jar hashbench.jar --run [--timeout-sec N] [--machine-id ID] | --count | --selftest");
    }
}
