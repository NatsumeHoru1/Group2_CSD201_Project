package hashbench.test;

import hashbench.test.Registry.Impl;
import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.util.List;

/**
 * Chạy: java -cp out hashbench.test.AllTests [--self] [--ops N] [--only TÊN]
 *   --self : kiểm tra chính harness (reference phải xanh, 4 bản đột biến phải bị bắt)
 *   --ops  : số thao tác oracle (mặc định 1_000_000)
 *   --require-all : coi cấu hình bị SKIP là thất bại (dùng trước khi gắn tag rc1)
 */
public final class AllTests {
    private AllTests() {}

    public static void main(String[] args) throws UnsupportedEncodingException {
        System.setOut(new PrintStream(new FileOutputStream(FileDescriptor.out), true, "UTF-8"));  // tiếng Việt (Windows: chcp 65001)
        boolean self = false;
        long ops = 1_000_000L;
        String only = null;
        for (int i = 0; i < args.length; i++) {
            if (args[i].equals("--self")) self = true;
            else if (args[i].equals("--ops")) ops = Long.parseLong(args[++i]);
            else if (args[i].equals("--only")) only = args[++i];
        }
        boolean requireAll = false;
        for (String a : args) if (a.equals("--require-all")) requireAll = true;
        List<Impl> impls = new java.util.ArrayList<Impl>();
        int skipped = 0;
        if (self) impls = Registry.reference();
        else for (Impl i : Registry.all()) {
            String why = Registry.unavailableReason(i);
            if (why == null) impls.add(i);
            else { skipped++; System.out.println("SKIP  " + i.name + "  (chưa dùng được: " + why + ")"); }
        }
        if (impls.isEmpty()) {
            System.out.println("Chưa có cấu hình nào dùng được trong HashIndexFactory.");
            System.exit(2);
        }
        long t0 = System.nanoTime();
        for (Impl impl : impls) {
            if (only != null && !impl.name.equals(only)) continue;
            run(impl, ops);
        }
        int code = 0;
        if (self) code = mutationCheck();
        if (skipped > 0) {
            System.out.println("\nCẢNH BÁO: còn " + skipped + "/6 cấu hình chưa có (SKIP)." + (requireAll ? " --require-all => THẤT BẠI" : ""));
            if (requireAll) code = 1;
        }
        code = Math.max(code, TestHarness.summary());
        System.out.printf(java.util.Locale.US, "Thời gian: %.1f s%n", (System.nanoTime() - t0) / 1e9);
        System.exit(code);
    }

    static void run(Impl impl, long ops) {
        TestHarness.context(impl.name);
        CoreCases.run(impl);
        WorkloadTests.run(impl);
        OracleTest.runAll(impl, ops);
    }

    /** Mỗi bản đột biến PHẢI làm ít nhất 1 test FAIL; nếu không, harness yếu. */
    static int mutationCheck() {
        System.out.println("\n--- Kiểm tra đột biến (harness phải bắt được lỗi) ---");
        int escaped = 0;
        for (final Impl m : Registry.mutants()) {
            final Impl impl = m;
            int caught = TestHarness.sandbox(new Runnable() {
                public void run() {
                    TestHarness.context(impl.name);
                    CoreCases.run(impl);
                    OracleTest.runAll(impl, 60_000);
                }
            });
            System.out.println((caught > 0 ? "BẮT ĐƯỢC  " : "LỌT LƯỚI   ") + impl.name + " (" + caught + " test FAIL)");
            if (caught == 0) escaped++;
        }
        return escaped == 0 ? 0 : 1;
    }
}
