package hashbench.test;

import hashbench.test.Registry.Impl;
import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.util.List;

/**
 * Chay: java -cp out hashbench.test.AllTests [--self] [--ops N] [--only TEN]
 *   --self : kiem tra chinh harness (reference phai xanh, 4 ban dot bien phai bi bat)
 *   --ops  : so thao tac oracle (mac dinh 1_000_000)
 *   --require-all : coi cau hinh bi SKIP la that bai (dung truoc khi gan tag rc1)
 */
public final class AllTests {
    private AllTests() {}

    public static void main(String[] args) throws UnsupportedEncodingException {
        System.setOut(new PrintStream(new FileOutputStream(FileDescriptor.out), true, "UTF-8"));
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
            else { skipped++; System.out.println("SKIP  " + i.name + "  (chua dung duoc: " + why + ")"); }
        }
        if (impls.isEmpty()) {
            System.out.println("Chua co cau hinh nao dung duoc trong HashIndexFactory.");
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
            System.out.println("\nCANH BAO: con " + skipped + "/6 cau hinh chua co (SKIP)." + (requireAll ? " --require-all => THAT BAI" : ""));
            if (requireAll) code = 1;
        }
        code = Math.max(code, TestHarness.summary());
        System.out.printf(java.util.Locale.US, "Thoi gian: %.1f s%n", (System.nanoTime() - t0) / 1e9);
        System.exit(code);
    }

    static void run(Impl impl, long ops) {
        TestHarness.context(impl.name);
        CoreCases.run(impl);
        WorkloadTests.run(impl);
        OracleTest.runAll(impl, ops);
    }

    /** Moi ban dot bien PHAI lam it nhat 1 test FAIL; neu khong, harness yeu. */
    static int mutationCheck() {
        System.out.println("\n--- Kiem tra dot bien (harness phai bat duoc loi) ---");
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
            System.out.println((caught > 0 ? "BAT DUOC  " : "LOT LUOI   ") + impl.name + " (" + caught + " test FAIL)");
            if (caught == 0) escaped++;
        }
        return escaped == 0 ? 0 : 1;
    }
}
