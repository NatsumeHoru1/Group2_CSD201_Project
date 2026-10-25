package hashbench;

/**
 * Tạo bảng băm theo thuật toán. Runner / phần đo nên gọi:
 *
 *     HashIndex index = HashIndexFactory.create(config.algo, config.variant, config.p);
 *
 * Hiện hỗ trợ:
 *   algo = "rh", variant = "base"    -> Robin Hood đầy đủ (có dừng sớm)
 *   algo = "rh", variant = "noearly" -> Robin Hood tắt dừng sớm (rh-noearly)
 *   algo = "lp-backshift", variant = "base" -> Dò mở tuyến tính, xóa dịch lùi
 *     theo Algorithm R của Knuth (TV6)
 *
 * Ghi vào CSV: rh-noearly là algorithm=rh, variant=noearly (không dùng algorithm="rh-noearly").
 * Thuật toán chưa hỗ trợ thì ném IllegalArgumentException; mỗi cặp tự thêm nhánh `if` cho
 * thuật toán của mình. Thuật toán cần tham số riêng (ví dụ tau của lp-tomb) thì đổi chữ ký
 * hàm này cho phù hợp.
 *
 * Bảng rh còn implement HashStats: dùng ((HashStats) index) để đọc dibMean(), dibMax()
 * (chỉ gọi ngoài vùng đo). Số lần dò đọc từ Counters.probeCount ở lượt --count.
 *
 * Người thêm: TV5.
 */
public final class HashIndexFactory {
    private HashIndexFactory() {
    }

    public static HashIndex create(String algo, String variant, int p) {
        if ("rh".equals(algo)) {
            return new RobinHoodTable(p, !"noearly".equals(variant));
        }
        if ("lp-backshift".equals(algo)) {
            return new LinearProbingBackshiftTable(p);
        }
        throw new IllegalArgumentException("Chua ho tro algorithm: " + algo);
    }
}