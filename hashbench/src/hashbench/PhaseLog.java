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
        if (phase == null || phase.length() == 0 || phase.indexOf('\t') >= 0
                || phase.indexOf('\n') >= 0 || phase.indexOf('\r') >= 0) {
            throw new IllegalArgumentException("Tên pha không hợp lệ");
        }
        if (!isAllowedStatus(status)) {
            throw new IllegalArgumentException("Trạng thái không hợp lệ: " + status);
        }
        if (Double.isNaN(elapsedMs) || Double.isInfinite(elapsedMs) || elapsedMs < 0.0) {
            throw new IllegalArgumentException("elapsed_ms phải là số hữu hạn không âm");
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
            writer.write(String.format(Locale.US, "%.3f", Double.valueOf(elapsedMs)));
            writer.newLine();
            writer.flush();
        }
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
            if (fields.length != 3 || fields[0].length() == 0
                    || !isAllowedStatus(fields[1])) {
                throw new IOException("Dòng phase log không hợp lệ: " + line);
            }
            try {
                double elapsedMs = Double.parseDouble(fields[2]);
                if (Double.isNaN(elapsedMs) || Double.isInfinite(elapsedMs) || elapsedMs < 0.0) {
                    throw new NumberFormatException("elapsed_ms không hợp lệ");
                }
                entries.add(new Entry(fields[0], fields[1], fields[2]));
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

        private Entry(String phase, String status, String elapsedMs) {
            this.phase = phase;
            this.status = status;
            this.elapsedMs = elapsedMs;
        }
    }
}
