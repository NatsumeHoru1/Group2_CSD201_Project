package hashbench;

/**
 * Bổ sung tham số mô tả cơ chế đặc biệt vào cột params của CSV.
 *
 * Quy định của đề 2 yêu cầu với lp-backshift phải ghi rõ đã dùng
 * Algorithm R của Knuth hay cách chèn lại cụm của Sedgewick-Wayne.
 * Hàm này chỉ nối thêm một cặp key=value, không sửa các cặp đã có.
 *
 * Phụ trách: TV6.
 */
public final class CsvParams {
    /** Khóa tham số mô tả cơ chế xóa dịch lùi. */
    public static final String KEY = "backshift";

    private CsvParams() {
    }

    /**
     * Trả về chuỗi params đã bổ sung cơ chế của thuật toán.
     * Thuật toán khác lp-backshift giữ nguyên chuỗi params.
     */
    public static String augment(String algo, String params) {
        if (!"lp-backshift".equals(algo)) {
            return params;
        }
        if (params == null) {
            params = "";
        }
        if (params.contains(KEY + "=")) {
            return params;
        }
        if (params.length() == 0) {
            return KEY + "=" + LinearProbingBackshiftTable.BACKSHIFT_METHOD;
        }
        return params + ";" + KEY + "=" + LinearProbingBackshiftTable.BACKSHIFT_METHOD;
    }

    /** Trả về giá trị của một tham số trong chuỗi params, hoặc null nếu không có. */
    public static String value(String params, String key) {
        if (params == null || key == null) {
            return null;
        }
        String[] parts = params.split(";", -1);
        for (String part : parts) {
            int at = part.indexOf('=');
            if (at > 0 && part.substring(0, at).equals(key)) {
                return part.substring(at + 1);
            }
        }
        return null;
    }
}
