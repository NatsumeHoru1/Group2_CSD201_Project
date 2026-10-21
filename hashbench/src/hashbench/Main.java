package hashbench;

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
            runStub();
        } else if ("--stub-child".equals(args[0])) {
            runStubCheck();
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

    /** Chạy tiến trình con chứa kiểm tra stub và báo rõ trạng thái. */
    private static void runStub() throws Exception {
        Counters.reset();
        RunConfig config = new RunConfig("TODO", "stub", 1, 0.0, 0, 0.0, 0L, 1);
        String status = Runner.run(config);
        System.out.println("status=" + status);
        if (!"ok".equals(status)) {
            System.exit(1);
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

    /** In cú pháp các chế độ hiện được hỗ trợ. */
    private static void printUsage() {
        System.err.println("Usage: java -jar hashbench.jar --run | --count | --selftest");
    }
}
