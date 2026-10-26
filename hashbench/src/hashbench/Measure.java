package hashbench;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryType;
import java.lang.management.MemoryUsage;
import java.util.SplittableRandom;

/** Tạo dữ liệu và đo các pha build, present, absent trong tiến trình con. */
public final class Measure {
    public static final String GENERATOR_VERSION = "1.0";

    private Measure() {
    }

    /** Sinh dữ liệu đầu vào và checksum ngoài vùng đo thời gian. */
    public static Data buildData(long seed, int p, double alpha, int queries) {
        if (p < 1 || p > 30 || alpha <= 0.0 || alpha > 1.0 || queries < 0) {
            throw new IllegalArgumentException("p, alpha hoặc queries không hợp lệ");
        }
        long roundedN = Math.round(alpha * (1L << p));
        if (roundedN < 1L || roundedN > Integer.MAX_VALUE - 1L) {
            throw new IllegalArgumentException("N không thể lưu trong mảng Java");
        }
        int n = (int) roundedN;
        long[] present = Generator.generatePresentKeys(seed, n);
        LongSet presentSet = new LongSet(n);
        for (int i = 0; i < present.length; i++) {
            presentSet.add(present[i]);
        }
        long[] absentPool = Generator.generateAbsentKeys(seed, queries, presentSet);
        long[] presentQueries = new long[queries];
        SplittableRandom queryRandom = new SplittableRandom(seed ^ 0x5DEECE66DL);
        for (int i = 0; i < presentQueries.length; i++) {
            presentQueries[i] = present[queryRandom.nextInt(present.length)];
        }
        String inputSha256 = Generator.computeSHA256(present, absentPool);
        return new Data(present, presentQueries, absentPool, inputSha256);
    }

    /** Làm nóng ba lần trên dữ liệu nhỏ, không tạo phase log. */
    public static void warmUp(String algo, String variant, int p, double alpha,
            double tau, long seed, int queries) {
        int warmP = Math.min(p, 12);
        int warmQueries = Math.min(queries, 10000);
        for (int repeat = 0; repeat < 3; repeat++) {
            long warmSeed = seed ^ (0x9E3779B97F4A7C15L + repeat);
            Data data = buildData(warmSeed, warmP, alpha, warmQueries);
            HashIndex index = HashIndexFactory.create(algo, variant, warmP, tau);
            runPhase("build", index, data, false);
            runPhase("present", index, data, false);
            runPhase("absent", index, data, false);
        }
    }

    /** Đo đúng một pha; chuẩn bị heap nằm ngoài vùng System.nanoTime. */
    public static PhaseResult runPhase(String phase, HashIndex index, Data data,
            boolean countMode) {
        System.gc();
        resetHeapPeaks();
        long heapBefore = heapBytes(false);
        if ("present".equals(phase) || "absent".equals(phase)) {
            Counters.reset();
        }

        long outputCheck = 0L;
        long start = System.nanoTime();
        if ("build".equals(phase)) {
            for (int i = 0; i < data.present.length; i++) {
                index.put(data.present[i], i + 1);
            }
        } else if ("present".equals(phase)) {
            outputCheck = runQueries(index, data.presentQueries);
        } else if ("absent".equals(phase)) {
            outputCheck = runQueries(index, data.absentQueries);
        } else {
            throw new IllegalArgumentException("Pha đo không hỗ trợ: " + phase);
        }
        long elapsedNanos = System.nanoTime() - start;
        long heapPeak = heapBytes(true);
        String elapsedMs = CsvWriter.formatDecimal(elapsedNanos / 1000000.0);
        String opsCount = "";
        String output = "";
        if (countMode && !"build".equals(phase)) {
            opsCount = Long.toString(Counters.probeCount);
        }
        if (!"build".equals(phase)) {
            output = Long.toString(outputCheck);
        }
        return new PhaseResult(elapsedMs, Long.toString(heapBefore),
                Long.toString(heapPeak), opsCount, output,
                data.inputSha256, GENERATOR_VERSION);
    }

    /** Trả về tổng dung lượng heap đang dùng hoặc đỉnh heap theo mẫu spec mục 6.4. */
    public static long heapBytes(boolean peak) {
        long sum = 0;
        for (MemoryPoolMXBean p : ManagementFactory.getMemoryPoolMXBeans()) {
            if (p.getType() != MemoryType.HEAP) continue;
            MemoryUsage u = peak ? p.getPeakUsage() : p.getUsage();
            if (u != null) sum += u.getUsed();
        }
        return sum;
    }

    /** Đặt lại đỉnh heap của mọi vùng theo đúng mẫu spec mục 6.4. */
    public static void resetHeapPeaks() {
        for (MemoryPoolMXBean p : ManagementFactory.getMemoryPoolMXBeans())
            if (p.getType() == MemoryType.HEAP) p.resetPeakUsage();
    }

    /** Tính tổng theo đề: giá trị tìm thấy cộng một cho mỗi khóa không tìm thấy. */
    private static long runQueries(HashIndex index, long[] queries) {
        long check = 0L;
        for (int i = 0; i < queries.length; i++) {
            int value = index.get(queries[i]);
            if (value == HashIndex.NOT_FOUND) {
                check++;
            } else {
                check += value;
            }
        }
        return check;
    }

    /** Lưu các mảng đầu vào dùng chung cho những pha của một tiến trình. */
    public static final class Data {
        public final long[] present;
        public final long[] presentQueries;
        public final long[] absentQueries;
        public final String inputSha256;

        private Data(long[] present, long[] presentQueries, long[] absentQueries,
                String inputSha256) {
            this.present = present;
            this.presentQueries = presentQueries;
            this.absentQueries = absentQueries;
            this.inputSha256 = inputSha256;
        }
    }

    /** Lưu số liệu đo đã thu được cho một pha. */
    public static final class PhaseResult {
        public final String elapsedMs;
        public final String heapBefore;
        public final String heapPeak;
        public final String opsCount;
        public final String outputCheck;
        public final String inputSha256;
        public final String generatorVersion;

        private PhaseResult(String elapsedMs, String heapBefore, String heapPeak,
                String opsCount, String outputCheck, String inputSha256,
                String generatorVersion) {
            this.elapsedMs = elapsedMs;
            this.heapBefore = heapBefore;
            this.heapPeak = heapPeak;
            this.opsCount = opsCount;
            this.outputCheck = outputCheck;
            this.inputSha256 = inputSha256;
            this.generatorVersion = generatorVersion;
        }
    }
}
