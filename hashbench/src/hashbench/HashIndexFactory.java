package hashbench;

/** Tạo bảng băm theo thuật toán. Mỗi cặp thêm nhánh cho thuật toán của mình. */
public final class HashIndexFactory {
    private HashIndexFactory() {
    }

    public static HashIndex create(String algo, String variant, int p) {
        if ("rh".equals(algo)) {
            return new RobinHoodTable(p, !"noearly".equals(variant));
        }
        throw new IllegalArgumentException("Chua ho tro algorithm: " + algo);
    }
}