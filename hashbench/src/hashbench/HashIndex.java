package hashbench;

/** Hợp đồng chung cho các cài đặt bảng băm của nhóm. */
public interface HashIndex {
    int NOT_FOUND = Integer.MIN_VALUE;
    int INSERTED = 1, UPDATED = 0, FULL = -1;

    /** Thêm hoặc cập nhật khóa String; key null và giá trị NOT_FOUND ném IllegalArgumentException. */
    int put(String key, int value);

    /** Trả về giá trị của khóa hoặc NOT_FOUND nếu không có; key null ném IllegalArgumentException. */
    int get(String key);

    /** Xóa khóa và báo có xóa được hay không; key null ném IllegalArgumentException. */
    boolean remove(String key);

    /** Trả về số phần tử hiện có. */
    int size();

    /** Trả về dung lượng M = 2^p. */
    int capacity();
}
