package hashbench;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.SplittableRandom;

/**
 * Bo sinh khoa String va tinh SHA-256 theo thu tu khoa.
 * Phu trach: TV2
 */
public final class Generator {
    public static final long[] FIXED_SEEDS = { 1L, 2L, 3L };

    private static final char[] HEX_CHARS = "0123456789abcdef".toCharArray();

    private Generator() {
    }

    /** Sinh ID khoa co mat, duy nhat trong mien cua KeyCodec. */
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

    /** Chuyen mang ID thanh khoa String du 12 ky tu. */
    public static String[] idsToKeys(long[] ids) {
        if (ids == null) {
            throw new IllegalArgumentException("ids khong duoc null");
        }
        String[] keys = new String[ids.length];
        for (int i = 0; i < ids.length; i++) {
            keys[i] = KeyCodec.encode(ids[i]);
        }
        return keys;
    }

    /** Sinh khoa co mat duy nhat va ma hoa chung thanh String. */
    public static String[] generatePresentKeys(long seed, int count) {
        return idsToKeys(generatePresentIds(seed, count));
    }

    /** Sinh ID vang mat, khong trung ID nao trong tap co mat. */
    public static long[] generateAbsentIds(long seed, int count, long[] presentIds) {
        checkCount(count);
        if (presentIds == null) {
            throw new IllegalArgumentException("presentIds khong duoc null");
        }
        LongSet presentSet = new LongSet(presentIds.length);
        for (int i = 0; i < presentIds.length; i++) {
            if (!presentSet.add(presentIds[i])) {
                throw new IllegalArgumentException("presentIds chua ID trung lap");
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

    /** Sinh khoa vang mat, khong giao voi tap khoa co mat. */
    public static String[] generateAbsentKeys(long seed, int count, long[] presentIds) {
        return idsToKeys(generateAbsentIds(seed, count, presentIds));
    }

    /** Sinh khoa don cum voi it nhat 10% home nho hon max(1, M/64). */
    public static String[] generateClusteredKeys(long seed, int count, int p) {
        checkCount(count);
        if (p < 1 || p > 30) {
            throw new IllegalArgumentException("p phai nam trong khoang 1..30");
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

    /** Chuyen mang byte sang chuoi Hex bang tra cuu ky tu. */
    public static String bytesToHex(byte[] bytes) {
        char[] hexChars = new char[bytes.length * 2];
        for (int i = 0; i < bytes.length; i++) {
            int value = bytes[i] & 0xFF;
            hexChars[i * 2] = HEX_CHARS[value >>> 4];
            hexChars[i * 2 + 1] = HEX_CHARS[value & 0x0F];
        }
        return new String(hexChars);
    }

    /** Tinh SHA-256 cua hai mang khoa noi tiep, moi khoa la 12 byte ASCII. */
    public static String computeSHA256(String[] first, String[] second) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            updateDigest(digest, first);
            updateDigest(digest, second);
            return bytesToHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new RuntimeException("He thong thieu thuat toan SHA-256", exception);
        }
    }

    /** Dua cac khoa vao digest theo tung khoi 8KB. */
    private static void updateDigest(MessageDigest digest, String[] keys) {
        if (keys == null || keys.length == 0) {
            return;
        }
        byte[] buffer = new byte[8192];
        int pointer = 0;
        for (int i = 0; i < keys.length; i++) {
            String key = keys[i];
            if (key == null || key.length() != KeyCodec.KEY_LENGTH) {
                throw new IllegalArgumentException("Moi khoa phai co dung 12 ky tu");
            }
            for (int j = 0; j < key.length(); j++) {
                char value = key.charAt(j);
                if (!((value >= '0' && value <= '9') || (value >= 'A' && value <= 'Z'))) {
                    throw new IllegalArgumentException("Khoa chi duoc chua ky tu 0-9, A-Z");
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
            throw new IllegalArgumentException("count phai >= 0");
        }
    }
}
