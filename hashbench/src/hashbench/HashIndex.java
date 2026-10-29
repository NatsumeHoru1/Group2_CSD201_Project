package hashbench;

/** Hop dong chung cho cac cai dat bang bam cua nhom. */
public interface HashIndex {
    int NOT_FOUND = Integer.MIN_VALUE;
    int INSERTED = 1, UPDATED = 0, FULL = -1;

    /** Them hoac cap nhat khoa String; key null va gia tri NOT_FOUND nem IllegalArgumentException. */
    int put(String key, int value);

    /** Tra ve gia tri cua khoa hoac NOT_FOUND neu khong co; key null nem IllegalArgumentException. */
    int get(String key);

    /** Xoa khoa va bao co xoa duoc hay khong; key null nem IllegalArgumentException. */
    boolean remove(String key);

    /** Tra ve so phan tu hien co. */
    int size();

    /** Tra ve dung luong M = 2^p. */
    int capacity();
}
