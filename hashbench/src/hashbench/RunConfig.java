package hashbench;

import java.util.ArrayList;
import java.util.List;

/** Luu cau hinh benchmark cung cac tuy chon chi dung cho test tien trinh. */
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

    /** Tao cau hinh test cu voi variant base va heap mac dinh 2g. */
    public RunConfig(String rq, String algo, int p, double alpha, int c,
                     double tau, long seed, int repeat) {
        this(rq, algo, "base", "test", 0L, p, alpha, c, tau, 0.0, 0L,
                seed, repeat, "2g", "", 60, false);
    }

    /** Tao cau hinh benchmark voi toan bo truong co the ghi vao CSV. */
    public RunConfig(String rq, String algo, String variant, String dataset, long n,
                     int p, double alpha, int c, double tau, double load, long queries,
                     long seed, int repeat, String xmx, String machineId,
                     int timeoutSec, boolean countMode) {
        this(rq, algo, variant, dataset, n, p, alpha, c, tau, load, queries, seed,
                repeat, xmx, machineId, timeoutSec, countMode, null);
    }

    /** Tao cau hinh benchmark voi danh sach pha tuy chon. */
    public RunConfig(String rq, String algo, String variant, String dataset, long n,
                     int p, double alpha, int c, double tau, double load, long queries,
                     long seed, int repeat, String xmx, String machineId,
                     int timeoutSec, boolean countMode, String phaseNames) {
        this(rq, algo, variant, dataset, n, p, alpha, c, tau, load, queries, seed,
                repeat, xmx, machineId, timeoutSec, countMode, null, 0L, null, 1, null,
                phaseNames, false);
    }

    /** Khoi tao va xac thuc cau hinh; cac hook cuoi chi phuc vu kiem thu tien trinh. */
    private RunConfig(String rq, String algo, String variant, String dataset, long n,
                      int p, double alpha, int c, double tau, double load, long queries,
                      long seed, int repeat, String xmx, String machineId,
                      int timeoutSec, boolean countMode, String sleepPhase,
                      long sleepSeconds, String exitPhase, int exitCode, String oomPhase,
                      String phaseNames, boolean profile) {
        if (rq == null || rq.length() == 0 || dataset == null || dataset.length() == 0) {
            throw new IllegalArgumentException("rq va dataset khong duoc rong");
        }
        if (!"sc".equals(algo) && !"lp-tomb".equals(algo)
                && !"lp-backshift".equals(algo) && !"rh".equals(algo)) {
            throw new IllegalArgumentException("Algorithm khong hop le: " + algo);
        }
        if (!"base".equals(variant) && !"noearly".equals(variant)) {
            throw new IllegalArgumentException("Variant khong hop le: " + variant);
        }
        if ("noearly".equals(variant) && !"rh".equals(algo)) {
            throw new IllegalArgumentException("Variant noearly chi ap dung cho rh");
        }
        if (("rq2".equals(dataset) || "rq3".equals(dataset))
                && (p < 1 || !isFinite(load) || load <= 0.0 || load > 1.0)) {
            throw new IllegalArgumentException("rq2/rq3 yeu cau p trong [1,30] va load trong (0,1]");
        }
        if (p < 0 || p > 30 || n < 0L || c < 0 || queries < 0L || repeat < 1) {
            throw new IllegalArgumentException("p, n, queries hoac repeat khong hop le");
        }
        if (!isFinite(alpha) || !isFinite(load) || Double.isNaN(tau) || tau < 0.0) {
            throw new IllegalArgumentException(
                    "alpha va load phai huu han; tau phai khong am va khong phai NaN");
        }
        if (xmx == null || !xmx.matches("[1-9][0-9]*[kKmMgG]?")) {
            throw new IllegalArgumentException("xmx phai co dang so k/m/g hop le");
        }
        if (timeoutSec <= 0) {
            throw new IllegalArgumentException("timeoutSec phai lon hon 0");
        }
        if (sleepPhase != null && (sleepSeconds < 0L
                || sleepSeconds > Long.MAX_VALUE / 1000L)) {
            throw new IllegalArgumentException("sleepSeconds khong hop le");
        }
        this.rq = rq;
        this.algo = algo;
        this.variant = variant;
        this.dataset = dataset;
        this.n = "rq1a".equals(dataset) || "rq1b".equals(dataset) || "rq1clust".equals(dataset)
                ? Math.round(alpha * (1L << p))
                : ("rq2".equals(dataset) || "rq3".equals(dataset)
                        ? Math.round(load * (1L << p)) : n);
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

    /** Tao ban sao bat co count cho tien trinh con. */
    public RunConfig withCountMode(boolean enabled) {
        return copy(enabled, sleepPhase, sleepSeconds, exitPhase, exitCode, oomPhase, profile);
    }

    /** Tao ban sao bat co profile cho tien trinh con. */
    public RunConfig withProfile(boolean profile) {
        return copy(countMode, sleepPhase, sleepSeconds, exitPhase, exitCode, oomPhase, profile);
    }

    /** Tao ban sao voi hook sleep chi dung de kiem tra timeout. */
    public RunConfig withSleepPhase(String phase, long seconds) {
        return copy(countMode, phase, seconds, exitPhase, exitCode, oomPhase, profile);
    }

    /** Tao ban sao voi hook exit chi dung de kiem tra trang thai crash. */
    public RunConfig withExitPhase(String phase, int code) {
        return copy(countMode, sleepPhase, sleepSeconds, phase, code, oomPhase, profile);
    }

    /** Tao ban sao voi hook OutOfMemoryError chi dung de kiem tra trang thai oom. */
    public RunConfig withOomPhase(String phase) {
        return copy(countMode, sleepPhase, sleepSeconds, exitPhase, exitCode, phase, profile);
    }

    /** Sao chep cac truong cau hinh trong khi thay doi duy nhat cac tuy chon test. */
    private RunConfig copy(boolean newCountMode, String newSleepPhase, long newSleepSeconds,
            String newExitPhase, int newExitCode, String newOomPhase, boolean newProfile) {
        return new RunConfig(rq, algo, variant, dataset, n, p, alpha, c, tau, load,
                queries, seed, repeat, xmx, machineId, timeoutSec, newCountMode,
                newSleepPhase, newSleepSeconds, newExitPhase, newExitCode, newOomPhase,
                phaseNames, newProfile);
    }

    /** Tinh M = 2^p theo dung luong bang. */
    public int capacity() {
        return 1 << p;
    }

    /** Tra ve danh sach pha gia theo dung thu tu cua test runner hien tai. */
    public static List<String> defaultPhases() {
        List<String> phases = new ArrayList<String>();
        phases.add("build");
        phases.add("present");
        phases.add("absent");
        phases.add("control");
        return phases;
    }

    /** Chon pha chay theo dataset; dataset test giu control de tuong thich test stub. */
    public static List<String> phasesForDataset(String dataset) {
        return phasesForDataset(dataset, null);
    }

    /** Tra ve danh sach pha do cau hinh chi dinh hoac mac dinh theo dataset. */
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
        } else if ("rq2".equals(dataset)) {
            if (configuredPhases == null) {
                phases.add("build");
                phases.add("workload");
                phases.add("present");
                phases.add("absent");
                phases.add("ctl_present");
                phases.add("ctl_absent");
            } else {
                addConfiguredPhases(phases, configuredPhases, true, true);
            }
        } else if ("rq3".equals(dataset)) {
            if (configuredPhases == null) {
                phases.add("build");
                phases.add("workload");
                phases.add("present");
                phases.add("absent");
            } else {
                addConfiguredPhases(phases, configuredPhases, false, true);
            }
        } else {
            throw new IllegalArgumentException("Dataset chua ho tro: " + dataset);
        }
        return phases;
    }

    /** Tach va kiem tra pha CLI de khong nhan pha khong trien khai. */
    private static void addConfiguredPhases(List<String> phases, String configuredPhases,
            boolean allowControl) {
        addConfiguredPhases(phases, configuredPhases, allowControl, false);
    }

    private static void addConfiguredPhases(List<String> phases, String configuredPhases,
            boolean allowControl, boolean allowWorkloadPhases) {
        String[] names = configuredPhases.split(",", -1);
        for (String name : names) {
            if (!"build".equals(name) && !"present".equals(name) && !"absent".equals(name)
                    && !(allowControl && "control".equals(name))
                    && !(allowWorkloadPhases && "workload".equals(name))
                    && !(allowControl && ("ctl_present".equals(name)
                            || "ctl_absent".equals(name)))) {
                throw new IllegalArgumentException("Pha chua ho tro: " + name);
            }
            phases.add(name);
        }
        if (phases.isEmpty()) {
            throw new IllegalArgumentException("Danh sach pha khong duoc rong");
        }
    }

    /** Dung chuoi key=value on dinh, khong phu thuoc locale cua may. */
    private static String buildParams(int p, double alpha, int c, double tau,
            double load, long queries) {
        return "p=" + p + ";alpha=" + Double.toString(alpha)
                + ";c=" + c + ";tau=" + (Double.isInfinite(tau) ? "inf"
                        : Double.toString(tau))
                + ";load=" + Double.toString(load) + ";queries=" + queries;
    }

    /** Kiem tra so double huu han theo API co san trong Java 8. */
    private static boolean isFinite(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value);
    }

    /** Sinh proc_id tat dinh, chi chua [A-Za-z0-9_-]. */
    public String procId() {
        StringBuilder sb = new StringBuilder();
        sb.append(rq).append('_').append(algo).append('_').append(variant).append('_').append(dataset);
        sb.append("_p").append(p);
        sb.append("_a").append(formatDoubleNoDot(alpha));
        sb.append("_t").append(formatDoubleNoDot(tau));
        if ("rq2".equals(dataset) || "rq3".equals(dataset)) {
            sb.append("_l").append(formatDoubleNoDot(load));
            sb.append("_c").append(c);
        }
        sb.append("_s").append(seed);
        sb.append("_r").append(repeat);
        if (countMode) {
            sb.append("-count");
        }
        return sb.toString();
    }

    /** Dinh dang double khong dau cham: 0.75 -> 0p75, inf -> inf. */
    public static String formatDoubleNoDot(double v) {
        if (Double.isInfinite(v)) {
            return "inf";
        }
        String s = Double.toString(v);
        return s.replace('.', 'p');
    }
}
