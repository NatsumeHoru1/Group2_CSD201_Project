package hashbench;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Kiem tra cac file CSV do runner ghi ra theo luoc do 32 cot cua CsvSchema.
 *
 * Bat bien duoc kiem tra (theo spec_notes.txt muc A, B, D va CONVENTIONS.md):
 *  1. Header dung thu tu va du 32 cot; moi hang cung co dung 32 o.
 *  2. status thuoc dung tap {ok, timeout, crash, oom, wrong}.
 *  3. Luot khong ok thi elapsed_ms phai trong; luot ok thi elapsed_ms phai la
 *     so thap phan co dung 3 chu so le.
 *  4. phase_note = not_started chi di kem status timeout/crash/oom.
 *  5. run_id khong trung trong cung mot file va phai co dang proc_id + "_" + phase.
 *  6. Khong co dong nao co status = wrong (day la loi ket qua, phai bao).
 *  7. output_check phai bang nhau giua cac thuat toan cung cau hinh, load, C, seed va phase.
 *  8. RQ1 du ba pha; RQ2/RQ3 du cac pha theo proc_id.
 *  9. Cac cot so phai parse duoc; cot chua do duoc thi de trong, khong ghi so 0.
 *
 * Lop nay chi doc file, khong sua dong nao trong CSV.
 *
 * Phu trach: TV6.
 */
public final class CsvValidator {
    /** Cac trang thai hop le theo quy dinh chung muc 6.7. */
    public static final Set<String> ALLOWED_STATUS = new HashSet<String>(Arrays.asList(
            "ok", "timeout", "crash", "oom", "wrong"));

    /** Cac ten thuat toan theo Bang 2 va Bang 3 cua de 2. */
    public static final Set<String> ALLOWED_ALGORITHM = new HashSet<String>(Arrays.asList(
            "sc", "lp-tomb", "lp-backshift", "rh"));

    /** Cac bien the hop le. */
    public static final Set<String> ALLOWED_VARIANT = new HashSet<String>(Arrays.asList(
            "base", "noearly"));

    /** Cac ma dataset theo E3. */
    public static final Set<String> ALLOWED_DATASET = new HashSet<String>(Arrays.asList(
            "rq1a", "rq1b", "rq1clust", "rq2", "rq3", "test"));

    /** Cac pha theo E2. */
    public static final Set<String> ALLOWED_PHASE = new HashSet<String>(Arrays.asList(
            "build", "present", "absent", "workload", "ctl_present", "ctl_absent"));

    /** Cac cot phai la so nguyen khong am neu co gia tri. */
    private static final int[] NON_NEGATIVE_LONG_COLUMNS = {
        CsvSchema.N_INDEX,
        CsvSchema.SEED_INDEX,
        CsvSchema.REPEAT_INDEX,
        CsvSchema.HEAP_BEFORE_BYTES_INDEX,
        CsvSchema.HEAP_PEAK_BYTES_INDEX,
        CsvSchema.OPS_COUNT_INDEX,
    };

    /** Cac cot phai la so thap phan khong am neu co gia tri. */
    private static final int[] NON_NEGATIVE_DOUBLE_COLUMNS = {
        CsvSchema.ELAPSED_MS_INDEX,
        CsvSchema.WALL_MS_INDEX,
        CsvSchema.TOMB_RATIO_INDEX,
        CsvSchema.EMPTY_RATIO_INDEX,
        CsvSchema.REBUILD_MS_INDEX,
        CsvSchema.BLOCK_MEDIAN_MS_INDEX,
        CsvSchema.BLOCK_MAX_MS_INDEX,
        CsvSchema.DIB_MEAN_INDEX,
    };

    private CsvValidator() {
    }

    private static String getFullGroupKey(String[] row, boolean includeAlgo) {
        String params = row[CsvSchema.PARAMS_INDEX];
        String p = CsvParams.value(params, "p");
        String alpha = CsvParams.value(params, "alpha");
        String tau = CsvParams.value(params, "tau");
        String queries = CsvParams.value(params, "queries");
        String load = CsvParams.value(params, "load");
        String cycles = CsvParams.value(params, "c");

        StringBuilder sb = new StringBuilder();
        if (includeAlgo) {
            sb.append(row[CsvSchema.ALGORITHM_INDEX]).append('/')
                    .append(row[CsvSchema.VARIANT_INDEX]).append('|');
        }
        sb.append(row[CsvSchema.DATASET_INDEX]);
        sb.append("|p=").append(p != null ? p : "");
        sb.append("|a=").append(alpha != null ? alpha : "");
        sb.append("|t=").append(tau != null ? tau : "");
        sb.append("|l=").append(load != null ? load : "");
        sb.append("|c=").append(cycles != null ? cycles : "");
        sb.append("|q=").append(queries != null ? queries : "");
        sb.append('|').append(row[CsvSchema.SEED_INDEX]);
        return sb.toString();
    }

    /** Mot van de phat hien duoc trong CSV. */
    public static final class Issue {
        public final int line;
        public final String message;

        Issue(int line, String message) {
            this.line = line;
            this.message = message;
        }

        @Override
        public String toString() {
            return "dong " + line + ": " + message;
        }
    }

    /** Ket qua kiem tra cho mot file CSV. */
    public static final class Report {
        public final Path file;
        public final List<Issue> issues;
        public final int dataRows;

        Report(Path file, List<Issue> issues, int dataRows) {
            this.file = file;
            this.issues = issues;
            this.dataRows = dataRows;
        }

        /** Tra ve true khi khong co van de nao. */
        public boolean isValid() {
            return issues.isEmpty();
        }

        /** In bao cao ra man hinh. */
        public void print() {
            System.out.println("=== " + file + " (" + dataRows + " dong du lieu) ===");
            if (issues.isEmpty()) {
                System.out.println("OK: khong phat hien van de");
                return;
            }
            for (Issue issue : issues) {
                System.out.println("VI PHAM " + issue);
            }
            System.out.println("TONG SO VI pham: " + issues.size());
        }
    }

    /**
     * Kiem tra mot file CSV va tra ve bao cao (giu nguyen mac dinh khong loc dong trung).
     */
    public static Report validate(Path file, boolean checkCrossAlgorithm) throws IOException {
        return validate(file, checkCrossAlgorithm, false);
    }

    /**
     * Kiem tra mot file CSV va tra ve bao cao. Khi latest=true, chi giu dong cuoi cung cua moi run_id.
     */
    public static Report validate(Path file, boolean checkCrossAlgorithm, boolean latest) throws IOException {
        List<Issue> issues = new ArrayList<Issue>();
        List<String[]> rows = readRows(file, issues);
        if (rows.isEmpty()) {
            issues.add(new Issue(0, "file rong hoac khong doc duoc"));
            return new Report(file, issues, 0);
        }
        String[] header = rows.get(0);
        checkHeader(header, issues);
        List<String[]> dataRows = rows.subList(1, rows.size());
        if (latest) {
            Map<String, String[]> lastByRunId = new LinkedHashMap<String, String[]>();
            for (String[] r : dataRows) {
                if (r.length > CsvSchema.RUN_ID_INDEX) {
                    lastByRunId.put(r[CsvSchema.RUN_ID_INDEX], r);
                }
            }
            dataRows = new ArrayList<String[]>(lastByRunId.values());
        }
        checkRows(dataRows, issues);
        if (checkCrossAlgorithm) {
            checkOutputCheckAgreement(dataRows, issues);
        }
        checkRq2ControlAgreement(dataRows, issues);
        checkPhaseCoverage(dataRows, issues);
        checkRepeatVariance(dataRows, issues);
        return new Report(file, issues, dataRows.size());
    }

    /** Doc CSV va tach o theo luat CSV, bao loi neu dong khong du 32 o. */
    private static List<String[]> readRows(Path file, List<Issue> issues) throws IOException {
        List<String[]> rows = new ArrayList<String[]>();
        try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            String line;
            int lineNumber = 0;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                if (line.trim().length() == 0) {
                    continue;
                }
                String[] cells = splitCsvLine(line);
                if (cells.length != CsvSchema.COLUMNS.length) {
                    issues.add(new Issue(lineNumber, "so cot " + cells.length
                            + " khac " + CsvSchema.COLUMNS.length + " mong doi"));
                }
                rows.add(cells);
            }
        }
        return rows;
    }

    /** Tach mot dong CSV theo luat dau phay va dau nhay kep. */
    static String[] splitCsvLine(String line) {
        List<String> cells = new ArrayList<String>();
        StringBuilder current = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (quoted) {
                if (c == '"') {
                    if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
                        current.append('"');
                        i++;
                    } else {
                        quoted = false;
                    }
                } else {
                    current.append(c);
                }
            } else if (c == '"') {
                quoted = true;
            } else if (c == ',') {
                cells.add(current.toString());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        cells.add(current.toString());
        return cells.toArray(new String[cells.size()]);
    }

    /** Kiem tra header co dung ten va dung thu tu cot theo CsvSchema. */
    private static void checkHeader(String[] header, List<Issue> issues) {
        if (header.length != CsvSchema.COLUMNS.length) {
            issues.add(new Issue(1, "header co " + header.length + " cot, can "
                    + CsvSchema.COLUMNS.length));
            return;
        }
        for (int i = 0; i < header.length; i++) {
            if (!CsvSchema.COLUMNS[i].equals(header[i])) {
                issues.add(new Issue(1, "cot " + i + " ten '" + header[i]
                        + "' khong doi '" + CsvSchema.COLUMNS[i] + "'"));
            }
        }
    }

    /** Kiem tra tung dong du lieu theo cac bat bien cua de. */
    private static void checkRows(List<String[]> rows, List<Issue> issues) {
        Set<String> runIds = new HashSet<String>();
        for (int i = 0; i < rows.size(); i++) {
            int lineNumber = i + 2; // +1 cho header, +1 cho danh so 1-based
            String[] row = rows.get(i);
            if (row.length != CsvSchema.COLUMNS.length) {
                continue; // da bao o readRows
            }
            String runId = row[CsvSchema.RUN_ID_INDEX];
            if (runId.length() == 0) {
                issues.add(new Issue(lineNumber, "run_id rong"));
            } else if (!runIds.add(runId)) {
                issues.add(new Issue(lineNumber, "run_id bi trung: " + runId));
            }
            String procId = row[CsvSchema.PROC_ID_INDEX];
            String phase = row[CsvSchema.PHASE_INDEX];
            if (procId.length() == 0 || phase.length() == 0) {
                issues.add(new Issue(lineNumber, "proc_id hoac phase rong"));
            } else if (!runId.equals(procId + "_" + phase)) {
                issues.add(new Issue(lineNumber, "run_id '" + runId
                        + "' khong khop proc_id + '_' + phase"));
            }

            String status = row[CsvSchema.STATUS_INDEX];
            if (!ALLOWED_STATUS.contains(status)) {
                issues.add(new Issue(lineNumber, "status '" + status + "' khong hop le"));
            }
            if ("wrong".equals(status)) {
                issues.add(new Issue(lineNumber, "status = wrong: ket qua lech oracle"));
            }

            String elapsed = row[CsvSchema.ELAPSED_MS_INDEX];
            if ("ok".equals(status)) {
                if (elapsed.length() == 0) {
                    issues.add(new Issue(lineNumber, "status ok thi elapsed_ms phai co gia tri"));
                } else if (!elapsed.matches("[0-9]+\\.[0-9]{3}")) {
                    issues.add(new Issue(lineNumber,
                            "elapsed_ms '" + elapsed + "' phai la so thap phan 3 chu so le"));
                }
            } else if (elapsed.length() != 0) {
                issues.add(new Issue(lineNumber, "status " + status
                        + " thi elapsed_ms phai de trong"));
            }

            String phaseNote = row[CsvSchema.PHASE_NOTE_INDEX];
            if ("not_started".equals(phaseNote) && !"timeout".equals(status)
                    && !"crash".equals(status) && !"oom".equals(status)) {
                issues.add(new Issue(lineNumber,
                        "phase_note = not_started khong phu hop voi status " + status));
            }

            if (!ALLOWED_ALGORITHM.contains(row[CsvSchema.ALGORITHM_INDEX])) {
                issues.add(new Issue(lineNumber, "algorithm '" + row[CsvSchema.ALGORITHM_INDEX]
                        + "' khong hop le"));
            }
            if (!ALLOWED_VARIANT.contains(row[CsvSchema.VARIANT_INDEX])) {
                issues.add(new Issue(lineNumber, "variant '" + row[CsvSchema.VARIANT_INDEX]
                        + "' khong hop le"));
            }
            if ("noearly".equals(row[CsvSchema.VARIANT_INDEX])
                    && !"rh".equals(row[CsvSchema.ALGORITHM_INDEX])) {
                issues.add(new Issue(lineNumber, "variant noearly chi ap dung cho rh"));
            }
            if (!ALLOWED_DATASET.contains(row[CsvSchema.DATASET_INDEX])) {
                issues.add(new Issue(lineNumber, "dataset '" + row[CsvSchema.DATASET_INDEX]
                        + "' khong hop le"));
            }
            if (!ALLOWED_PHASE.contains(phase)) {
                issues.add(new Issue(lineNumber, "phase '" + phase + "' khong hop le"));
            }
            if (row[CsvSchema.RQ_INDEX].length() == 0) {
                issues.add(new Issue(lineNumber, "rq rong"));
            }

            checkNumericColumns(row, lineNumber, issues);
            checkInputSha256(row, lineNumber, issues);
            checkAlgorithmParams(row, lineNumber, issues);
        }
    }

    /**
     * Kiem tra tham so rieng bat buoc cua tung thuat toan.
     * Voi lp-backshift, de 2 yeu cau ghi ro da dung Algorithm R cua Knuth hay
     * cach chen lai cum cua Sedgewick-Wayne, nen cot params phai co khoa backshift.
     */
    private static void checkAlgorithmParams(String[] row, int lineNumber, List<Issue> issues) {
        String algo = row[CsvSchema.ALGORITHM_INDEX];
        String params = row[CsvSchema.PARAMS_INDEX];
        if ("lp-backshift".equals(algo)) {
            String method = CsvParams.value(params, CsvParams.KEY);
            if (method == null || method.length() == 0) {
                issues.add(new Issue(lineNumber, "lp-backshift phai ghi ro co che xoa trong params"
                        + " (backshift=knuth-r hoac backshift=sedgewick-wayne)"));
            } else if (!"knuth-r".equals(method) && !"sedgewick-wayne".equals(method)) {
                issues.add(new Issue(lineNumber, "co che backshift khong hop le: " + method));
            }
        }
    }

    /** Kiem tra cac cot so: rong hoac phai parse duoc va khong am. */
    private static void checkNumericColumns(String[] row, int lineNumber, List<Issue> issues) {
        for (int index : NON_NEGATIVE_LONG_COLUMNS) {
            String value = row[index];
            if (value.length() == 0) {
                continue;
            }
            if (!value.matches("[0-9]+")) {
                issues.add(new Issue(lineNumber, "cot " + CsvSchema.COLUMNS[index]
                        + " phai la so nguyen khong am, nhung la '" + value + "'"));
            }
        }
        for (int index : NON_NEGATIVE_DOUBLE_COLUMNS) {
            String value = row[index];
            if (value.length() == 0) {
                continue;
            }
            double parsed;
            try {
                parsed = Double.parseDouble(value);
            } catch (NumberFormatException exception) {
                issues.add(new Issue(lineNumber, "cot " + CsvSchema.COLUMNS[index]
                        + " khong phai so thap phan: '" + value + "'"));
                continue;
            }
            if (Double.isNaN(parsed) || Double.isInfinite(parsed) || parsed < 0.0) {
                issues.add(new Issue(lineNumber, "cot " + CsvSchema.COLUMNS[index]
                        + " phai khong am va huu han: '" + value + "'"));
            }
        }
        String rebuildCount = row[CsvSchema.REBUILD_COUNT_INDEX];
        if (rebuildCount.length() > 0 && !rebuildCount.matches("[0-9]+")) {
            issues.add(new Issue(lineNumber, "rebuild_count phai la so nguyen khong am"));
        }
        String dibMax = row[CsvSchema.DIB_MAX_INDEX];
        if (dibMax.length() > 0 && !dibMax.matches("[0-9]+")) {
            issues.add(new Issue(lineNumber, "dib_max phai la so nguyen khong am"));
        }
    }

    /** Kiem tra input_sha256 khi co gia tri phai la 64 ky tu hex. */
    private static void checkInputSha256(String[] row, int lineNumber, List<Issue> issues) {
        String sha = row[CsvSchema.INPUT_SHA256_INDEX];
        if (sha.length() == 0) {
            return;
        }
        if (!sha.matches("[0-9a-f]{64}")) {
            issues.add(new Issue(lineNumber, "input_sha256 phai la 64 ky tu hex"));
        }
    }

    /**
     * So sanh output_check theo dataset, p, load, C, queries va seed.
     * Day la bat bien quan trong nhat: cung du lieu thi ket qua get phai giong nhau.
     */
    private static void checkOutputCheckAgreement(List<String[]> rows, List<Issue> issues) {
        Map<String, List<String[]>> byGroup = new LinkedHashMap<String, List<String[]>>();
        for (String[] row : rows) {
            if (row.length != CsvSchema.COLUMNS.length) {
                continue;
            }
            if (!"ok".equals(row[CsvSchema.STATUS_INDEX])) {
                continue;
            }
            String outputCheck = row[CsvSchema.OUTPUT_CHECK_INDEX];
            if (outputCheck.length() == 0) {
                continue;
            }
            String group = getOutputCheckGroupKey(row) + "|" + row[CsvSchema.PHASE_INDEX];
            List<String[]> entries = byGroup.get(group);
            if (entries == null) {
                entries = new ArrayList<String[]>();
                byGroup.put(group, entries);
            }
            entries.add(row);
        }
        for (Map.Entry<String, List<String[]>> entry : byGroup.entrySet()) {
            List<String[]> entries = entry.getValue();
            String reference = entries.get(0)[CsvSchema.OUTPUT_CHECK_INDEX];
            String referenceAlgorithm = entries.get(0)[CsvSchema.ALGORITHM_INDEX] + "/"
                    + entries.get(0)[CsvSchema.VARIANT_INDEX];
            for (int i = 1; i < entries.size(); i++) {
                String[] row = entries.get(i);
                String value = row[CsvSchema.OUTPUT_CHECK_INDEX];
                if (!reference.equals(value)) {
                    issues.add(new Issue(0, "output_check khac nhau trong nhom ("
                            + entry.getKey() + "): " + referenceAlgorithm + " = " + reference
                            + " nhung " + row[CsvSchema.ALGORITHM_INDEX] + "/"
                            + row[CsvSchema.VARIANT_INDEX] + " = " + value));
                }
            }
        }
    }

    private static String getOutputCheckGroupKey(String[] row) {
        String params = row[CsvSchema.PARAMS_INDEX];
        StringBuilder sb = new StringBuilder();
        sb.append(row[CsvSchema.DATASET_INDEX]);
        sb.append("|p=").append(valueOrEmpty(CsvParams.value(params, "p")));
        sb.append("|a=").append(valueOrEmpty(CsvParams.value(params, "alpha")));
        sb.append("|l=").append(valueOrEmpty(CsvParams.value(params, "load")));
        sb.append("|c=").append(valueOrEmpty(CsvParams.value(params, "c")));
        sb.append("|q=").append(valueOrEmpty(CsvParams.value(params, "queries")));
        sb.append('|').append(row[CsvSchema.SEED_INDEX]);
        return sb.toString();
    }

    private static String valueOrEmpty(String value) {
        return value == null ? "" : value;
    }

    private static void checkRq2ControlAgreement(List<String[]> rows, List<Issue> issues) {
        Map<String, Map<String, String[]>> byProcess =
                new LinkedHashMap<String, Map<String, String[]>>();
        for (String[] row : rows) {
            if (row.length != CsvSchema.COLUMNS.length
                    || !"rq2".equals(row[CsvSchema.DATASET_INDEX])
                    || !"ok".equals(row[CsvSchema.STATUS_INDEX])) {
                continue;
            }
            String procId = row[CsvSchema.PROC_ID_INDEX];
            Map<String, String[]> phases = byProcess.get(procId);
            if (phases == null) {
                phases = new HashMap<String, String[]>();
                byProcess.put(procId, phases);
            }
            phases.put(row[CsvSchema.PHASE_INDEX], row);
        }
        for (Map.Entry<String, Map<String, String[]>> entry : byProcess.entrySet()) {
            Map<String, String[]> phases = entry.getValue();
            checkControlPair(entry.getKey(), "present", "ctl_present", phases, issues);
            checkControlPair(entry.getKey(), "absent", "ctl_absent", phases, issues);
        }
    }

    private static void checkControlPair(String procId, String queryPhase,
            String controlPhase, Map<String, String[]> phases, List<Issue> issues) {
        String[] query = phases.get(queryPhase);
        String[] control = phases.get(controlPhase);
        if (query == null || control == null) {
            return;
        }
        String queryResult = query[CsvSchema.OUTPUT_CHECK_INDEX];
        String controlResult = control[CsvSchema.OUTPUT_CHECK_INDEX];
        if (!queryResult.equals(controlResult)) {
            issues.add(new Issue(0, "output_check cua " + controlPhase + " khac "
                    + queryPhase + " trong proc_id " + procId + ": "
                    + controlResult + " != " + queryResult));
        }
    }

    /**
     * Kiem tra moi nhom (algorithm, variant, cau hinh, seed) cua RQ1 co du ba pha.
     * Nhom thieu pha thuong do tien trinh chet som, nen bao de nguoi do biet.
     */
    private static void checkPhaseCoverage(List<String[]> rows, List<Issue> issues) {
        Map<String, Set<String>> phasesByGroup = new HashMap<String, Set<String>>();
        Map<String, String> datasetByProcess = new HashMap<String, String>();
        for (String[] row : rows) {
            if (row.length != CsvSchema.COLUMNS.length) {
                continue;
            }
            String dataset = row[CsvSchema.DATASET_INDEX];
            if ("rq2".equals(dataset) || "rq3".equals(dataset)) {
                String procId = row[CsvSchema.PROC_ID_INDEX];
                Set<String> phases = phasesByGroup.get(procId);
                if (phases == null) {
                    phases = new HashSet<String>();
                    phasesByGroup.put(procId, phases);
                    datasetByProcess.put(procId, dataset);
                }
                phases.add(row[CsvSchema.PHASE_INDEX]);
                continue;
            }
            if (!"rq1a".equals(dataset) && !"rq1b".equals(dataset)
                    && !"rq1clust".equals(dataset)) {
                continue;
            }
            String group = getFullGroupKey(row, true);
            Set<String> phases = phasesByGroup.get(group);
            if (phases == null) {
                phases = new HashSet<String>();
                phasesByGroup.put(group, phases);
            }
            phases.add(row[CsvSchema.PHASE_INDEX]);
        }
        for (Map.Entry<String, Set<String>> entry : phasesByGroup.entrySet()) {
            String dataset = datasetByProcess.get(entry.getKey());
            if ("rq2".equals(dataset) || "rq3".equals(dataset)) {
                List<String> required = RunConfig.phasesForDataset(dataset);
                if (!entry.getValue().containsAll(required)) {
                    issues.add(new Issue(0, "proc_id " + entry.getKey()
                            + " thieu pha " + dataset + " " + required
                            + ", co: " + entry.getValue()));
                }
                continue;
            }
            Set<String> phases = entry.getValue();
            if (!phases.contains("build") || !phases.contains("present")
                    || !phases.contains("absent")) {
                issues.add(new Issue(0, "nhom " + entry.getKey()
                        + " thieu pha RQ1 (build/present/absent), co: " + phases));
            }
        }
    }

    /**
     * Kiem tra do dao dong giua cac luot cung mot cau hinh (P3.4).
     *
     * Theo C4 va quy dinh 10.1, mot luot chay lai phai nam trong khoang
     * 0,5 - 2 lan median. Luot lech khoi khoang nay la dau hieu nhieu may do
     * hoac loi, nen bao de nguoi do kiem tra lai.
     */
    private static void checkRepeatVariance(List<String[]> rows, List<Issue> issues) {
        Map<String, List<double[]>> elapsedByGroup =
                new LinkedHashMap<String, List<double[]>>();
        for (String[] row : rows) {
            if (row.length != CsvSchema.COLUMNS.length) {
                continue;
            }
            if (!"ok".equals(row[CsvSchema.STATUS_INDEX])) {
                continue;
            }
            String elapsed = row[CsvSchema.ELAPSED_MS_INDEX];
            if (elapsed.length() == 0) {
                continue;
            }
            double value;
            try {
                value = Double.parseDouble(elapsed);
            } catch (NumberFormatException exception) {
                continue;
            }
            String group = getFullGroupKey(row, true) + "|" + row[CsvSchema.PHASE_INDEX];
            List<double[]> samples = elapsedByGroup.get(group);
            if (samples == null) {
                samples = new ArrayList<double[]>();
                elapsedByGroup.put(group, samples);
            }
            samples.add(new double[] {value, Double.parseDouble(row[CsvSchema.REPEAT_INDEX])});
        }
        for (Map.Entry<String, List<double[]>> entry : elapsedByGroup.entrySet()) {
            List<double[]> samples = entry.getValue();
            if (samples.size() < 2) {
                continue; // chi mot luot thi khong co gi de so
            }
            double[] sorted = new double[samples.size()];
            for (int i = 0; i < samples.size(); i++) {
                sorted[i] = samples.get(i)[0];
            }
            java.util.Arrays.sort(sorted);
            double median = sorted.length % 2 == 1
                    ? sorted[sorted.length / 2]
                    : (sorted[sorted.length / 2 - 1] + sorted[sorted.length / 2]) / 2.0;
            if (median <= 0.0) {
                continue;
            }
            for (double[] sample : samples) {
                double ratio = sample[0] / median;
                if (ratio < 0.5 || ratio > 2.0) {
                    issues.add(new Issue(0, "do dao dong giua cac luot lon (nhom "
                            + entry.getKey() + "): repeat=" + (long) sample[1]
                            + " elapsed_ms=" + sample[0] + " = " + ratio
                            + " lan median " + median + " (ngoai 0.5-2 lan)"));
                }
            }
        }
    }

    /** Diem vao CLI: kiem tra cac file CSV duoc truyen vao. */
    public static void main(String[] args) throws IOException {
        boolean latest = false;
        List<String> files = new ArrayList<String>();
        for (String arg : args) {
            if ("--latest".equals(arg)) {
                latest = true;
            } else {
                files.add(arg);
            }
        }
        if (files.isEmpty()) {
            System.out.println("Usage: java -cp hashbench/out hashbench.CsvValidator [--latest] <file.csv> [...]");
            System.exit(2);
            return;
        }
        int totalIssues = 0;
        for (String arg : files) {
            Path file = java.nio.file.Paths.get(arg);
            Report report = validate(file, true, latest);
            report.print();
            totalIssues += report.issues.size();
        }
        if (totalIssues > 0) {
            System.out.println("KET QUA: FAIL (" + totalIssues + " vi pham)");
            System.exit(1);
        }
        System.out.println("KET QUA: PASS");
    }
}
