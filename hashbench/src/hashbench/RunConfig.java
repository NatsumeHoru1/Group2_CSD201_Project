package hashbench;

/** Lưu cấu hình của một lượt chạy benchmark. */
public final class RunConfig {
    public final String rq;
    public final String algo;
    public final int p;
    public final double alpha;
    public final int c;
    public final double tau;
    public final long seed;
    public final int rep;

    /** Tạo cấu hình với các trường theo tên tạm lấy từ yêu cầu người dùng. */
    public RunConfig(String rq, String algo, int p, double alpha, int c,
                     double tau, long seed, int rep) {
        if (rq == null || algo == null) {
            throw new IllegalArgumentException("rq và algo không được null");
        }
        if (p < 0 || p > 30) {
            throw new IllegalArgumentException("p phải nằm trong khoảng 0..30");
        }
        this.rq = rq;
        this.algo = algo;
        this.p = p;
        this.alpha = alpha;
        this.c = c;
        this.tau = tau;
        this.seed = seed;
        this.rep = rep;
    }

    /** Tính M = 2^p theo dung lượng bảng. */
    public int capacity() {
        return 1 << p;
    }
}
