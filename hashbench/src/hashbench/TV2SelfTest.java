package hashbench;

import java.util.SplittableRandom;

/**
 * Kiểm thử nghiệm thu các module do TV2 phụ trách (Hashing, LongSet, Generator).
 */
public final class TV2SelfTest {
    public static void main(String[] args) {
        System.out.println("=== KIỂM THỬ MODULE TV2 (Hashing, LongSet, Generator) ===");

        // 1. Kiểm tra hằng số Fibonacci
        boolean fOk = Hashing.verifyConstant();
        System.out.println("1. Hằng số Fibonacci 64-bit hợp lệ: " + fOk + " (" + Hashing.EXPECTED_UNSIGNED_STR + ")");
        if (!fOk) throw new AssertionError("Sai hằng số Fibonacci!");

        // 2. Kiểm tra LongSet
        LongSet set = new LongSet(100);
        if (!set.add(0L)) throw new AssertionError("Không thêm được khóa 0L");
        if (set.add(0L)) throw new AssertionError("Không phát hiện khóa trùng 0L");
        if (!set.contains(0L)) throw new AssertionError("contains(0L) phải trả về true");
        if (!set.add(100L)) throw new AssertionError("Không thêm được khóa 100L");
        if (!set.contains(100L)) throw new AssertionError("contains(100L) phải trả về true");
        if (set.contains(999L)) throw new AssertionError("contains(999L) phải trả về false");
        System.out.println("2. LongSet hoạt động đúng (gồm cả khóa 0L, zero-boxing): OK");

        // 3. Kiểm tra findKeyWithHome
        int p = 10; // M = 1024
        int targetHome = 42;
        SplittableRandom rng = new SplittableRandom(12345);
        long keyForHome = Hashing.findKeyWithHome(targetHome, p, rng);
        if (Hashing.hash(keyForHome, p) != targetHome) {
            throw new AssertionError("Lỗi: Key tìm được không khớp targetHome");
        }
        System.out.println("3. Tìm khóa có home cố định (" + targetHome + "): OK -> key = " + keyForHome);

        // 4. Kiểm tra Generator và SHA-256
        int count = 10000;
        long seed = 42L;
        long[] present = Generator.generatePresentKeys(seed, count);
        if (present.length != count) throw new AssertionError("Sai số lượng khóa");
        String sha = Generator.computeSHA256(present);
        System.out.println("4. Sinh " + count + " khóa và tính SHA-256 từng khối: OK");
        System.out.println("   SHA-256 = " + sha);

        // 5. Kiểm tra kết nối với RobinHoodTable
        RobinHoodTable rht = new RobinHoodTable(p);
        for (int i = 0; i < 100; i++) {
            rht.put(present[i], i + 1);
        }
        if (rht.get(present[0]) != 1) throw new AssertionError("Lỗi RobinHoodTable get");
        System.out.println("5. Tích hợp thử với RobinHoodTable: OK");

        System.out.println("\n>>> TẤT CẢ TEST CỦA TV2 ĐỀU ĐẠT CHUẨN! <<<");
    }
}
