package hashbench;

import java.util.ArrayList;
import java.util.List;

/** Lưu cấu hình benchmark cùng các tùy chọn chỉ dùng cho test tiến trình. */
public final class RunConfig {
    public final String rq;
    public final String algo;
    public final String variant;
    public final String dataset;
    public final long n;
    public final int p;
    public final double alpha;
    public final int c;
    public final double tau;
    public final double load;
    public final long queries;
    public final long seed;
    public final int repeat;
    public final String xmx;
    public final String machineId;
    public final String params;
    public final int timeoutSec;
    public final boolean countMode;
    public final String sleepPhase;
    public final long sleepSeconds;
    public final String exitPhase;
    public final int exitCode;
    public final String oomPhase;

    /** Tạo cấu hình cơ bản với variant base và heap mặc định 2g. */
    public RunConfig(String rq, String algo, int p, double alpha, int c,
                     double tau, long seed, int repeat) {
        this(rq, algo, "base", "test", 0L, p, alpha, c, tau, 0.0, 0L,
                seed, repeat, "2g", "", 60, false);
    }

    /** Tạo cấu hình benchmark với toàn bộ trường có thể ghi vào CSV. */
    public RunConfig(String rq, String algo, String variant, String dataset, long n,
                     int p, double alpha, int c, double tau, double load, long queries,
                     long seed, int repeat, String xmx, String machineId,
                     int timeoutSec, boolean countMode) {
        this(rq, algo, variant, dataset, n, p, alpha, c, tau, load, queries, seed,
                repeat, xmx, machineId, timeoutSec, countMode, null, 0L, null, 1, null);
    }

    /** Khởi tạo và xác thực cấu hình; các hook cuối chỉ phục vụ kiểm thử tiến trình. */
    private RunConfig(String rq, String algo, String variant, String dataset, long n,
                      int p, double alpha, int c, double tau, double load, long queries,
                      long seed, int repeat, String xmx, String machineId,
                      int timeoutSec, boolean countMode, String sleepPhase,
                      long sleepSeconds, String exitPhase, int exitCode, String oomPhase) {
        if (rq == null || rq.length() == 0 || dataset == null || dataset.length() == 0) {
            throw new IllegalArgumentException("rq và dataset không được rỗng");
        }
        if (!"sc".equals(algo) && !"lp-tomb".equals(algo)
                && !"lp-backshift".equals(algo) && !"rh".equals(algo)) {
            throw new IllegalArgumentException("Algorithm không hợp lệ: " + algo);
        }
        if (!"base".equals(variant) && !"noearly".equals(variant)) {
            throw new IllegalArgumentException("Variant không hợp lệ: " + variant);
        }
        if ("noearly".equals(variant) && !"rh".equals(algo)) {
            throw new IllegalArgumentException("Variant noearly chỉ áp dụng cho rh");
        }
        if (p < 0 || p > 30 || n < 0L || queries < 0L || repeat < 1) {
            throw new IllegalArgumentException("p, n, queries hoặc repeat không hợp lệ");
        }
        if (!isFinite(alpha) || !isFinite(tau) || !isFinite(load)) {
            throw new IllegalArgumentException("alpha, tau và load phải hữu hạn");
        }
        if (xmx == null || !xmx.matches("[1-9][0-9]*[kKmMgG]?")) {
            throw new IllegalArgumentException("xmx phải có dạng số k/m/g hợp lệ");
        }
        if (timeoutSec <= 0) {
            throw new IllegalArgumentException("timeoutSec phải lớn hơn 0");
        }
        if (sleepPhase != null && (sleepSeconds < 0L
                || sleepSeconds > Long.MAX_VALUE / 1000L)) {
            throw new IllegalArgumentException("sleepSeconds không hợp lệ");
        }
        this.rq = rq;
        this.algo = algo;
        this.variant = variant;
        this.dataset = dataset;
        this.n = n;
        this.p = p;
        this.alpha = alpha;
        this.c = c;
        this.tau = tau;
        this.load = load;
        this.queries = queries;
        this.seed = seed;
        this.repeat = repeat;
        this.xmx = xmx;
        this.machineId = machineId == null ? "" : machineId;
        this.params = buildParams(p, alpha, c, tau, load, queries);
        this.timeoutSec = timeoutSec;
        this.countMode = countMode;
        this.sleepPhase = sleepPhase;
        this.sleepSeconds = sleepSeconds;
        this.exitPhase = exitPhase;
        this.exitCode = exitCode;
        this.oomPhase = oomPhase;
    }

    /** Tạo bản sao bật cờ count cho tiến trình con. */
    public RunConfig withCountMode(boolean enabled) {
        return copy(enabled, sleepPhase, sleepSeconds, exitPhase, exitCode, oomPhase);
    }

    /** Tạo bản sao với hook sleep chỉ dùng để kiểm tra timeout. */
    public RunConfig withSleepPhase(String phase, long seconds) {
        return copy(countMode, phase, seconds, exitPhase, exitCode, oomPhase);
    }

    /** Tạo bản sao với hook exit chỉ dùng để kiểm tra trạng thái crash. */
    public RunConfig withExitPhase(String phase, int code) {
        return copy(countMode, sleepPhase, sleepSeconds, phase, code, oomPhase);
    }

    /** Tạo bản sao với hook OutOfMemoryError chỉ dùng để kiểm tra trạng thái oom. */
    public RunConfig withOomPhase(String phase) {
        return copy(countMode, sleepPhase, sleepSeconds, exitPhase, exitCode, phase);
    }

    /** Sao chép các trường cấu hình trong khi thay đổi duy nhất các tùy chọn test. */
    private RunConfig copy(boolean newCountMode, String newSleepPhase, long newSleepSeconds,
            String newExitPhase, int newExitCode, String newOomPhase) {
        return new RunConfig(rq, algo, variant, dataset, n, p, alpha, c, tau, load,
                queries, seed, repeat, xmx, machineId, timeoutSec, newCountMode,
                newSleepPhase, newSleepSeconds, newExitPhase, newExitCode, newOomPhase);
    }

    /** Tính M = 2^p theo dung lượng bảng. */
    public int capacity() {
        return 1 << p;
    }

    /** Trả về danh sách pha giả theo đúng thứ tự của test runner hiện tại. */
    public static List<String> defaultPhases() {
        List<String> phases = new ArrayList<String>();
        phases.add("build");
        phases.add("present");
        phases.add("absent");
        phases.add("control");
        return phases;
    }

    /** Dựng chuỗi key=value ổn định, không phụ thuộc locale của máy. */
    private static String buildParams(int p, double alpha, int c, double tau,
            double load, long queries) {
        return "p=" + p + ";alpha=" + Double.toString(alpha)
                + ";c=" + c + ";tau=" + Double.toString(tau)
                + ";load=" + Double.toString(load) + ";queries=" + queries;
    }

    /** Kiểm tra số double hữu hạn theo API có sẵn trong Java 8. */
    private static boolean isFinite(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value);
    }
}
