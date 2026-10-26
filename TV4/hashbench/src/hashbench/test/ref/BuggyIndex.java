package hashbench.test.ref;

/** Bản cố ý SAI để chứng minh harness bắt được lỗi (kiểm tra đột biến). */
public final class BuggyIndex extends ReferenceIndex {
    public enum Mode { ACCEPT_MIN_VALUE, NO_FULL_LIMIT, WRONG_UPDATE_CODE, REMOVE_LOSES_NEIGHBOR }
    private final Mode mode;

    public BuggyIndex(int m, Mode mode) { super(m); this.mode = mode; }

    @Override public int put(long key, int value) {
        switch (mode) {
            case ACCEPT_MIN_VALUE:
                if (value == NOT_FOUND) { map.put(key, value); return INSERTED; }
                return super.put(key, value);
            case NO_FULL_LIMIT:
                if (!map.containsKey(key)) { map.put(key, value); return INSERTED; }
                map.put(key, value); return UPDATED;
            case WRONG_UPDATE_CODE:
                int r = super.put(key, value);
                return r == UPDATED ? INSERTED : r;
            default:
                return super.put(key, value);
        }
    }
    @Override public boolean remove(long key) {
        boolean r = super.remove(key);
        if (mode == Mode.REMOVE_LOSES_NEIGHBOR) map.remove(key + 1);
        return r;
    }
}
