package hashbench;

/**
 * Bo sung tham so mo ta co che dac biet vao cot params cua CSV.
 *
 * Quy dinh cua de 2 yeu cau voi lp-backshift phai ghi ro da dung
 * Algorithm R cua Knuth hay cach chen lai cum cua Sedgewick-Wayne.
 * Ham nay chi noi them mot cap key=value, khong sua cac cap da co.
 *
 * Phu trach: TV6.
 */
public final class CsvParams {
    /** Khoa tham so mo ta co che xoa dich lui. */
    public static final String KEY = "backshift";

    private CsvParams() {
    }

    /**
     * Tra ve chuoi params da bo sung co che cua thuat toan.
     * Thuat toan khac lp-backshift giu nguyen chuoi params.
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

    /** Tra ve gia tri cua mot tham so trong chuoi params, hoac null neu khong co. */
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
