package hashbench.test;

import hashbench.HashIndex;
import java.lang.reflect.Method;

/** Tien ich chung cho cac ca kiem thu. */
public final class TestUtil {
    private TestUtil() {}

    /** Goi validate() cong khai (neu lop co) bang reflection; nem lai IllegalStateException cua cai dat. */
    public static void validate(HashIndex t) {
        try {
            Method m = t.getClass().getMethod("validate");
            m.invoke(t);
        } catch (NoSuchMethodException ignored) {
            // Mot so bang kiem thu khong co validate(); bo qua.
        } catch (Exception e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            if (cause instanceof RuntimeException) throw (RuntimeException) cause;
            throw new IllegalStateException(cause);
        }
    }

    /** Gia tri xac dinh theo khoa, khong bao gio la NOT_FOUND. */
    public static int valueOf(String key) {
        if (key == null) throw new IllegalArgumentException("key khong duoc null");
        long id = hashbench.KeyCodec.decode(key);
        long mixed = id ^ (id >>> 32);
        int v = (int) (mixed & 0x7fffffffL);
        return v == 0 ? 1 : v;
    }
}
