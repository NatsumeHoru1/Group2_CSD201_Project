package hashbench;

import java.util.SplittableRandom;

/**
 * Thư viện băm Fibonacci 64-bit cố định theo Đề tài 2.
 * Phụ trách: TV2
 */
public final class Hashing {
    // Hằng số tỉ lệ vàng 64-bit: 2^64 / phi
    public static final long FIBONACCI_CONSTANT = 0x9E3779B97F4A7C15L;
    public static final String EXPECTED_UNSIGNED_STR = "11400714819323198485";

    private Hashing() {
    }

    /**
     * Băm khóa String bằng hashCode rồi nhân hằng số Fibonacci 64-bit.
     */
    public static int hash(String key, int p) {
        if (key == null) {
            throw new IllegalArgumentException("key không được null");
        }
        if (p < 1 || p > 30) {
            throw new IllegalArgumentException("p phải nằm trong khoảng 1..30");
        }
        return (int) (((long) key.hashCode() * FIBONACCI_CONSTANT) >>> (64 - p));
    }

    /**
     * Kiểm tra tính toàn vẹn của hằng số Fibonacci khi khởi động.
     */
    public static boolean verifyConstant() {
        return Long.toUnsignedString(FIBONACCI_CONSTANT).equals(EXPECTED_UNSIGNED_STR);
    }

    /**
     * Tìm một khóa String có chỉ số băm home đúng bằng targetHome.
     */
    public static String findKeyWithHome(int targetHome, int p, SplittableRandom rng) {
        if (p < 1 || p > 30) {
            throw new IllegalArgumentException("p phải nằm trong khoảng 1..30");
        }
        if (rng == null) {
            throw new IllegalArgumentException("rng không được null");
        }
        int capacity = 1 << p;
        if (targetHome < 0 || targetHome >= capacity) {
            throw new IllegalArgumentException("targetHome phải nằm trong [0, " + (capacity - 1) + "]");
        }
        while (true) {
            String candidate = KeyCodec.encode(rng.nextLong(KeyCodec.SPACE));
            if (hash(candidate, p) == targetHome) {
                return candidate;
            }
        }
    }
}
