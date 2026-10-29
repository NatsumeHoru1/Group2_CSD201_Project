package hashbench;

import java.io.BufferedWriter;
import java.io.Closeable;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/** Ghi tieu de va hang du lieu theo luoc do CSV dung chung. */
public final class CsvWriter implements Closeable {
    private final BufferedWriter writer;
    private boolean headerWritten;

    /** Mo file CSV UTF-8 o che do ghi moi. */
    public CsvWriter(Path path) throws IOException {
        this(path, false);
    }

    /** Mo file CSV de append hoac ghi moi, tao thu muc cha neu can. */
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

    /** Chi ghi header mot lan va chi khi file chua co noi dung. */
    public void writeHeader() throws IOException {
        if (headerWritten) {
            return;
        }
        writeRow(CsvSchema.COLUMNS);
        headerWritten = true;
    }

    /** Ghi mot hang dung so cot schema; gia tri null duoc ghi thanh o trong. */
    public void writeRow(String[] values) throws IOException {
        if (values == null || values.length != CsvSchema.COLUMNS.length) {
            throw new IllegalArgumentException(
                    "Hang CSV phai co dung " + CsvSchema.COLUMNS.length + " cot");
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
     * Dien metadata da biet. TODO: input_sha256, generator_version,
     * heap_before_bytes, heap_peak_bytes, ops_count, output_check va cac chi so rieng
     * chua co nguon du lieu nen de trong.
     */
    public void writePhaseRow(Runner.PhaseResult result, String wallMs, RunConfig config,
            String gitCommit)
            throws IOException {
        if (result == null || config == null) {
            throw new IllegalArgumentException("result va config khong duoc null");
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
            values[CsvSchema.TOMB_RATIO_INDEX] = result.tombRatio;
            values[CsvSchema.EMPTY_RATIO_INDEX] = result.emptyRatio;
            values[CsvSchema.REBUILD_COUNT_INDEX] = result.rebuildCount;
            values[CsvSchema.REBUILD_MS_INDEX] = result.rebuildMs;
            values[CsvSchema.BLOCK_MEDIAN_MS_INDEX] = result.blockMedianMs;
            values[CsvSchema.BLOCK_MAX_MS_INDEX] = result.blockMaxMs;
            values[CsvSchema.DIB_MEAN_INDEX] = result.dibMean;
            values[CsvSchema.DIB_MAX_INDEX] = result.dibMax;
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

    /** Tao khoa dong duy nhat ma khong them cot ngoai schema da yeu cau. */
    public static String runId(String procId, String phase) {
        if (procId == null || phase == null) {
            throw new IllegalArgumentException("procId va phase khong duoc null");
        }
        return procId + "_" + phase;
    }

    /** Dinh dang so thap phan on dinh theo locale US voi ba chu so le. */
    public static String formatDecimal(double value) {
        return String.format(Locale.US, "%.3f", Double.valueOf(value));
    }

    /** Thoat dau phay, dau nhay va xuong dong theo quy tac CSV. */
    private String escape(String value) {
        if (value.indexOf(',') < 0 && value.indexOf('"') < 0
                && value.indexOf('\n') < 0 && value.indexOf('\r') < 0) {
            return value;
        }
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }

    /** Dong file CSV da mo. */
    @Override
    public void close() throws IOException {
        writer.close();
    }
}
