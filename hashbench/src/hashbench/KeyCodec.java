package hashbench;

/** Ma hoa ID so thanh khoa String gom 12 ky tu chu-so. */
public final class KeyCodec {
    public static final String ALPHABET = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    public static final int KEY_LENGTH = 12;
    public static final long SPACE = 4738381338321616896L;

    private KeyCodec() {
    }

    /** Ma hoa ID trong mien khoa thanh chuoi du 12 ky tu, dem so 0 ben trai. */
    public static String encode(long id) {
        if (id < 0L || id >= SPACE) {
            throw new IllegalArgumentException("id phai nam trong [0, " + SPACE + ")");
        }
        char[] key = new char[KEY_LENGTH];
        for (int i = KEY_LENGTH - 1; i >= 0; i--) {
            key[i] = ALPHABET.charAt((int) (id % 36L));
            id /= 36L;
        }
        return new String(key);
    }

    /** Giai ma khoa 12 ky tu chu-so thanh ID. */
    public static long decode(String key) {
        if (key == null || key.length() != KEY_LENGTH) {
            throw new IllegalArgumentException("key phai co dung 12 ky tu");
        }
        long id = 0L;
        for (int i = 0; i < key.length(); i++) {
            int digit = ALPHABET.indexOf(key.charAt(i));
            if (digit < 0) {
                throw new IllegalArgumentException("key chi duoc chua ky tu 0-9, A-Z");
            }
            id = id * 36L + digit;
        }
        return id;
    }
}
