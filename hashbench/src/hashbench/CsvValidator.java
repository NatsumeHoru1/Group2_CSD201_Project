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
 * Kiểm tra các file CSV do runner ghi ra theo lược đồ 32 cột của CsvSchema.
 *
 * Bất biến được kiểm tra (theo spec_notes.txt mục A, B, D và CONVENTIONS.md):
 *  1. Header đúng thứ tự và đủ 32 cột; mọi hàng cũng có đúng 32 ô.
 *  2. status thuộc đúng tập {ok, timeout, crash, oom, wrong}.
 *  3. Lượt không ok thì elapsed_ms phải trống; lượt ok thì elapsed_ms phải là
 *     số thập phân có đúng 3 chữ số lẻ.
 *  4. phase_note = not_started chỉ đi kèm status timeout/crash/oom.
 *  5. run_id không trùng trong cùng một file và phải có dạng proc_id + "_" + phase.
 *  6. Không có dòng nào có status = wrong (đây là lỗi kết quả, phải báo).
 *  7. output_check phải bằng nhau giữa các thuật toán cùng cấu hình, load, C, seed và phase.
 *  8. RQ1 đủ ba pha; RQ2/RQ3 đủ các pha theo proc_id.
 *  9. Các cột số phải parse được; cột chưa đo được thì để trống, không ghi số 0.
 *
 * Lớp này chỉ đọc file, không sửa dòng nào trong CSV.
 *
 * Phụ trách: TV6.
 */
public final class CsvValidator {
    /** Các trạng thái hợp lệ theo quy định chung mục 6.7. */
    public static final Set<String> ALLOWED_STATUS = new HashSet<String>(Arrays.asList(
            "ok", "timeout", "crash", "oom", "wrong"));

    /** Các tên thuật toán theo Bảng 2 và Bảng 3 của đề 2. */
    public static final Set<String> ALLOWED_ALGORITHM = new HashSet<String>(Arrays.asList(
            "sc", "lp-tomb", "lp-backshift", "rh"));

    /** Các biến thể hợp lệ. */
    public static final Set<String> ALLOWED_VARIANT = new HashSet<String>(Arrays.asList(
            "base", "noearly"));

    /** Các mã dataset theo E3. */
    public static final Set<String> ALLOWED_DATASET = new HashSet<String>(Arrays.asList(
            "rq1a", "rq1b", "rq1clust", "rq2", "rq3", "test"));

    /** Các pha theo E2. */
    public static final Set<String> ALLOWED_PHASE = new HashSet<String>(Arrays.asList(
            "build", "present", "absent", "workload", "ctl_present", "ctl_absent"));

    /** Các cột phải là số nguyên không âm nếu có giá trị. */
    private static final int[] NON_NEGATIVE_LONG_COLUMNS = {
        CsvSchema.N_INDEX,
        CsvSchema.SEED_INDEX,
        CsvSchema.REPEAT_INDEX,
        CsvSchema.HEAP_BEFORE_BYTES_INDEX,
        CsvSchema.HEAP_PEAK_BYTES_INDEX,
        CsvSchema.OPS_COUNT_INDEX,
    };

    /** Các cột phải là số thập phân không âm nếu có giá trị. */
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

    /** Một vấn đề phát hiện được trong CSV. */
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

    /** Kết quả kiểm tra cho một file CSV. */
    public static final class Report {
        public final Path file;
        public final List<Issue> issues;
        public final int dataRows;

        Report(Path file, List<Issue> issues, int dataRows) {
            this.file = file;
            this.issues = issues;
            this.dataRows = dataRows;
        }

        /** Trả về true khi không có vấn đề nào. */
        public boolean isValid() {
            return issues.isEmpty();
        }

        /** In báo cáo ra màn hình. */
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
     * Kiểm tra một file CSV và trả về báo cáo (giữ nguyên mặc định không lọc dòng trùng).
     */
    public static Report validate(Path file, boolean checkCrossAlgorithm) throws IOException {
        return validate(file, checkCrossAlgorithm, false);
    }

    /**
     * Kiểm tra một file CSV và trả về báo cáo. Khi latest=true, chỉ giữ dòng cuối cùng của mỗi run_id.
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

    /** Đọc CSV và tách ô theo luật CSV, báo lỗi nếu dòng không đủ 32 ô. */
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

    /** Tách một dòng CSV theo luật dấu phẩy và dấu nháy kép. */
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

    /** Kiểm tra header có đúng tên và đúng thứ tự cột theo CsvSchema. */
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

    /** Kiểm tra từng dòng dữ liệu theo các bất biến của đề. */
    private static void checkRows(List<String[]> rows, List<Issue> issues) {
        Set<String> runIds = new HashSet<String>();
        for (int i = 0; i < rows.size(); i++) {
            int lineNumber = i + 2; // +1 cho header, +1 cho đánh số 1-based
            String[] row = rows.get(i);
            if (row.length != CsvSchema.COLUMNS.length) {
                continue; // đã báo ở readRows
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
     * Kiểm tra tham số riêng bắt buộc của từng thuật toán.
     * Với lp-backshift, đề 2 yêu cầu ghi rõ đã dùng Algorithm R của Knuth hay
     * cách chèn lại cụm của Sedgewick-Wayne, nên cột params phải có khoá backshift.
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

    /** Kiểm tra các cột số: rỗng hoặc phải parse được và không âm. */
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

    /** Kiểm tra input_sha256 khi có giá trị phải là 64 ký tự hex. */
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
     * So sánh output_check theo dataset, p, load, C, queries và seed.
     * Đây là bất biến quan trọng nhất: cùng dữ liệu thì kết quả get phải giống nhau.
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
     * Kiểm tra mỗi nhóm (algorithm, variant, cấu hình, seed) của RQ1 có đủ ba pha.
     * Nhóm thiếu pha thường do tiến trình chết sớm, nên báo để người đo biết.
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
     * Kiểm tra độ dao động giữa các lượt cùng một cấu hình (P3.4).
     *
     * Theo C4 và quy định 10.1, một lượt chạy lại phải nằm trong khoảng
     * 0,5 - 2 lần median. Lượt lệch khỏi khoảng này là dấu hiệu nhiễu máy đo
     * hoặc lỗi, nên báo để người đo kiểm tra lại.
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
                continue; // chỉ một lượt thì không có gì để so
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

    /** Điểm vào CLI: kiểm tra các file CSV được truyền vào. */
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
