package hashbench;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Ghi và đọc các pha đã hoàn thành của một tiến trình con. */
public final class PhaseLog {
    private final Path file;

    /** Tạo log tại results/raw/tmp dưới thư mục làm việc hiện tại. */
    public PhaseLog(String procId) throws IOException {
        this(procId, Paths.get("."));
    }

    /** Tạo log tại results/raw/tmp dưới thư mục làm việc được chỉ định. */
    public PhaseLog(String procId, Path workingDirectory) throws IOException {
        if (procId == null || !procId.matches("[A-Za-z0-9_-]+")) {
            throw new IllegalArgumentException("proc_id chỉ được chứa chữ, số, _ hoặc -");
        }
        if (workingDirectory == null) {
            throw new IllegalArgumentException("workingDirectory không được null");
        }
        Path directory = workingDirectory.resolve(Paths.get("results", "raw", "tmp"));
        Files.createDirectories(directory);
        this.file = directory.resolve(procId + ".phases");
    }

    /** Mở file ở chế độ append, ghi xong flush và đóng ngay. */
    public void append(String phase, String status, double elapsedMs) throws IOException {
        append(phase, status, String.format(Locale.US, "%.3f", Double.valueOf(elapsedMs)),
                "", "", "", "", "", "");
    }

    /** Ghi toàn bộ số liệu pha vào một dòng TSV và đóng file ngay. */
    public void append(String phase, String status, String elapsedMs, String heapBefore,
            String heapPeak, String opsCount, String outputCheck, String inputSha256,
            String generatorVersion) throws IOException {
        appendInternal(phase, status, elapsedMs, heapBefore, heapPeak, opsCount, outputCheck,
                inputSha256, generatorVersion, null);
    }

    /** Ghi toàn bộ số liệu pha và tám thống kê mở rộng vào một dòng TSV. */
    public void append(String phase, String status, String elapsedMs, String heapBefore,
            String heapPeak, String opsCount, String outputCheck, String inputSha256,
            String generatorVersion, String tombRatio, String emptyRatio,
            String rebuildCount, String rebuildMs, String blockMedianMs,
            String blockMaxMs, String dibMean, String dibMax) throws IOException {
        appendInternal(phase, status, elapsedMs, heapBefore, heapPeak, opsCount,
                outputCheck, inputSha256, generatorVersion, new String[] {
                    tombRatio, emptyRatio, rebuildCount, rebuildMs, blockMedianMs,
                    blockMaxMs, dibMean, dibMax
                });
    }

    private void appendInternal(String phase, String status, String elapsedMs,
            String heapBefore, String heapPeak, String opsCount, String outputCheck,
            String inputSha256, String generatorVersion, String[] extendedFields)
            throws IOException {
        if (phase == null || phase.length() == 0 || phase.indexOf('\t') >= 0
                || phase.indexOf('\n') >= 0 || phase.indexOf('\r') >= 0) {
            throw new IllegalArgumentException("Tên pha không hợp lệ");
        }
        if (!isAllowedStatus(status)) {
            throw new IllegalArgumentException("Trạng thái không hợp lệ: " + status);
        }
        if (elapsedMs == null || heapBefore == null || heapPeak == null || opsCount == null
                || outputCheck == null || inputSha256 == null || generatorVersion == null) {
            throw new IllegalArgumentException("Trường phase log không được null");
        }
        if (extendedFields != null) {
            for (String value : extendedFields) {
                if (value == null) {
                    throw new IllegalArgumentException("Trường phase log không được null");
                }
            }
        }
        if (elapsedMs.length() > 0) {
            try {
                double elapsed = Double.parseDouble(elapsedMs);
                if (Double.isNaN(elapsed) || Double.isInfinite(elapsed) || elapsed < 0.0) {
                    throw new NumberFormatException("elapsed_ms không hợp lệ");
                }
            } catch (NumberFormatException exception) {
                throw new IllegalArgumentException("elapsed_ms không hợp lệ", exception);
            }
        }
        if ("ok".equals(status) && elapsedMs.length() == 0) {
            throw new IllegalArgumentException("Pha ok phải có elapsed_ms");
        }

        try (BufferedWriter writer = Files.newBufferedWriter(
                file,
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.WRITE,
                StandardOpenOption.APPEND)) {
            writer.write(phase);
            writer.write('\t');
            writer.write(status);
            writer.write('\t');
            writer.write(elapsedMs);
            writeField(writer, heapBefore);
            writeField(writer, heapPeak);
            writeField(writer, opsCount);
            writeField(writer, outputCheck);
            writeField(writer, inputSha256);
            writeField(writer, generatorVersion);
            if (extendedFields != null) {
                for (String value : extendedFields) {
                    writeField(writer, value);
                }
            }
            writer.newLine();
            writer.flush();
        }
    }

    /** Ghi một trường TSV đã được ngăn cách bằng tab. */
    private static void writeField(BufferedWriter writer, String value) throws IOException {
        if (value.indexOf('\t') >= 0 || value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0) {
            throw new IllegalArgumentException("Trường phase log không hợp lệ");
        }
        writer.write('\t');
        writer.write(value);
    }

    /** Đọc những dòng kết thúc bằng newline và bỏ qua phần cuối chưa hoàn chỉnh. */
    public List<Entry> readCompleted() throws IOException {
        List<Entry> entries = new ArrayList<Entry>();
        if (!Files.exists(file)) {
            return entries;
        }

        String content = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
        int completeEnd = content.lastIndexOf('\n');
        if (completeEnd < 0) {
            return entries;
        }

        String[] lines = content.substring(0, completeEnd).split("\n", -1);
        for (String line : lines) {
            if (line.endsWith("\r")) {
                line = line.substring(0, line.length() - 1);
            }
            if (line.length() == 0) {
                continue;
            }
            String[] fields = line.split("\t", -1);
            if ((fields.length != 9 && fields.length != 17) || fields[0].length() == 0
                    || !isAllowedStatus(fields[1])) {
                throw new IOException("Dòng phase log không hợp lệ: " + line);
            }
            if (fields.length == 9) {
                String[] extended = new String[17];
                System.arraycopy(fields, 0, extended, 0, fields.length);
                for (int i = 9; i < extended.length; i++) {
                    extended[i] = "";
                }
                fields = extended;
            }
            try {
                if (fields[2].length() > 0) {
                    double elapsedMs = Double.parseDouble(fields[2]);
                    if (Double.isNaN(elapsedMs) || Double.isInfinite(elapsedMs)
                            || elapsedMs < 0.0) {
                        throw new NumberFormatException("elapsed_ms không hợp lệ");
                    }
                } else if ("ok".equals(fields[1])) {
                    throw new NumberFormatException("Pha ok thiếu elapsed_ms");
                }
                entries.add(new Entry(fields));
            } catch (NumberFormatException exception) {
                throw new IOException("elapsed_ms không hợp lệ trong phase log: " + line, exception);
            }
        }
        return entries;
    }

    /** Chỉ chấp nhận các trạng thái CSV đã quy định. */
    private static boolean isAllowedStatus(String status) {
        return "ok".equals(status) || "timeout".equals(status) || "crash".equals(status)
                || "oom".equals(status) || "wrong".equals(status);
    }

    /** Trả về đường dẫn file phase log. */
    public Path path() {
        return file;
    }

    /** Lưu một dòng pha hoàn chỉnh đã đọc từ file. */
    public static final class Entry {
        public final String phase;
        public final String status;
        public final String elapsedMs;
        public final String heapBefore;
        public final String heapPeak;
        public final String opsCount;
        public final String outputCheck;
        public final String inputSha256;
        public final String generatorVersion;
        public final String tombRatio;
        public final String emptyRatio;
        public final String rebuildCount;
        public final String rebuildMs;
        public final String blockMedianMs;
        public final String blockMaxMs;
        public final String dibMean;
        public final String dibMax;

        private Entry(String[] fields) {
            this.phase = fields[0];
            this.status = fields[1];
            this.elapsedMs = fields[2];
            this.heapBefore = fields[3];
            this.heapPeak = fields[4];
            this.opsCount = fields[5];
            this.outputCheck = fields[6];
            this.inputSha256 = fields[7];
            this.generatorVersion = fields[8];
            this.tombRatio = fields[9];
            this.emptyRatio = fields[10];
            this.rebuildCount = fields[11];
            this.rebuildMs = fields[12];
            this.blockMedianMs = fields[13];
            this.blockMaxMs = fields[14];
            this.dibMean = fields[15];
            this.dibMax = fields[16];
        }
    }
}
