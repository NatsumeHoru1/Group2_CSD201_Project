package hashbench;

/**
 * Tạo bảng băm theo thuật toán. Runner / phần đo nên gọi:
 *
 *     HashIndex index = HashIndexFactory.create(config.algo, config.variant, config.p);
 *
 * Hiện hỗ trợ:
 *   algo = "rh", variant = "base"    -> Robin Hood đầy đủ (có dừng sớm)
 *   algo = "rh", variant = "noearly" -> Robin Hood tắt dừng sớm (rh-noearly)
 *   algo = "lp-tomb", variant = "base" -> Linear probing + tombstone (TV3); tau chỉ dùng cho
 *                                         lp-tomb: rebuild khi tỉ lệ TOMB > tau.
 *
 * Ghi vào CSV: rh-noearly là algorithm=rh, variant=noearly (không dùng algorithm="rh-noearly").
 * Thuật toán chưa hỗ trợ thì ném IllegalArgumentException; mỗi cặp tự thêm nhánh `if` cho
 * thuật toán của mình. Hàm 3 tham số được giữ lại để không vỡ build của người khác; nó gọi
 * hàm 4 tham số với tau = vô hạn (không bao giờ rebuild).
 *
 * Bảng rh còn implement HashStats: dùng ((HashStats) index) để đọc dibMean(), dibMax()
 * (chỉ gọi ngoài vùng đo). Số lần dò đọc từ Counters.probeCount ở lượt --count.
 *
 * Người thêm: TV5 (rh), TV3 (lp-tomb và hàm có tau).
 */
public final class HashIndexFactory {
    private HashIndexFactory() {
    }

    /** Giữ chữ ký cũ để không vỡ build của người khác; tau = vô hạn (không rebuild). */
    public static HashIndex create(String algo, String variant, int p) {
        return create(algo, variant, p, Double.POSITIVE_INFINITY);
    }

    /** tau chỉ dùng cho lp-tomb: rebuild khi tỉ lệ TOMB > tau. Thuật toán khác bỏ qua tau. */
    public static HashIndex create(String algo, String variant, int p, double tau) {
        if ("rh".equals(algo)) {
            return new RobinHoodTable(p, !"noearly".equals(variant));
        }
        if ("lp-tomb".equals(algo)) {
            return new LinearProbingTombTable(p, tau);
        }
        throw new IllegalArgumentException("Chua ho tro algorithm: " + algo);
    }
}