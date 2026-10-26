package hashbench;

import java.util.ArrayList;
import java.util.HashMap;
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
        String rq = required(options, "--rq");
        String algo = required(options, "--algo");
        String variant = required(options, "--variant");
        String dataset = required(options, "--dataset");
        int p = Integer.parseInt(required(options, "--p"));
        double alpha = Double.parseDouble(required(options, "--alpha"));
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
        RunConfig config = new RunConfig(rq, algo, variant, dataset, 0L, p, alpha,
                0, tau, 0.0, queries, seed, repeat, xmx, machineId, timeoutSec,
                count, phases);
        Runner.RunResult result = Runner.runWithCsv(
                config, Paths.get(out), Paths.get("."));
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
            if ("--count".equals(name)) {
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
        Main.countMode = childCount;
        System.out.println("Counters.ON=" + Counters.ON);

        String rq = required(options, "--rq");
        String algo = required(options, "--algo");
        String variant = required(options, "--variant");
        String dataset = required(options, "--dataset");
        int p = Integer.parseInt(required(options, "--p"));
        double alpha = Double.parseDouble(required(options, "--alpha"));
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
        if (!"rq1a".equals(dataset) && !"rq1b".equals(dataset)) {
            throw new IllegalArgumentException("Measure chỉ hỗ trợ rq1a/rq1b");
        }
        if (queriesValue < 0L || queriesValue > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("queries phải nằm trong giới hạn mảng Java");
        }
        int queries = (int) queriesValue;
        List<String> phases = parsePhaseList(required(options, "--phases"));
        PhaseLog log = new PhaseLog(procId);
        Measure.Data data;
        try {
            data = Measure.buildData(seed, p, alpha, queries);
            Measure.warmUp(algo, variant, p, alpha, tau,
                    seed ^ 0x6A09E667F3BCC909L, queries);
            Counters.reset();
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
        if (index.put(10L, 25) != HashIndex.INSERTED
                || index.get(10L) != 25
                || !index.remove(10L)
                || index.get(10L) != HashIndex.NOT_FOUND) {
            throw new IllegalStateException("StubHashIndex self-check thất bại");
        }
        System.out.println("StubHashIndex put/get/remove: PASS");
    }

    /** In cú pháp các chế độ CLI hỗ trợ. */
    private static void printUsage() {
        System.err.println("Usage: java -jar hashbench.jar --run [--timeout-sec N]"
                + " [--machine-id ID] | --count [--timeout-sec N]"
                + " [--machine-id ID] | --selftest");
    }
}
