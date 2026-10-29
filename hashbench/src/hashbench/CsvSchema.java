package hashbench;

/** Noi duy nhat khai bao thu tu ten cot va chi so cot CSV. */
public final class CsvSchema {
    public static final int RUN_ID_INDEX = 0;
    public static final int RQ_INDEX = 1;
    public static final int ALGORITHM_INDEX = 2;
    public static final int VARIANT_INDEX = 3;
    public static final int DATASET_INDEX = 4;
    public static final int N_INDEX = 5;
    public static final int PARAMS_INDEX = 6;
    public static final int SEED_INDEX = 7;
    public static final int INPUT_SHA256_INDEX = 8;
    public static final int GENERATOR_VERSION_INDEX = 9;
    public static final int REPEAT_INDEX = 10;
    public static final int ELAPSED_MS_INDEX = 11;
    public static final int HEAP_BEFORE_BYTES_INDEX = 12;
    public static final int HEAP_PEAK_BYTES_INDEX = 13;
    public static final int OPS_COUNT_INDEX = 14;
    public static final int OUTPUT_CHECK_INDEX = 15;
    public static final int STATUS_INDEX = 16;
    public static final int XMX_INDEX = 17;
    public static final int MACHINE_ID_INDEX = 18;
    public static final int GIT_COMMIT_INDEX = 19;
    public static final int PROC_ID_INDEX = 20;
    public static final int PHASE_INDEX = 21;
    public static final int WALL_MS_INDEX = 22;
    public static final int PHASE_NOTE_INDEX = 23;
    public static final int TOMB_RATIO_INDEX = 24;
    public static final int EMPTY_RATIO_INDEX = 25;
    public static final int REBUILD_COUNT_INDEX = 26;
    public static final int REBUILD_MS_INDEX = 27;
    public static final int BLOCK_MEDIAN_MS_INDEX = 28;
    public static final int BLOCK_MAX_MS_INDEX = 29;
    public static final int DIB_MEAN_INDEX = 30;
    public static final int DIB_MAX_INDEX = 31;

    public static final String[] COLUMNS = {
        "run_id",
        "rq",
        "algorithm",
        "variant",
        "dataset",
        "n",
        "params",
        "seed",
        "input_sha256",
        "generator_version",
        "repeat",
        "elapsed_ms",
        "heap_before_bytes",
        "heap_peak_bytes",
        "ops_count",
        "output_check",
        "status",
        "xmx",
        "machine_id",
        "git_commit",
        "proc_id",
        "phase",
        "wall_ms",
        "phase_note",
        "tomb_ratio",
        "empty_ratio",
        "rebuild_count",
        "rebuild_ms",
        "block_median_ms",
        "block_max_ms",
        "dib_mean",
        "dib_max"
    };

    private CsvSchema() {
    }
}
