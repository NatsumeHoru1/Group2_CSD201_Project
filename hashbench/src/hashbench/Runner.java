package hashbench;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/** Khởi chạy tiến trình con và thu hoạch kết quả từng pha. */
public final class Runner {
    private Runner() {
    }

    /** Chạy pha stub với CSV mặc định và trả về trạng thái tổng quát. */
    public static String run(RunConfig config) throws IOException, InterruptedException {
        RunResult result = runWithCsv(config, Paths.get("results", "raw", "runner.csv"),
                Paths.get("."));
        System.out.println("child Counters.ON=" + result.childCountEnabled);
        if (result.timedOut) {
            return "timeout";
        }
        if (result.exitCode == 3) {
            return "oom";
        }
        if (result.exitCode != 0) {
            return "crash";
        }
        return "ok";
    }

    /** Chạy con, đọc phase log sau khi kết thúc và ghi một hàng CSV cho mỗi pha. */
    public static RunResult runWithCsv(RunConfig config, Path csvPath, Path workingDirectory)
            throws IOException, InterruptedException {
        if (config == null) {
            throw new IllegalArgumentException("config không được null");
        }
        if (csvPath == null || workingDirectory == null) {
            throw new IllegalArgumentException("Đường dẫn CSV và thư mục làm việc là bắt buộc");
        }
        if (config.capacity() <= 0) {
            throw new IllegalArgumentException("Dung lượng bảng không hợp lệ");
        }

        String javaName = "java";
        if (System.getProperty("os.name").toLowerCase().contains("win")) {
            javaName = "java.exe";
        }
        String javaPath = System.getProperty("java.home") + File.separator
                + "bin" + File.separator + javaName;
        String procId = UUID.randomUUID().toString();
        Path absoluteWorkingDirectory = workingDirectory.toAbsolutePath().normalize();
        PhaseLog phaseLog = new PhaseLog(procId, absoluteWorkingDirectory);
        Path childOutput = phaseLog.path().resolveSibling(procId + ".stdout");
        List<String> phases = RunConfig.phasesForDataset(config.dataset, config.phaseNames);

        List<String> command = new ArrayList<String>();
        command.add(javaPath);
        command.add("-Xmx" + config.xmx);
        command.add("-cp");
        command.add(absoluteClassPath());
        command.add(Main.class.getName());
        if ("test".equals(config.dataset)) {
            command.add("--phase-child");
            command.add(procId);
            command.addAll(phases);
            if (config.sleepPhase != null) {
                command.add("--sleep-phase");
                command.add(config.sleepPhase);
                command.add(Long.toString(config.sleepSeconds));
            }
            if (config.exitPhase != null) {
                command.add("--exit-phase");
                command.add(config.exitPhase);
                command.add(Integer.toString(config.exitCode));
            }
            if (config.oomPhase != null) {
                command.add("--oom-phase");
                command.add(config.oomPhase);
            }
            if (config.countMode) {
                command.add("--count");
            }
        } else {
            command.add("--measure-child");
            command.add(procId);
            addArgument(command, "--rq", config.rq);
            addArgument(command, "--algo", config.algo);
            addArgument(command, "--variant", config.variant);
            addArgument(command, "--dataset", config.dataset);
            addArgument(command, "--p", Integer.toString(config.p));
            addArgument(command, "--alpha", Double.toString(config.alpha));
            addArgument(command, "--tau", Double.isInfinite(config.tau)
                    ? "inf" : Double.toString(config.tau));
            addArgument(command, "--seed", Long.toString(config.seed));
            addArgument(command, "--queries", Long.toString(config.queries));
            addArgument(command, "--phases", join(phases, ","));
            if (config.sleepPhase != null) {
                addArgument(command, "--sleep-phase",
                        config.sleepPhase + ":" + config.sleepSeconds);
            }
            if (config.countMode) {
                command.add("--count");
            }
        }

        ProcessBuilder builder = new ProcessBuilder(command);
        builder.directory(absoluteWorkingDirectory.toFile());
        builder.redirectErrorStream(true);
        builder.redirectOutput(childOutput.toFile());

        long startNanos = System.nanoTime();
        Process child = builder.start();
        boolean timedOut;
        try {
            timedOut = !child.waitFor((long) config.timeoutSec, TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
            child.destroyForcibly();
            child.waitFor();
            throw exception;
        }
        if (timedOut) {
            child.destroyForcibly();
            child.waitFor();
        }

        long wallNanos = System.nanoTime() - startNanos;
        String wallMs = CsvWriter.formatDecimal(wallNanos / 1000000.0);
        int exitCode = child.exitValue();
        String output = new String(Files.readAllBytes(childOutput), StandardCharsets.UTF_8);
        Files.deleteIfExists(childOutput);
        if (!timedOut && exitCode != 0 && output.length() > 0) {
            System.err.print(output);
        }
        String countMarker = "Counters.ON=" + Boolean.toString(config.countMode);
        if (!timedOut && !output.contains(countMarker)) {
            throw new IOException("Tiến trình con không xác nhận " + countMarker
                    + ". Output: " + output);
        }
        List<PhaseResult> results = collectPhases(
                procId, phases, phaseLog.readCompleted(), timedOut, exitCode);

        try (CsvWriter writer = new CsvWriter(csvPath, true)) {
            writer.writeHeader();
            for (PhaseResult result : results) {
                writer.writePhaseRow(result, wallMs, config);
            }
        }
        return new RunResult(procId, wallMs, timedOut, exitCode,
                output.contains("Counters.ON=true"), results);
    }

    /** Thêm cặp tham số CLI vào lệnh tiến trình con. */
    private static void addArgument(List<String> command, String name, String value) {
        command.add(name);
        command.add(value);
    }

    /** Ghép tên pha theo dấu phân cách CLI. */
    private static String join(List<String> values, String separator) {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                result.append(separator);
            }
            result.append(values.get(i));
        }
        return result.toString();
    }

    /** Đổi classpath tương đối thành tuyệt đối trước khi tiến trình đổi thư mục làm việc. */
    private static String absoluteClassPath() {
        String[] entries = System.getProperty("java.class.path").split(
                java.util.regex.Pattern.quote(File.pathSeparator), -1);
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < entries.length; i++) {
            if (i > 0) {
                result.append(File.pathSeparator);
            }
            File entry = new File(entries[i].length() == 0 ? "." : entries[i]);
            result.append(entry.getAbsolutePath());
        }
        return result.toString();
    }

    /** Kết hợp log với trạng thái của pha lỗi và những pha chưa được bắt đầu. */
    private static List<PhaseResult> collectPhases(String procId, List<String> phases,
            List<PhaseLog.Entry> entries, boolean timedOut, int exitCode) throws IOException {
        Map<String, PhaseLog.Entry> byPhase = new HashMap<String, PhaseLog.Entry>();
        for (PhaseLog.Entry entry : entries) {
            if (byPhase.put(entry.phase, entry) != null) {
                throw new IOException("Pha bị ghi lặp trong phase log: " + entry.phase);
            }
        }

        List<PhaseResult> results = new ArrayList<PhaseResult>();
        String stoppedStatus = null;
        for (String phase : phases) {
            if (stoppedStatus != null) {
                results.add(new PhaseResult(procId, phase, stoppedStatus, "", "not_started",
                        "", "", "", "", "", ""));
                continue;
            }
            PhaseLog.Entry entry = byPhase.get(phase);
            if (entry != null) {
                String elapsedMs = "ok".equals(entry.status) ? entry.elapsedMs : "";
                results.add(new PhaseResult(procId, phase, entry.status, elapsedMs, "",
                        entry.heapBefore, entry.heapPeak, entry.opsCount, entry.outputCheck,
                        entry.inputSha256, entry.generatorVersion));
                if ("oom".equals(entry.status) || "crash".equals(entry.status)
                        || "timeout".equals(entry.status)) {
                    stoppedStatus = entry.status;
                }
            } else {
                stoppedStatus = timedOut ? "timeout" : exitCode == 3 ? "oom" : "crash";
                results.add(new PhaseResult(procId, phase, stoppedStatus, "", "",
                        "", "", "", "", "", ""));
            }
        }
        return results;
    }

    /** Một dòng kết quả pha đã được cha thu hoạch. */
    public static final class PhaseResult {
        public final String procId;
        public final String phase;
        public final String status;
        public final String elapsedMs;
        public final String phaseNote;
        public final String heapBefore;
        public final String heapPeak;
        public final String opsCount;
        public final String outputCheck;
        public final String inputSha256;
        public final String generatorVersion;

        private PhaseResult(String procId, String phase, String status,
                String elapsedMs, String phaseNote, String heapBefore, String heapPeak,
                String opsCount, String outputCheck, String inputSha256,
                String generatorVersion) {
            this.procId = procId;
            this.phase = phase;
            this.status = status;
            this.elapsedMs = elapsedMs;
            this.phaseNote = phaseNote;
            this.heapBefore = heapBefore;
            this.heapPeak = heapPeak;
            this.opsCount = opsCount;
            this.outputCheck = outputCheck;
            this.inputSha256 = inputSha256;
            this.generatorVersion = generatorVersion;
        }

        /** Tạo run_id duy nhất từ proc_id và tên pha. */
        public String runId() {
            return CsvWriter.runId(procId, phase);
        }
    }

    /** Tổng hợp trạng thái tiến trình con và các pha đã thu hoạch. */
    public static final class RunResult {
        public final String procId;
        public final String wallMs;
        public final boolean timedOut;
        public final int exitCode;
        public final boolean childCountEnabled;
        public final List<PhaseResult> phases;

        private RunResult(String procId, String wallMs, boolean timedOut, int exitCode,
                boolean childCountEnabled, List<PhaseResult> phases) {
            this.procId = procId;
            this.wallMs = wallMs;
            this.timedOut = timedOut;
            this.exitCode = exitCode;
            this.childCountEnabled = childCountEnabled;
            this.phases = phases;
        }
    }
}
