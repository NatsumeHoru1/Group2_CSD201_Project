# Hashbench

Java 8 CLI benchmark scaffold. `build.bat` compiles every `.java` file under
`hashbench/src` and creates `hashbench/out/hashbench.jar`.

## CSV output

The CSV has 32 columns in this order. The first 20 are the required columns
from `spec_notes.txt` section A:

`run_id`, `rq`, `algorithm`, `variant`, `dataset`, `n`, `params`, `seed`,
`input_sha256`, `generator_version`, `repeat`, `elapsed_ms`,
`heap_before_bytes`, `heap_peak_bytes`, `ops_count`, `output_check`, `status`,
`xmx`, `machine_id`, `git_commit`.

The 12 project-specific columns from section E5 follow:

`proc_id`, `phase`, `wall_ms`, `phase_note`, `tomb_ratio`, `empty_ratio`,
`rebuild_count`, `rebuild_ms`, `block_median_ms`, `block_max_ms`, `dib_mean`,
`dib_max`.

The phase rows currently populate the fields available for RQ1. Fields that
cannot yet be measured or identified, including `git_commit` and the RQ2/RQ3
statistics, remain empty rather than being estimated.

## Run RQ1

Build first:

```bat
build.bat
```

Example timed run for one RQ1 configuration:

```bat
java -Xmx2g -jar hashbench/out/hashbench.jar --bench --rq RQ1 --algo rh --variant base --dataset rq1a --p 10 --alpha 0.75 --seed 1 --queries 20000 --repeat 1 --xmx 2g --timeout-sec 60 --machine-id dev --out results/raw/dev/bench.csv
```

Counter run is a separate child JVM and records probe totals in `ops_count`:

```bat
java -Xmx2g -jar hashbench/out/hashbench.jar --bench --rq RQ1 --algo rh --variant base --dataset rq1a --p 10 --alpha 0.75 --seed 1 --queries 20000 --repeat 1 --xmx 2g --timeout-sec 60 --machine-id dev --out results/raw/dev/bench.csv --count
```

Only `rh/base` and `rh/noearly` are currently available through
`HashIndexFactory`. RQ1 datasets run the `build`, `present`, and `absent`
phases. `n` is computed as `round(alpha * 2^p)`. The output file is appended;
its header is written only when the file is new or empty.

`--run`, `--count`, `--selftest`, and `--phase-child` remain available for the
stub runner. `--bench` is the real RQ1 measurement path.

## Tests

After building, run:

```bat
java -cp hashbench/out hashbench.RunnerPhaseTest
java -cp hashbench/out hashbench.TV2SelfTest
java -cp hashbench/out hashbench.MeasureChildTest
```

`RunnerPhaseTest` verifies stub process handling, timeout/crash/OOM statuses,
CSV schema and append behavior. `TV2SelfTest` checks the TV2 hashing, set, and
generator modules. `MeasureChildTest` checks RQ1 measurements, deterministic
input/output checks, the `HashMap` oracle, both Robin Hood variants, probe
counters, and timeout harvesting. Tests use temporary directories and remove
their own files.
