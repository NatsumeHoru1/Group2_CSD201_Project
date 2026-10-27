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
    public final String phaseNames;
    public final boolean profile;

    /** Tạo cấu hình test cũ với variant base và heap mặc định 2g. */
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
                repeat, xmx, machineId, timeoutSec, countMode, null);
    }

    /** Tạo cấu hình benchmark với danh sách pha tùy chọn. */
    public RunConfig(String rq, String algo, String variant, String dataset, long n,
                     int p, double alpha, int c, double tau, double load, long queries,
                     long seed, int repeat, String xmx, String machineId,
                     int timeoutSec, boolean countMode, String phaseNames) {
        this(rq, algo, variant, dataset, n, p, alpha, c, tau, load, queries, seed,
                repeat, xmx, machineId, timeoutSec, countMode, null, 0L, null, 1, null,
                phaseNames, false);
    }

    /** Khởi tạo và xác thực cấu hình; các hook cuối chỉ phục vụ kiểm thử tiến trình. */
    private RunConfig(String rq, String algo, String variant, String dataset, long n,
                      int p, double alpha, int c, double tau, double load, long queries,
                      long seed, int repeat, String xmx, String machineId,
                      int timeoutSec, boolean countMode, String sleepPhase,
                      long sleepSeconds, String exitPhase, int exitCode, String oomPhase,
                      String phaseNames, boolean profile) {
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
        if (!isFinite(alpha) || !isFinite(load) || Double.isNaN(tau) || tau < 0.0) {
            throw new IllegalArgumentException(
                    "alpha và load phải hữu hạn; tau phải không âm và không phải NaN");
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
        this.n = "rq1a".equals(dataset) || "rq1b".equals(dataset) || "rq1clust".equals(dataset)
                ? Math.round(alpha * (1L << p)) : n;
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
        this.phaseNames = phaseNames == null || phaseNames.length() == 0
                ? null : phaseNames;
        this.profile = profile;
    }

    /** Tạo bản sao bật cờ count cho tiến trình con. */
    public RunConfig withCountMode(boolean enabled) {
        return copy(enabled, sleepPhase, sleepSeconds, exitPhase, exitCode, oomPhase, profile);
    }

    /** Tạo bản sao bật cờ profile cho tiến trình con. */
    public RunConfig withProfile(boolean profile) {
        return copy(countMode, sleepPhase, sleepSeconds, exitPhase, exitCode, oomPhase, profile);
    }

    /** Tạo bản sao với hook sleep chỉ dùng để kiểm tra timeout. */
    public RunConfig withSleepPhase(String phase, long seconds) {
        return copy(countMode, phase, seconds, exitPhase, exitCode, oomPhase, profile);
    }

    /** Tạo bản sao với hook exit chỉ dùng để kiểm tra trạng thái crash. */
    public RunConfig withExitPhase(String phase, int code) {
        return copy(countMode, sleepPhase, sleepSeconds, phase, code, oomPhase, profile);
    }

    /** Tạo bản sao với hook OutOfMemoryError chỉ dùng để kiểm tra trạng thái oom. */
    public RunConfig withOomPhase(String phase) {
        return copy(countMode, sleepPhase, sleepSeconds, exitPhase, exitCode, phase, profile);
    }

    /** Sao chép các trường cấu hình trong khi thay đổi duy nhất các tùy chọn test. */
    private RunConfig copy(boolean newCountMode, String newSleepPhase, long newSleepSeconds,
            String newExitPhase, int newExitCode, String newOomPhase, boolean newProfile) {
        return new RunConfig(rq, algo, variant, dataset, n, p, alpha, c, tau, load,
                queries, seed, repeat, xmx, machineId, timeoutSec, newCountMode,
                newSleepPhase, newSleepSeconds, newExitPhase, newExitCode, newOomPhase,
                phaseNames, newProfile);
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

    /** Chọn pha chạy theo dataset; dataset test giữ control để tương thích test stub. */
    public static List<String> phasesForDataset(String dataset) {
        return phasesForDataset(dataset, null);
    }

    /** Trả về danh sách pha do cấu hình chỉ định hoặc mặc định theo dataset. */
    public static List<String> phasesForDataset(String dataset, String configuredPhases) {
        List<String> phases = new ArrayList<String>();
        if ("rq1a".equals(dataset) || "rq1b".equals(dataset) || "rq1clust".equals(dataset)) {
            if (configuredPhases == null) {
                phases.add("build");
                phases.add("present");
                phases.add("absent");
            } else {
                addConfiguredPhases(phases, configuredPhases, false);
            }
        } else if ("test".equals(dataset)) {
            if (configuredPhases == null) {
                phases.addAll(defaultPhases());
            } else {
                addConfiguredPhases(phases, configuredPhases, true);
            }
        } else {
            throw new IllegalArgumentException("Dataset chưa hỗ trợ: " + dataset);
        }
        return phases;
    }

    /** Tách và kiểm tra pha CLI để không nhận pha không triển khai. */
    private static void addConfiguredPhases(List<String> phases, String configuredPhases,
            boolean allowControl) {
        String[] names = configuredPhases.split(",", -1);
        for (String name : names) {
            if (!"build".equals(name) && !"present".equals(name) && !"absent".equals(name)
                    && !(allowControl && "control".equals(name))) {
                throw new IllegalArgumentException("Pha chưa hỗ trợ: " + name);
            }
            phases.add(name);
        }
        if (phases.isEmpty()) {
            throw new IllegalArgumentException("Danh sách pha không được rỗng");
        }
    }

    /** Dựng chuỗi key=value ổn định, không phụ thuộc locale của máy. */
    private static String buildParams(int p, double alpha, int c, double tau,
            double load, long queries) {
        return "p=" + p + ";alpha=" + Double.toString(alpha)
                + ";c=" + c + ";tau=" + (Double.isInfinite(tau) ? "inf"
                        : Double.toString(tau))
                + ";load=" + Double.toString(load) + ";queries=" + queries;
    }

    /** Kiểm tra số double hữu hạn theo API có sẵn trong Java 8. */
    private static boolean isFinite(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value);
    }

    /** Sinh proc_id tất định, chỉ chứa [A-Za-z0-9_-]. */
    public String procId() {
        StringBuilder sb = new StringBuilder();
        sb.append(rq).append('_').append(algo).append('_').append(variant).append('_').append(dataset);
        sb.append("_p").append(p);
        sb.append("_a").append(formatDoubleNoDot(alpha));
        sb.append("_t").append(formatDoubleNoDot(tau));
        sb.append("_s").append(seed);
        sb.append("_r").append(repeat);
        if (countMode) {
            sb.append("-count");
        }
        return sb.toString();
    }

    /** Định dạng double không dấu chấm: 0.75 -> 0p75, inf -> inf. */
    public static String formatDoubleNoDot(double v) {
        if (Double.isInfinite(v)) {
            return "inf";
        }
        String s = Double.toString(v);
        return s.replace('.', 'p');
    }
}
