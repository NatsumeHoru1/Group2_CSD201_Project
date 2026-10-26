package hashbench;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.SplittableRandom;

/**
 * Bộ sinh khóa String và tính SHA-256 theo thứ tự khóa.
 * Phụ trách: TV2
 */
public final class Generator {
    public static final long[] FIXED_SEEDS = { 1L, 2L, 3L };

    private static final char[] HEX_CHARS = "0123456789abcdef".toCharArray();

    private Generator() {
    }

    /** Sinh ID khóa có mặt, duy nhất trong miền của KeyCodec. */
    public static long[] generatePresentIds(long seed, int count) {
        checkCount(count);
        SplittableRandom rng = new SplittableRandom(seed);
        LongSet set = new LongSet(count);
        long[] ids = new long[count];
        int i = 0;
        while (i < count) {
            long id = rng.nextLong(KeyCodec.SPACE);
            if (set.add(id)) {
                ids[i++] = id;
            }
        }
        return ids;
    }

    /** Chuyển mảng ID thành khóa String đủ 12 ký tự. */
    public static String[] idsToKeys(long[] ids) {
        if (ids == null) {
            throw new IllegalArgumentException("ids không được null");
        }
        String[] keys = new String[ids.length];
        for (int i = 0; i < ids.length; i++) {
            keys[i] = KeyCodec.encode(ids[i]);
        }
        return keys;
    }

    /** Sinh khóa có mặt duy nhất và mã hóa chúng thành String. */
    public static String[] generatePresentKeys(long seed, int count) {
        return idsToKeys(generatePresentIds(seed, count));
    }

    /** Sinh ID vắng mặt, không trùng ID nào trong tập có mặt. */
    public static long[] generateAbsentIds(long seed, int count, long[] presentIds) {
        checkCount(count);
        if (presentIds == null) {
            throw new IllegalArgumentException("presentIds không được null");
        }
        LongSet presentSet = new LongSet(presentIds.length);
        for (int i = 0; i < presentIds.length; i++) {
            if (!presentSet.add(presentIds[i])) {
                throw new IllegalArgumentException("presentIds chứa ID trùng lặp");
            }
        }
        SplittableRandom rng = new SplittableRandom(seed ^ 0xDEADBEEFCAFEBABEL);
        LongSet absentSet = new LongSet(count);
        long[] ids = new long[count];
        int i = 0;
        while (i < count) {
            long id = rng.nextLong(KeyCodec.SPACE);
            if (!presentSet.contains(id) && absentSet.add(id)) {
                ids[i++] = id;
            }
        }
        return ids;
    }

    /** Sinh khóa vắng mặt, không giao với tập khóa có mặt. */
    public static String[] generateAbsentKeys(long seed, int count, long[] presentIds) {
        return idsToKeys(generateAbsentIds(seed, count, presentIds));
    }

    /** Sinh khóa dồn cụm với ít nhất 10% home nhỏ hơn max(1, M/64). */
    public static String[] generateClusteredKeys(long seed, int count, int p) {
        checkCount(count);
        if (p < 1 || p > 30) {
            throw new IllegalArgumentException("p phải nằm trong khoảng 1..30");
        }
        SplittableRandom rng = new SplittableRandom(seed);
        LongSet set = new LongSet(count);
        String[] keys = new String[count];
        int clusterLimit = Math.max(1, (1 << p) / 64);
        int clusterCount = (count + 9) / 10;
        int i = 0;
        while (i < clusterCount) {
            String key = KeyCodec.encode(rng.nextLong(KeyCodec.SPACE));
            if (Hashing.hash(key, p) < clusterLimit && set.add(KeyCodec.decode(key))) {
                keys[i++] = key;
            }
        }
        while (i < count) {
            long id = rng.nextLong(KeyCodec.SPACE);
            if (set.add(id)) {
                keys[i++] = KeyCodec.encode(id);
            }
        }
        return keys;
    }

    /** Chuyển mảng byte sang chuỗi Hex bằng tra cứu ký tự. */
    public static String bytesToHex(byte[] bytes) {
        char[] hexChars = new char[bytes.length * 2];
        for (int i = 0; i < bytes.length; i++) {
            int value = bytes[i] & 0xFF;
            hexChars[i * 2] = HEX_CHARS[value >>> 4];
            hexChars[i * 2 + 1] = HEX_CHARS[value & 0x0F];
        }
        return new String(hexChars);
    }

    /** Tính SHA-256 của hai mảng khóa nối tiếp, mỗi khóa là 12 byte ASCII. */
    public static String computeSHA256(String[] first, String[] second) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            updateDigest(digest, first);
            updateDigest(digest, second);
            return bytesToHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new RuntimeException("Hệ thống thiếu thuật toán SHA-256", exception);
        }
    }

    /** Đưa các khóa vào digest theo từng khối 8KB. */
    private static void updateDigest(MessageDigest digest, String[] keys) {
        if (keys == null || keys.length == 0) {
            return;
        }
        byte[] buffer = new byte[8192];
        int pointer = 0;
        for (int i = 0; i < keys.length; i++) {
            String key = keys[i];
            if (key == null || key.length() != KeyCodec.KEY_LENGTH) {
                throw new IllegalArgumentException("Mỗi khóa phải có đúng 12 ký tự");
            }
            for (int j = 0; j < key.length(); j++) {
                char value = key.charAt(j);
                if (KeyCodec.ALPHABET.indexOf(value) < 0) {
                    throw new IllegalArgumentException("Khóa chỉ được chứa ký tự 0-9, A-Z");
                }
                buffer[pointer++] = (byte) value;
                if (pointer == buffer.length) {
                    digest.update(buffer, 0, buffer.length);
                    pointer = 0;
                }
            }
        }
        if (pointer > 0) {
            digest.update(buffer, 0, pointer);
        }
    }

    private static void checkCount(int count) {
        if (count < 0) {
            throw new IllegalArgumentException("count phải >= 0");
        }
    }
}
