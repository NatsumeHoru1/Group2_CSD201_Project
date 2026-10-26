package hashbench;

import java.io.BufferedWriter;
import java.io.Closeable;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/** Ghi tiêu đề và hàng dữ liệu theo lược đồ CSV dùng chung. */
public final class CsvWriter implements Closeable {
    private final BufferedWriter writer;
    private boolean headerWritten;

    /** Mở file CSV UTF-8 ở chế độ ghi mới. */
    public CsvWriter(Path path) throws IOException {
        this(path, false);
    }

    /** Mở file CSV để append hoặc ghi mới, tạo thư mục cha nếu cần. */
    public CsvWriter(Path path, boolean append) throws IOException {
        Path parent = path.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        boolean existingData = append && Files.exists(path) && Files.size(path) > 0L;
        if (append) {
            this.writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8,
                    java.nio.file.StandardOpenOption.CREATE,
                    java.nio.file.StandardOpenOption.WRITE,
                    java.nio.file.StandardOpenOption.APPEND);
        } else {
            this.writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8,
                    java.nio.file.StandardOpenOption.CREATE,
                    java.nio.file.StandardOpenOption.WRITE,
                    java.nio.file.StandardOpenOption.TRUNCATE_EXISTING);
        }
        this.headerWritten = existingData;
    }

    /** Chỉ ghi header một lần và chỉ khi file chưa có nội dung. */
    public void writeHeader() throws IOException {
        if (headerWritten) {
            return;
        }
        writeRow(CsvSchema.COLUMNS);
        headerWritten = true;
    }

    /** Ghi một hàng đúng số cột schema; giá trị null được ghi thành ô trống. */
    public void writeRow(String[] values) throws IOException {
        if (values == null || values.length != CsvSchema.COLUMNS.length) {
            throw new IllegalArgumentException(
                    "Hàng CSV phải có đúng " + CsvSchema.COLUMNS.length + " cột");
        }
        for (int i = 0; i < values.length; i++) {
            if (i > 0) {
                writer.write(',');
            }
            writer.write(escape(values[i] == null ? "" : values[i]));
        }
        writer.newLine();
    }

    /**
     * Điền metadata đã biết. TODO: input_sha256, generator_version,
     * heap_before_bytes, heap_peak_bytes, ops_count, output_check và các chỉ số riêng
     * chưa có nguồn dữ liệu nên để trống.
     */
    public void writePhaseRow(Runner.PhaseResult result, String wallMs, RunConfig config,
            String gitCommit)
            throws IOException {
        if (result == null || config == null) {
            throw new IllegalArgumentException("result và config không được null");
        }
        String[] values = new String[CsvSchema.COLUMNS.length];
        values[CsvSchema.RUN_ID_INDEX] = result.runId();
        values[CsvSchema.RQ_INDEX] = config.rq;
        values[CsvSchema.ALGORITHM_INDEX] = config.algo;
        values[CsvSchema.VARIANT_INDEX] = config.variant;
        values[CsvSchema.DATASET_INDEX] = config.dataset;
        values[CsvSchema.N_INDEX] = Long.toString(config.n);
        values[CsvSchema.PARAMS_INDEX] = CsvParams.augment(config.algo, config.params)
                + ";keytype=str12";
        values[CsvSchema.SEED_INDEX] = Long.toString(config.seed);
        values[CsvSchema.REPEAT_INDEX] = Integer.toString(config.repeat);
        if ("ok".equals(result.status)) {
            values[CsvSchema.ELAPSED_MS_INDEX] = result.elapsedMs;
            values[CsvSchema.HEAP_BEFORE_BYTES_INDEX] = result.heapBefore;
            values[CsvSchema.HEAP_PEAK_BYTES_INDEX] = result.heapPeak;
            values[CsvSchema.OPS_COUNT_INDEX] = result.opsCount;
            values[CsvSchema.OUTPUT_CHECK_INDEX] = result.outputCheck;
        }
        values[CsvSchema.INPUT_SHA256_INDEX] = result.inputSha256;
        values[CsvSchema.GENERATOR_VERSION_INDEX] = result.generatorVersion;
        values[CsvSchema.STATUS_INDEX] = result.status;
        values[CsvSchema.XMX_INDEX] = config.xmx;
        values[CsvSchema.MACHINE_ID_INDEX] = config.machineId;
        values[CsvSchema.GIT_COMMIT_INDEX] = gitCommit;
        values[CsvSchema.PROC_ID_INDEX] = result.procId;
        values[CsvSchema.PHASE_INDEX] = result.phase;
        values[CsvSchema.WALL_MS_INDEX] = wallMs;
        values[CsvSchema.PHASE_NOTE_INDEX] = result.phaseNote;
        writeRow(values);
    }

    /** Tạo khóa dòng duy nhất mà không thêm cột ngoài schema đã yêu cầu. */
    public static String runId(String procId, String phase) {
        if (procId == null || phase == null) {
            throw new IllegalArgumentException("procId và phase không được null");
        }
        return procId + "_" + phase;
    }

    /** Định dạng số thập phân ổn định theo locale US với ba chữ số lẻ. */
    public static String formatDecimal(double value) {
        return String.format(Locale.US, "%.3f", Double.valueOf(value));
    }

    /** Thoát dấu phẩy, dấu nháy và xuống dòng theo quy tắc CSV. */
    private String escape(String value) {
        if (value.indexOf(',') < 0 && value.indexOf('"') < 0
                && value.indexOf('\n') < 0 && value.indexOf('\r') < 0) {
            return value;
        }
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }

    /** Đóng file CSV đã mở. */
    @Override
    public void close() throws IOException {
        writer.close();
    }
}
