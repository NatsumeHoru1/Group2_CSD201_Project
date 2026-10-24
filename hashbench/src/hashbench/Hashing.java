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
     * Hàm băm Fibonacci 64-bit sang bảng có kích thước M = 2^p.
     * Công thức: (int) ((key * FIBONACCI_CONSTANT) >>> (64 - p))
     */
    public static int hash(long key, int p) {
        return (int) ((key * FIBONACCI_CONSTANT) >>> (64 - p));
    }

    /**
     * Kiểm tra tính toàn vẹn của hằng số Fibonacci khi khởi động.
     */
    public static boolean verifyConstant() {
        return Long.toUnsignedString(FIBONACCI_CONSTANT).equals(EXPECTED_UNSIGNED_STR);
    }

    /**
     * Tìm một khóa long >= 0 có chỉ số băm home đúng bằng targetHome.
     * Dùng cho TV3, TV4, TV5 khi dựng 6 ca kiểm thử bắt buộc.
     */
    public static long findKeyWithHome(int targetHome, int p, SplittableRandom rng) {
        if (p < 1 || p > 30) {
            throw new IllegalArgumentException("p phải nằm trong khoảng 1..30");
        }
        int capacity = 1 << p;
        if (targetHome < 0 || targetHome >= capacity) {
            throw new IllegalArgumentException("targetHome phải nằm trong [0, " + (capacity - 1) + "]");
        }
        while (true) {
            long candidate = rng.nextLong(Long.MAX_VALUE); // Khóa luôn >= 0
            if (hash(candidate, p) == targetHome) {
                return candidate;
            }
        }
    }
}
