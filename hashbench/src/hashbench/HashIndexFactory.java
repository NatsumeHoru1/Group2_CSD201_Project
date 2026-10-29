package hashbench;

/**
 * Tao bang bam theo thuat toan. Runner / phan do nen goi:
 *
 *     HashIndex index = HashIndexFactory.create(config.algo, config.variant, config.p);
 *
 * Hien ho tro:
 *   algo = "rh", variant = "base"    -> Robin Hood day du (co dung som)
 *   algo = "rh", variant = "noearly" -> Robin Hood tat dung som (rh-noearly)
 *   algo = "lp-tomb", variant = "base" -> Linear probing + tombstone (TV3); tau chi dung cho
 *                                         lp-tomb: rebuild khi ti le TOMB > tau.
 *   algo = "lp-backshift", variant = "base" -> Do mo tuyen tinh, xoa dich lui
 *     theo Algorithm R cua Knuth (TV6)
 *   algo = "sc", variant = "base" -> Separate chaining
 *
 * Ghi vao CSV: rh-noearly la algorithm=rh, variant=noearly (khong dung algorithm="rh-noearly").
 * Thuat toan chua ho tro thi nem IllegalArgumentException; moi cap tu them nhanh `if` cho
 * thuat toan cua minh. Ham 3 tham so duoc giu lai de khong vo build cua nguoi khac; no goi
 * ham 4 tham so voi tau = vo han (khong bao gio rebuild). Overload 4 tham so chi
 * ap dung tau cho lp-tomb; cac thuat toan khac bo qua.
 *
 * Bang rh con implement HashStats: dung ((HashStats) index) de doc dibMean(), dibMax()
 * (chi goi ngoai vung do). So lan do doc tu Counters.probeCount o luot --count.
 *
 * Nguoi them: TV5 (rh), TV3 (lp-tomb va ham co tau), TV6 (lp-backshift).
 */
public final class HashIndexFactory {
    private HashIndexFactory() {
    }

    /** Giu chu ky cu de khong vo build cua nguoi khac; tau = vo han (khong rebuild). */
    public static HashIndex create(String algo, String variant, int p) {
        return create(algo, variant, p, Double.POSITIVE_INFINITY);
    }

    /** tau chi dung cho lp-tomb: rebuild khi ti le TOMB > tau. Thuat toan khac bo qua tau. */
    public static HashIndex create(String algo, String variant, int p, double tau) {
        if ("rh".equals(algo)) {
            return new RobinHoodTable(p, !"noearly".equals(variant));
        }
        if ("lp-tomb".equals(algo)) {
            return new LinearProbingTombTable(p, tau);
        }
        if ("lp-backshift".equals(algo)) {
            return new LinearProbingBackshiftTable(p);
        }
        if ("sc".equals(algo)) {
            return new SeparateChainingTable(p);
        }
        throw new IllegalArgumentException("Chua ho tro algorithm: " + algo);
    }
}