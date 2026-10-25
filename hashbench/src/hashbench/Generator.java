package hashbench;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.SplittableRandom;

/**
 * Bộ sinh khóa ngẫu nhiên và tính SHA-256 đúng quy định QĐC 6.7.
 * Phụ trách: TV2
 */
public final class Generator {

    private Generator() {
    }

    /**
     * Sinh tập count khóa có mặt duy nhất (key >= 0).
     */
    public static long[] generatePresentKeys(long seed, int count) {
        SplittableRandom rng = new SplittableRandom(seed);
        LongSet set = new LongSet(count);
        long[] keys = new long[count];
        int i = 0;
        while (i < count) {
            long k = rng.nextLong(Long.MAX_VALUE);
            if (set.add(k)) {
                keys[i++] = k;
            }
        }
        return keys;
    }

    /**
     * Sinh tập count khóa vắng mặt (chắc chắn không giao với tập khóa có mặt).
     */
    public static long[] generateAbsentKeys(long seed, int count, LongSet presentSet) {
        SplittableRandom rng = new SplittableRandom(seed ^ 0xDEADBEEFCAFEBABEL);
        long[] absent = new long[count];
        int i = 0;
        while (i < count) {
            long k = rng.nextLong(Long.MAX_VALUE);
            if (!presentSet.contains(k)) {
                absent[i++] = k;
            }
        }
        return absent;
    }

    /**
     * Sinh bộ khóa dồn cụm (Clustered Keys) cho thí nghiệm RQ1.
     * Tập trung vào một số bucket liền kề để gây va chạm cục bộ.
     */
    public static long[] generateClusteredKeys(long seed, int count, int p, int clusterCenter) {
        SplittableRandom rng = new SplittableRandom(seed);
        LongSet set = new LongSet(count);
        long[] keys = new long[count];
        int i = 0;
        int spread = Math.max(4, (1 << p) / 64); // Bán kính cụm
        int mask = (1 << p) - 1;

        while (i < count) {
            long k = rng.nextLong(Long.MAX_VALUE);
            int h = Hashing.hash(k, p);
            int dist = (h - clusterCenter + (1 << p)) & mask;
            if (dist < spread) {
                if (set.add(k)) {
                    keys[i++] = k;
                }
            }
        }
        return keys;
    }

    /**
     * Tính SHA-256 của mảng khóa theo thứ tự Big-Endian, băm từng khối 8KB.
     * Không cấp phát cả mảng byte khổng lồ để tránh tràn heap (OOM).
     */
    public static String computeSHA256(long[] keys) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[8192]; // Buffer 8KB
            int bufPtr = 0;

            for (int i = 0; i < keys.length; i++) {
                long val = keys[i];
                // Ghi 8 bytes theo thứ tự Big-Endian vào buffer
                buffer[bufPtr++] = (byte) (val >>> 56);
                buffer[bufPtr++] = (byte) (val >>> 48);
                buffer[bufPtr++] = (byte) (val >>> 40);
                buffer[bufPtr++] = (byte) (val >>> 32);
                buffer[bufPtr++] = (byte) (val >>> 24);
                buffer[bufPtr++] = (byte) (val >>> 16);
                buffer[bufPtr++] = (byte) (val >>> 8);
                buffer[bufPtr++] = (byte) val;

                if (bufPtr == buffer.length) {
                    md.update(buffer, 0, buffer.length);
                    bufPtr = 0;
                }
            }
            if (bufPtr > 0) {
                md.update(buffer, 0, bufPtr);
            }

            byte[] digest = md.digest();
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (int b = 0; b < digest.length; b++) {
                sb.append(String.format("%02x", digest[b] & 0xFF));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Hệ thống thiếu thuật toán SHA-256", e);
        }
    }

    /**
     * Tính SHA-256 nối tiếp hai mảng theo thứ tự Big-Endian mà không ghép mảng.
     */
    public static String computeSHA256(long[] first, long[] second) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            updateDigest(md, first);
            updateDigest(md, second);
            byte[] digest = md.digest();
            StringBuilder result = new StringBuilder(digest.length * 2);
            for (int i = 0; i < digest.length; i++) {
                result.append(String.format("%02x", digest[i] & 0xFF));
            }
            return result.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new RuntimeException("Hệ thống thiếu thuật toán SHA-256", exception);
        }
    }

    /** Đưa byte Big-Endian của một mảng khóa vào cùng MessageDigest theo khối. */
    private static void updateDigest(MessageDigest digest, long[] keys) {
        byte[] buffer = new byte[8192];
        int pointer = 0;
        for (int i = 0; i < keys.length; i++) {
            long value = keys[i];
            buffer[pointer++] = (byte) (value >>> 56);
            buffer[pointer++] = (byte) (value >>> 48);
            buffer[pointer++] = (byte) (value >>> 40);
            buffer[pointer++] = (byte) (value >>> 32);
            buffer[pointer++] = (byte) (value >>> 24);
            buffer[pointer++] = (byte) (value >>> 16);
            buffer[pointer++] = (byte) (value >>> 8);
            buffer[pointer++] = (byte) value;
            if (pointer == buffer.length) {
                digest.update(buffer, 0, buffer.length);
                pointer = 0;
            }
        }
        if (pointer > 0) {
            digest.update(buffer, 0, pointer);
        }
    }
}
