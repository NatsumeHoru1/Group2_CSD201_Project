package hashbench;

/** Hợp đồng chung cho các cài đặt bảng băm của nhóm. */
public interface HashIndex {
    int NOT_FOUND = Integer.MIN_VALUE;
    int INSERTED = 1, UPDATED = 0, FULL = -1;

    /** Thêm hoặc cập nhật khóa; từ chối giá trị NOT_FOUND. */
    int put(long key, int value);

    /** Trả về giá trị của khóa hoặc NOT_FOUND nếu không có. */
    int get(long key);

    /** Xóa khóa và báo có xóa được hay không. */
    boolean remove(long key);

    /** Trả về số phần tử hiện có. */
    int size();

    /** Trả về dung lượng M = 2^p. */
    int capacity();
}
