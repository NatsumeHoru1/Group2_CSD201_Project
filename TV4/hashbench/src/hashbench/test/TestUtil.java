package hashbench.test;

import hashbench.HashIndex;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Set;

public final class TestUtil {
    /** Bản ĐỘC LẬP của hằng/hàm băm (không gọi Hashing của P1) để test có giá trị đối chứng. */
    public static final long FIB = 0x9E3779B97F4A7C15L;

    private TestUtil() {}

    public static int homeOf(long key, int p) {
        return (int) ((key * FIB) >>> (64 - p));
    }

    /** Tìm count khóa >= 0 có home == home, chưa nằm trong used (và thêm vào used). */
    public static long[] keysWithHome(int p, int home, int count, Set<Long> used) {
        long[] out = new long[count];
        int c = 0;
        for (long k = 0; c < count; k++) {
            if (k < 0) throw new IllegalStateException("hết khóa");
            if (used.contains(k)) continue;
            if (homeOf(k, p) == home) { out[c++] = k; used.add(k); }
        }
        return out;
    }

    /** Gọi validate() công khai (nếu lớp có) bằng reflection; ném lại IllegalStateException của cài đặt. */
    public static void validate(HashIndex t) {
        Method m;
        try { m = t.getClass().getMethod("validate"); } catch (NoSuchMethodException e) { return; }
        try { m.invoke(t); }
        catch (InvocationTargetException e) {
            Throwable c = e.getCause();
            if (c instanceof RuntimeException) throw (RuntimeException) c;
            if (c instanceof Error) throw (Error) c;
            throw new AssertionError(c);
        } catch (IllegalAccessException e) { throw new AssertionError(e); }
    }

    /** Chạy r trong luồng riêng; quá ms mà chưa xong thì coi là vòng lặp vô hạn (lỗi giới hạn dò M ô). */
    public static void withTimeout(String what, long ms, final Runnable r) {
        final Throwable[] err = new Throwable[1];
        Thread th = new Thread(new Runnable() {
            public void run() { try { r.run(); } catch (Throwable t) { err[0] = t; } }
        });
        th.setDaemon(true);
        th.start();
        try { th.join(ms); } catch (InterruptedException e) { throw new AssertionError("bị ngắt"); }
        if (th.isAlive()) throw new AssertionError(what + " không dừng sau " + ms + " ms (nghi lặp vô hạn)");
        if (err[0] instanceof Error) throw (Error) err[0];
        if (err[0] instanceof RuntimeException) throw (RuntimeException) err[0];
        if (err[0] != null) throw new AssertionError(err[0]);
    }

    /** Giá trị xác định theo khóa, không bao giờ là NOT_FOUND. */
    public static int valueOf(long key) {
        int v = (int) (key ^ (key >>> 32)) * 0x9E3779B1;
        return v == HashIndex.NOT_FOUND ? 0 : v;
    }
}
