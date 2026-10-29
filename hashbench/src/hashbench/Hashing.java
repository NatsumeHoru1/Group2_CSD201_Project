package hashbench;

import java.util.SplittableRandom;

/**
 * Thu vien bam Fibonacci 64-bit co dinh theo De tai 2.
 * Phu trach: TV2
 */
public final class Hashing {
    // Hang so ti le vang 64-bit: 2^64 / phi
    public static final long FIBONACCI_CONSTANT = 0x9E3779B97F4A7C15L;
    public static final String EXPECTED_UNSIGNED_STR = "11400714819323198485";

    private Hashing() {
    }

    /**
     * Bam khoa String bang hashCode roi nhan hang so Fibonacci 64-bit.
     */
    public static int hash(String key, int p) {
        if (key == null) {
            throw new IllegalArgumentException("key khong duoc null");
        }
        if (p < 1 || p > 30) {
            throw new IllegalArgumentException("p phai nam trong khoang 1..30");
        }
        return (int) (((long) key.hashCode() * FIBONACCI_CONSTANT) >>> (64 - p));
    }

    /**
     * Kiem tra tinh toan ven cua hang so Fibonacci khi khoi dong.
     */
    public static boolean verifyConstant() {
        return Long.toUnsignedString(FIBONACCI_CONSTANT).equals(EXPECTED_UNSIGNED_STR);
    }

    /**
     * Tim mot khoa String co chi so bam home dung bang targetHome.
     */
    public static String findKeyWithHome(int targetHome, int p, SplittableRandom rng) {
        if (p < 1 || p > 30) {
            throw new IllegalArgumentException("p phai nam trong khoang 1..30");
        }
        if (rng == null) {
            throw new IllegalArgumentException("rng khong duoc null");
        }
        int capacity = 1 << p;
        if (targetHome < 0 || targetHome >= capacity) {
            throw new IllegalArgumentException("targetHome phai nam trong [0, " + (capacity - 1) + "]");
        }
        while (true) {
            String candidate = KeyCodec.encode(rng.nextLong(KeyCodec.SPACE));
            if (hash(candidate, p) == targetHome) {
                return candidate;
            }
        }
    }
}
