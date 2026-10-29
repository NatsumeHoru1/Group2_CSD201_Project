package hashbench;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Kiem tra nhom checksum, coverage va doi chung cho dataset RQ2/RQ3. */
public final class CsvValidatorRq23Test {
    private CsvValidatorRq23Test() {
    }

    public static void main(String[] args) throws Exception {
        Path root = Files.createTempDirectory("hashbench-validator-rq23-");
        try {
            testRq2ControlMismatch(root);
            testRq23CoverageIncludesNotStarted(root);
            testComparisonGroups(root);
            System.out.println("CsvValidatorRq23Test: ALL PASS");
        } finally {
            Files.deleteIfExists(root.resolve("rq2-control.csv"));
            Files.deleteIfExists(root.resolve("rq2-coverage.csv"));
            Files.deleteIfExists(root.resolve("rq3-groups.csv"));
            Files.deleteIfExists(root);
        }
    }

    private static void testRq2ControlMismatch(Path root) throws Exception {
        RunConfig config = config("RQ2", "lp-tomb", "rq2", 0.5, 10,
                Double.POSITIVE_INFINITY);
        List<String[]> rows = phaseRows(config, RunConfig.phasesForDataset("rq2"));
        for (String[] row : rows) {
            String phase = row[CsvSchema.PHASE_INDEX];
            if ("present".equals(phase)) {
                row[CsvSchema.OUTPUT_CHECK_INDEX] = "100";
            } else if ("ctl_present".equals(phase)) {
                row[CsvSchema.OUTPUT_CHECK_INDEX] = "101";
            } else if ("absent".equals(phase) || "ctl_absent".equals(phase)) {
                row[CsvSchema.OUTPUT_CHECK_INDEX] = "20";
            }
        }
        Path csv = root.resolve("rq2-control.csv");
        write(csv, rows);
        CsvValidator.Report report = CsvValidator.validate(csv, false);
        assertContains(report, "ctl_present khac present",
                "RQ2 kiem tra checksum doi chung doc lap voi cross-algorithm");
    }

    private static void testRq23CoverageIncludesNotStarted(Path root) throws Exception {
        RunConfig config = config("RQ2", "rh", "rq2", 0.5, 10,
                Double.POSITIVE_INFINITY);
        List<String[]> rows = phaseRows(config, RunConfig.phasesForDataset("rq2"));
        for (String[] row : rows) {
            if ("ctl_present".equals(row[CsvSchema.PHASE_INDEX])
                    || "ctl_absent".equals(row[CsvSchema.PHASE_INDEX])) {
                row[CsvSchema.STATUS_INDEX] = "timeout";
                row[CsvSchema.ELAPSED_MS_INDEX] = "";
                row[CsvSchema.PHASE_NOTE_INDEX] = "not_started";
            }
        }
        Path csv = root.resolve("rq2-coverage.csv");
        write(csv, rows);
        CsvValidator.Report report = CsvValidator.validate(csv, false);
        assertTrue(report.isValid(), "pha not_started van duoc tinh trong coverage RQ2");

        rows.remove(rows.size() - 1);
        write(csv, rows);
        report = CsvValidator.validate(csv, false);
        assertContains(report, "thieu pha rq2", "RQ2 thieu pha phai that bai");
    }

    private static void testComparisonGroups(Path root) throws Exception {
        List<String[]> rows = new ArrayList<String[]>();
        addConfigRows(rows, config("RQ3", "lp-tomb", "rq3", 0.5, 10, 0.2),
                "10", "20");
        addConfigRows(rows, config("RQ3", "rh", "rq3", 0.5, 10,
                Double.POSITIVE_INFINITY), "11", "20");
        addConfigRows(rows, config("RQ3", "rh", "rq3", 0.8, 10,
                Double.POSITIVE_INFINITY), "30", "20");
        addConfigRows(rows, config("RQ3", "rh", "rq3", 0.5, 20,
                Double.POSITIVE_INFINITY), "40", "20");
        Path csv = root.resolve("rq3-groups.csv");
        write(csv, rows);
        CsvValidator.Report report = CsvValidator.validate(csv, true);
        assertEquals(1, countContains(report, "output_check khac nhau"),
                "chi cau hinh cung load/c phai duoc so sanh, bo tau khoi nhom");
    }

    private static void addConfigRows(List<String[]> rows, RunConfig config,
            String presentOutput, String absentOutput) {
        List<String[]> configured = phaseRows(config, RunConfig.phasesForDataset("rq3"));
        for (String[] row : configured) {
            if ("present".equals(row[CsvSchema.PHASE_INDEX])) {
                row[CsvSchema.OUTPUT_CHECK_INDEX] = presentOutput;
            } else if ("absent".equals(row[CsvSchema.PHASE_INDEX])) {
                row[CsvSchema.OUTPUT_CHECK_INDEX] = absentOutput;
            }
            rows.add(row);
        }
    }

    private static RunConfig config(String rq, String algo, String dataset,
            double load, int cycles, double tau) {
        return new RunConfig(rq, algo, "base", dataset, 0L, 10, 0.0,
                cycles, tau, load, 20L, 1L, 1, "2g", "test", 60, false);
    }

    private static List<String[]> phaseRows(RunConfig config, List<String> phases) {
        List<String[]> rows = new ArrayList<String[]>();
        for (String phase : phases) {
            String[] row = new String[CsvSchema.COLUMNS.length];
            row[CsvSchema.RUN_ID_INDEX] = CsvWriter.runId(config.procId(), phase);
            row[CsvSchema.RQ_INDEX] = config.rq;
            row[CsvSchema.ALGORITHM_INDEX] = config.algo;
            row[CsvSchema.VARIANT_INDEX] = config.variant;
            row[CsvSchema.DATASET_INDEX] = config.dataset;
            row[CsvSchema.N_INDEX] = Long.toString(config.n);
            row[CsvSchema.PARAMS_INDEX] = CsvParams.augment(config.algo, config.params)
                    + ";keytype=str12";
            row[CsvSchema.SEED_INDEX] = Long.toString(config.seed);
            row[CsvSchema.REPEAT_INDEX] = Integer.toString(config.repeat);
            row[CsvSchema.ELAPSED_MS_INDEX] = "1.000";
            row[CsvSchema.STATUS_INDEX] = "ok";
            row[CsvSchema.XMX_INDEX] = config.xmx;
            row[CsvSchema.MACHINE_ID_INDEX] = config.machineId;
            row[CsvSchema.PROC_ID_INDEX] = config.procId();
            row[CsvSchema.PHASE_INDEX] = phase;
            row[CsvSchema.WALL_MS_INDEX] = "1.000";
            row[CsvSchema.PHASE_NOTE_INDEX] = "";
            rows.add(row);
        }
        return rows;
    }

    private static void write(Path file, List<String[]> rows) throws Exception {
        try (CsvWriter writer = new CsvWriter(file)) {
            writer.writeHeader();
            for (String[] row : rows) {
                writer.writeRow(row);
            }
        }
    }

    private static void assertContains(CsvValidator.Report report, String text,
            String message) {
        assertTrue(countContains(report, text) > 0, message);
    }

    private static int countContains(CsvValidator.Report report, String text) {
        int count = 0;
        for (CsvValidator.Issue issue : report.issues) {
            if (issue.message.contains(text)) {
                count++;
            }
        }
        return count;
    }

    private static void assertTrue(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static void assertEquals(int expected, int actual, String message) {
        if (expected != actual) {
            throw new AssertionError(message + ": expected " + expected + ", got " + actual);
        }
    }
}
