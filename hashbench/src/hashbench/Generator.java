package hashbench;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.SplittableRandom;

/**
 * Bộ sinh khóa ngẫu nhiên và tính SHA-256 đúng quy định QĐC 6.7.
 * Phụ trách: TV2
 */
public final class Generator {

    /** 3 seed cố định được TV2 chốt dùng cho toàn bộ thí nghiệm (RQ1, RQ2, RQ3). */
    public static final long[] FIXED_SEEDS = { 1L, 2L, 3L };

    private static final char[] HEX_CHARS = "0123456789abcdef".toCharArray();

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
     * Sinh bộ khóa dồn cụm (Clustered Keys) cho thí nghiệm RQ1 đúng đặc tả đề bài:
     * - 10% số khóa có h(k) < M/64 (tập trung va chạm ở đầu bảng).
     * - 90% số khóa còn lại phân bố đều trên toàn bảng.
     */
    public static long[] generateClusteredKeys(long seed, int count, int p) {
        if (p < 1 || p > 30) {
            throw new IllegalArgumentException("p phải nằm trong khoảng 1..30");
        }
        if (count < 0) {
            throw new IllegalArgumentException("count phải >= 0");
        }
        SplittableRandom rng = new SplittableRandom(seed);
        LongSet set = new LongSet(count);
        long[] keys = new long[count];

        int clusteredLimit = (1 << p) / 64;
        if (clusteredLimit <= 0) {
            clusteredLimit = 1;
        }

        int clusterCount = (int) Math.round(count * 0.10);
        int i = 0;

        // 10% khóa có h(k) < M / 64
        while (i < clusterCount) {
            long k = rng.nextLong(Long.MAX_VALUE);
            if (Hashing.hash(k, p) < clusteredLimit) {
                if (set.add(k)) {
                    keys[i++] = k;
                }
            }
        }

        // 90% khóa còn lại phân bố đều trên toàn bảng
        while (i < count) {
            long k = rng.nextLong(Long.MAX_VALUE);
            if (set.add(k)) {
                keys[i++] = k;
            }
        }

        return keys;
    }

    /**
     * Phiên bản tương thích nhận thêm clusterCenter (mặc định gom cụm ở đầu bảng h(k) < M/64).
     */
    public static long[] generateClusteredKeys(long seed, int count, int p, int clusterCenter) {
        return generateClusteredKeys(seed, count, p);
    }

    /**
     * Chuyển mảng byte sang chuỗi Hex dùng bảng ký tự tĩnh (tra cứu O(1), không dùng String.format).
     */
    public static String bytesToHex(byte[] bytes) {
        char[] hexChars = new char[bytes.length * 2];
        for (int i = 0; i < bytes.length; i++) {
            int v = bytes[i] & 0xFF;
            hexChars[i * 2] = HEX_CHARS[v >>> 4];
            hexChars[i * 2 + 1] = HEX_CHARS[v & 0x0F];
        }
        return new String(hexChars);
    }

    /**
     * Tính SHA-256 của mảng khóa theo thứ tự Big-Endian, băm từng khối 8KB.
     * Dùng bảng hex tra cứu nhanh thay vì String.format từng byte.
     */
    public static String computeSHA256(long[] keys) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            updateDigest(md, keys);
            return bytesToHex(md.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Hệ thống thiếu thuật toán SHA-256", e);
        }
    }

    /**
     * Tính SHA-256 nối tiếp hai mảng theo thứ tự Big-Endian mà không cần ghép mảng.
     */
    public static String computeSHA256(long[] first, long[] second) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            if (first != null) {
                updateDigest(md, first);
            }
            if (second != null) {
                updateDigest(md, second);
            }
            return bytesToHex(md.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new RuntimeException("Hệ thống thiếu thuật toán SHA-256", exception);
        }
    }

    /**
     * Đưa byte Big-Endian của một mảng khóa vào MessageDigest theo khối 8KB để tránh cấp phát bộ nhớ lớn.
     */
    private static void updateDigest(MessageDigest digest, long[] keys) {
        if (keys == null || keys.length == 0) {
            return;
        }
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
