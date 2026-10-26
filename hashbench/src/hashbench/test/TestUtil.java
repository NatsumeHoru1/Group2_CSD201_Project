package hashbench.test;

import hashbench.HashIndex;
import java.lang.reflect.Method;

/** Tiện ích chung cho các ca kiểm thử. */
public final class TestUtil {
    private TestUtil() {}

    /** Gọi validate() công khai (nếu lớp có) bằng reflection; ném lại IllegalStateException của cài đặt. */
    public static void validate(HashIndex t) {
        try {
            Method m = t.getClass().getMethod("validate");
            m.invoke(t);
        } catch (NoSuchMethodException ignored) {
            // Một số bảng kiểm thử không có validate(); bỏ qua.
        } catch (Exception e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            if (cause instanceof RuntimeException) throw (RuntimeException) cause;
            throw new IllegalStateException(cause);
        }
    }

    /** Giá trị xác định theo khóa, không bao giờ là NOT_FOUND. */
    public static int valueOf(long key) {
        long mixed = key ^ (key >>> 32);
        int v = (int) (mixed & 0x7fffffffL);
        return v == 0 ? 1 : v;
    }
}
