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

/** Ghi va doc cac pha da hoan thanh cua mot tien trinh con. */
public final class PhaseLog {
    private final Path file;

    /** Tao log tai results/raw/tmp duoi thu muc lam viec hien tai. */
    public PhaseLog(String procId) throws IOException {
        this(procId, Paths.get("."));
    }

    /** Tao log tai results/raw/tmp duoi thu muc lam viec duoc chi dinh. */
    public PhaseLog(String procId, Path workingDirectory) throws IOException {
        if (procId == null || !procId.matches("[A-Za-z0-9_-]+")) {
            throw new IllegalArgumentException("proc_id chi duoc chua chu, so, _ hoac -");
        }
        if (workingDirectory == null) {
            throw new IllegalArgumentException("workingDirectory khong duoc null");
        }
        Path directory = workingDirectory.resolve(Paths.get("results", "raw", "tmp"));
        Files.createDirectories(directory);
        this.file = directory.resolve(procId + ".phases");
    }

    /** Mo file o che do append, ghi xong flush va dong ngay. */
    public void append(String phase, String status, double elapsedMs) throws IOException {
        append(phase, status, String.format(Locale.US, "%.3f", Double.valueOf(elapsedMs)),
                "", "", "", "", "", "");
    }

    /** Ghi toan bo so lieu pha vao mot dong TSV va dong file ngay. */
    public void append(String phase, String status, String elapsedMs, String heapBefore,
            String heapPeak, String opsCount, String outputCheck, String inputSha256,
            String generatorVersion) throws IOException {
        appendInternal(phase, status, elapsedMs, heapBefore, heapPeak, opsCount, outputCheck,
                inputSha256, generatorVersion, null);
    }

    /** Ghi toan bo so lieu pha va tam thong ke mo rong vao mot dong TSV. */
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
            throw new IllegalArgumentException("Ten pha khong hop le");
        }
        if (!isAllowedStatus(status)) {
            throw new IllegalArgumentException("Trang thai khong hop le: " + status);
        }
        if (elapsedMs == null || heapBefore == null || heapPeak == null || opsCount == null
                || outputCheck == null || inputSha256 == null || generatorVersion == null) {
            throw new IllegalArgumentException("Truong phase log khong duoc null");
        }
        if (extendedFields != null) {
            for (String value : extendedFields) {
                if (value == null) {
                    throw new IllegalArgumentException("Truong phase log khong duoc null");
                }
            }
        }
        if (elapsedMs.length() > 0) {
            try {
                double elapsed = Double.parseDouble(elapsedMs);
                if (Double.isNaN(elapsed) || Double.isInfinite(elapsed) || elapsed < 0.0) {
                    throw new NumberFormatException("elapsed_ms khong hop le");
                }
            } catch (NumberFormatException exception) {
                throw new IllegalArgumentException("elapsed_ms khong hop le", exception);
            }
        }
        if ("ok".equals(status) && elapsedMs.length() == 0) {
            throw new IllegalArgumentException("Pha ok phai co elapsed_ms");
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

    /** Ghi mot truong TSV da duoc ngan cach bang tab. */
    private static void writeField(BufferedWriter writer, String value) throws IOException {
        if (value.indexOf('\t') >= 0 || value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0) {
            throw new IllegalArgumentException("Truong phase log khong hop le");
        }
        writer.write('\t');
        writer.write(value);
    }

    /** Doc nhung dong ket thuc bang newline va bo qua phan cuoi chua hoan chinh. */
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
                throw new IOException("Dong phase log khong hop le: " + line);
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
                        throw new NumberFormatException("elapsed_ms khong hop le");
                    }
                } else if ("ok".equals(fields[1])) {
                    throw new NumberFormatException("Pha ok thieu elapsed_ms");
                }
                entries.add(new Entry(fields));
            } catch (NumberFormatException exception) {
                throw new IOException("elapsed_ms khong hop le trong phase log: " + line, exception);
            }
        }
        return entries;
    }

    /** Chi chap nhan cac trang thai CSV da quy dinh. */
    private static boolean isAllowedStatus(String status) {
        return "ok".equals(status) || "timeout".equals(status) || "crash".equals(status)
                || "oom".equals(status) || "wrong".equals(status);
    }

    /** Tra ve duong dan file phase log. */
    public Path path() {
        return file;
    }

    /** Luu mot dong pha hoan chinh da doc tu file. */
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
