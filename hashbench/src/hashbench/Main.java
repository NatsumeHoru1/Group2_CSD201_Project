package hashbench;

import java.util.ArrayList;
import java.util.List;

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

        countMode = "--count".equals(args[0]);
        if ("--selftest".equals(args[0])) {
            selfTest();
        } else if ("--run".equals(args[0]) || "--count".equals(args[0])) {
            runStub(args);
        } else if ("--phase-child".equals(args[0])) {
            runPhaseChild(args);
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
