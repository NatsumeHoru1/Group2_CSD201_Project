package hashbench;

import hashbench.workload.ControlTable;
import hashbench.workload.Workload;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Do cac moc workload RQ2/RQ3 trong tien trinh con. */
public final class MeasureWorkload {
    private static final int BLOCK_CYCLES = 5000;
    private static final int WARMUP_CYCLES = 8192;
    private static final int WARMUP_QUERIES = 10000;

    private MeasureWorkload() {
    }

    /** Chay tuan tu cac pha, ghi log ngay sau moi pha; tra 0, 1 hoac 3 lam exit code. */
    public static int run(String algo, String variant, String dataset, int p, double load,
            int cycles, double tau, long seed, int queries, boolean countMode,
            List<String> phases, PhaseLog log) throws IOException {
        String inputSha256 = "";
        Workload workload;
        try {
            warmUp(algo, variant, dataset, load, cycles, tau, seed);
            Counters.reset();
            workload = Workload.generate(p, load, cycles, seed, queries);
            inputSha256 = Generator.computeSHA256(
                    workload.pool(), workload.absentQueries());
        } catch (OutOfMemoryError error) {
            appendFailure(log, phases.get(0), "oom", "", "");
            System.err.println("OutOfMemoryError while preparing workload: "
                    + error.getMessage());
            return 3;
        } catch (Exception exception) {
            appendFailure(log, phases.get(0), "crash", "", "");
            System.err.println("Workload setup failed: " + exception.getMessage());
            return 1;
        }

        HashIndex index = null;
        HashIndex control = null;
        for (String phase : phases) {
            try {
                if ("build".equals(phase)) {
                    index = HashIndexFactory.create(algo, variant, p, tau);
                    final HashIndex measuredIndex = index;
                    final int[] fullCount = new int[1];
                    Measure.Measurement measurement = Measure.measure(
                            () -> fullCount[0] = workload.load(measuredIndex));
                    if (fullCount[0] > 0) {
                        appendFailure(log, phase, "wrong", inputSha256,
                                Measure.GENERATOR_VERSION);
                        return 1;
                    }
                    appendSuccess(log, phase, measurement, "", "", inputSha256,
                            Measure.GENERATOR_VERSION, emptyMetrics());
                } else if ("workload".equals(phase)) {
                    requireIndex(index);
                    final HashIndex measuredIndex = index;
                    final int blockCount = cycles == 0 ? 0
                            : (cycles - 1) / BLOCK_CYCLES + 1;
                    final long[] blockNanos = new long[blockCount];
                    final Workload.Result[] result = new Workload.Result[1];
                    Measure.Measurement measurement = Measure.measure(
                            () -> result[0] = workload.execute(
                                    measuredIndex, BLOCK_CYCLES, blockNanos));
                    String[] metrics = workloadMetrics(measuredIndex, blockNanos,
                            result[0].blocks);
                    if (result[0].fullCount > 0 || !workload.verify(measuredIndex)) {
                        appendFailure(log, phase, "wrong", inputSha256,
                                Measure.GENERATOR_VERSION);
                        return 1;
                    }
                    appendSuccess(log, phase, measurement, "", "", inputSha256,
                            Measure.GENERATOR_VERSION, metrics);
                } else if ("ctl_present".equals(phase) || "ctl_absent".equals(phase)) {
                    if (control == null) {
                        control = HashIndexFactory.create(algo, variant, p, tau);
                        ControlTable.fill(control, workload);
                    }
                    appendQueryPhase(log, phase, control, workload, countMode,
                            inputSha256);
                } else if ("present".equals(phase) || "absent".equals(phase)) {
                    requireIndex(index);
                    appendQueryPhase(log, phase, index, workload, countMode,
                            inputSha256);
                } else {
                    throw new IllegalArgumentException("Pha RQ2/RQ3 khong ho tro: " + phase);
                }
            } catch (OutOfMemoryError error) {
                appendFailure(log, phase, "oom", inputSha256,
                        Measure.GENERATOR_VERSION);
                System.err.println("OutOfMemoryError in phase " + phase + ": "
                        + error.getMessage());
                return 3;
            } catch (Exception exception) {
                appendFailure(log, phase, "crash", inputSha256,
                        Measure.GENERATOR_VERSION);
                System.err.println("Measure failed in phase " + phase + ": "
                        + exception.getMessage());
                return 1;
            }
        }
        return 0;
    }

    private static void warmUp(String algo, String variant, String dataset,
            double load, int cycles, double tau, long seed) {
        int warmCycles = Math.min(cycles, WARMUP_CYCLES);
        for (int repeat = 0; repeat < 3; repeat++) {
            long warmSeed = seed ^ (0x9E3779B97F4A7C15L + repeat);
            Workload warm = Workload.generate(12, load, warmCycles, warmSeed,
                    WARMUP_QUERIES);
            HashIndex index = HashIndexFactory.create(algo, variant, 12, tau);
            warm.load(index);
            warm.execute(index, BLOCK_CYCLES, new long[
                    warmCycles == 0 ? 0 : (warmCycles - 1) / BLOCK_CYCLES + 1]);
            Counters.reset();
            Measure.runQueries(index, warm.presentQueries());
            Counters.reset();
            Measure.runQueries(index, warm.absentQueries());
            if ("rq2".equals(dataset)) {
                HashIndex control = HashIndexFactory.create(algo, variant, 12, tau);
                ControlTable.fill(control, warm);
                Counters.reset();
                Measure.runQueries(control, warm.presentQueries());
                Counters.reset();
                Measure.runQueries(control, warm.absentQueries());
            }
        }
    }

    private static void appendQueryPhase(PhaseLog log, String phase, HashIndex index,
            Workload workload, boolean countMode, String inputSha256) throws IOException {
        String[] queries = ("present".equals(phase) || "ctl_present".equals(phase))
                ? workload.presentQueries() : workload.absentQueries();
        Counters.reset();
        final long[] outputCheck = new long[1];
        Measure.Measurement measurement = Measure.measure(
                () -> outputCheck[0] = Measure.runQueries(index, queries));
        String opsCount = countMode ? Long.toString(Counters.probeCount) : "";
        appendSuccess(log, phase, measurement, opsCount,
                Long.toString(outputCheck[0]), inputSha256,
                Measure.GENERATOR_VERSION, emptyMetrics());
    }

    private static String[] workloadMetrics(HashIndex index, long[] blockNanos,
            int blockCount) {
        if (!(index instanceof HashStats)) {
            throw new IllegalStateException("Algorithm khong cung cap HashStats");
        }
        HashStats stats = (HashStats) index;
        String[] metrics = emptyMetrics();
        double capacity = index.capacity();
        metrics[0] = CsvWriter.formatDecimal(stats.tombCount() / capacity);
        metrics[1] = CsvWriter.formatDecimal(stats.emptyCount() / capacity);
        metrics[2] = Long.toString(stats.rebuildCount());
        metrics[3] = CsvWriter.formatDecimal(stats.rebuildNanos() / 1000000.0);
        if (blockCount > 0) {
            List<Long> sorted = new ArrayList<Long>(blockCount);
            long max = 0L;
            for (int i = 0; i < blockCount; i++) {
                sorted.add(Long.valueOf(blockNanos[i]));
                max = Math.max(max, blockNanos[i]);
            }
            Collections.sort(sorted);
            double median;
            if ((blockCount & 1) == 1) {
                median = sorted.get(blockCount / 2).longValue();
            } else {
                median = (sorted.get(blockCount / 2 - 1).doubleValue()
                        + sorted.get(blockCount / 2).doubleValue()) / 2.0;
            }
            metrics[4] = CsvWriter.formatDecimal(median / 1000000.0);
            metrics[5] = CsvWriter.formatDecimal(max / 1000000.0);
        }
        metrics[6] = CsvWriter.formatDecimal(stats.dibMean());
        metrics[7] = Integer.toString(stats.dibMax());
        return metrics;
    }

    private static void appendSuccess(PhaseLog log, String phase,
            Measure.Measurement measurement, String opsCount, String outputCheck,
            String inputSha256, String generatorVersion, String[] metrics)
            throws IOException {
        log.append(phase, "ok",
                CsvWriter.formatDecimal(measurement.elapsedNanos / 1000000.0),
                Long.toString(measurement.heapBefore), Long.toString(measurement.heapPeak),
                opsCount, outputCheck, inputSha256, generatorVersion,
                metrics[0], metrics[1], metrics[2], metrics[3],
                metrics[4], metrics[5], metrics[6], metrics[7]);
    }

    private static void appendFailure(PhaseLog log, String phase, String status,
            String inputSha256, String generatorVersion) throws IOException {
        log.append(phase, status, "", "", "", "", "", inputSha256,
                generatorVersion, "", "", "", "", "", "", "", "");
    }

    private static String[] emptyMetrics() {
        return new String[] {"", "", "", "", "", "", "", ""};
    }

    private static void requireIndex(HashIndex index) {
        if (index == null) {
            throw new IllegalStateException("Pha build phai chay truoc workload/query phase");
        }
    }
}
