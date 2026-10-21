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

    /** Mở file CSV UTF-8 để ghi mới. */
    public CsvWriter(Path path) throws IOException {
        this.writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8);
    }

    /** Ghi hàng tiêu đề theo đúng thứ tự của CsvSchema. */
    public void writeHeader() throws IOException {
        writeRow(CsvSchema.COLUMNS);
    }

    /** Ghi một hàng 20 cột; giá trị null được ghi thành ô trống. */
    public void writeRow(String[] values) throws IOException {
        if (values == null || values.length != CsvSchema.COLUMNS.length) {
            throw new IllegalArgumentException("Hàng CSV phải có đúng 20 cột");
        }
        for (int i = 0; i < values.length; i++) {
            if (i > 0) {
                writer.write(',');
            }
            writer.write(escape(values[i] == null ? "" : values[i]));
        }
        writer.newLine();
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
