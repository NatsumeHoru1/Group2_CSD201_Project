package hashbench;

/** Mã hóa ID số thành khóa String gồm 12 ký tự chữ-số. */
public final class KeyCodec {
    public static final String ALPHABET = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    public static final int KEY_LENGTH = 12;
    public static final long SPACE = 4738381338321616896L;

    private KeyCodec() {
    }

    /** Mã hóa ID trong miền khóa thành chuỗi đủ 12 ký tự, đệm số 0 bên trái. */
    public static String encode(long id) {
        if (id < 0L || id >= SPACE) {
            throw new IllegalArgumentException("id phải nằm trong [0, " + SPACE + ")");
        }
        char[] key = new char[KEY_LENGTH];
        for (int i = KEY_LENGTH - 1; i >= 0; i--) {
            key[i] = ALPHABET.charAt((int) (id % 36L));
            id /= 36L;
        }
        return new String(key);
    }

    /** Giải mã khóa 12 ký tự chữ-số thành ID. */
    public static long decode(String key) {
        if (key == null || key.length() != KEY_LENGTH) {
            throw new IllegalArgumentException("key phải có đúng 12 ký tự");
        }
        long id = 0L;
        for (int i = 0; i < key.length(); i++) {
            int digit = ALPHABET.indexOf(key.charAt(i));
            if (digit < 0) {
                throw new IllegalArgumentException("key chỉ được chứa ký tự 0-9, A-Z");
            }
            id = id * 36L + digit;
        }
        return id;
    }
}
