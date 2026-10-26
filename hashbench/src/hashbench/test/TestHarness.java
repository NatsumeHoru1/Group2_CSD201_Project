package hashbench.test;

import java.util.ArrayList;
import java.util.List;

/** Khung test tự chứa (không JUnit). Exit code 0 = xanh, 1 = có FAIL, 2 = chưa có cài đặt nào. */
public final class TestHarness {
    public interface Body { void run() throws Exception; }

    private static int passed, failed;
    private static final List<String> failures = new ArrayList<String>();
    private static String ctx = "";
    private static boolean quiet;

    private TestHarness() {}

    public static void context(String c) { ctx = c; }

    public static void test(String name, Body b) {
        String full = "[" + ctx + "] " + name;
        try {
            b.run();
            passed++;
            if (!quiet) System.out.println("PASS  " + full);
        } catch (Throwable t) {
            failed++;
            failures.add(full + " -> " + t);
            if (!quiet) System.out.println("FAIL  " + full + "\n      " + t);
        }
    }

    public static void check(boolean cond, String msg) {
        if (!cond) throw new AssertionError(msg);
    }

    public static void eq(long expected, long actual, String msg) {
        if (expected != actual) throw new AssertionError(msg + ": mong đợi " + expected + ", nhận " + actual);
    }

    /** Chạy r mà không tính vào kết quả chung; trả về số test FAIL bên trong (dùng cho kiểm tra đột biến). */
    public static int sandbox(Runnable r) {
        int p = passed, f = failed, s = failures.size();
        boolean q = quiet;
        quiet = true;
        try { r.run(); } finally {
            quiet = q;
        }
        int nf = failed - f;
        passed = p; failed = f;
        while (failures.size() > s) failures.remove(failures.size() - 1);
        return nf;
    }

    public static int summary() {
        System.out.println("\n===== TỔNG KẾT: " + passed + " PASS, " + failed + " FAIL =====");
        for (String f : failures) System.out.println("  - " + f);
        return failed == 0 ? 0 : 1;
    }
}
