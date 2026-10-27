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

Only `rh/base`, `rh/noearly` and `lp-backshift/base` are currently available through
`HashIndexFactory`. `lp-backshift` is linear probing with Knuth Algorithm R
backward-shift deletion; its `params` column records `backshift=knuth-r`.
RQ1 datasets run the `build`, `present`, and `absent` phases. `n` is computed as
`round(alpha * 2^p)`. The output file is appended; its header is written only when
the file is new or empty.

`--run`, `--count`, `--selftest`, and `--phase-child` remain available for the
stub runner. `--bench` is the real RQ1 measurement path.

## Tests

After building, run:

```bat
java -cp hashbench/out hashbench.RunnerPhaseTest
java -cp hashbench/out hashbench.TV2SelfTest
java -cp hashbench/out hashbench.MeasureChildTest
java -cp hashbench/out hashbench.BatchTest
java -cp hashbench/out hashbench.LpBackshiftSelfTest
java -cp hashbench/out hashbench.RobinHoodSelfTest
```

`RunnerPhaseTest` verifies stub process handling, timeout/crash/OOM statuses,
CSV schema and append behavior. `TV2SelfTest` checks the TV2 hashing, set, and
generator modules. `MeasureChildTest` checks RQ1 measurements, deterministic
input/output checks, the `HashMap` oracle, both Robin Hood variants, probe
counters, and timeout harvesting. `LpBackshiftSelfTest` checks the TV6
`lp-backshift` implementation (10^6-op oracle over 6 configurations, the 6
mandatory cases plus a wrapped-cluster removal case, backshift semantics, probe
counting) and the CSV validator. Tests use temporary directories and remove
their own files.

## Validate CSV output (TV6)

Run the schema and invariant validator over any results file:

```bat
java -cp hashbench/out hashbench.CsvValidator results/raw/dev/bench.csv
```

Exit code 0 means PASS, 1 means violations were found, 2 means missing
arguments. The validator never modifies the CSV it reads. It checks the 32-column
schema, allowed status/algorithm/variant/dataset/phase values, the
`elapsed_ms` rule for non-`ok` runs, `phase_note = not_started`, unique
`run_id = proc_id + "_" + phase`, the `backshift` parameter for `lp-backshift`,
`input_sha256` format, matching `output_check` across algorithms on the same
(dataset, seed, phase), presence of all three RQ1 phases, and repeat-to-repeat
variance within 0.5-2x the median.

## Run lp-backshift

```bat
java -Xmx2g -jar hashbench/out/hashbench.jar --bench --rq RQ1 --algo lp-backshift --variant base --dataset rq1a --p 18 --alpha 0.75 --seed 1 --queries 1000000 --repeat 1 --xmx 2g --timeout-sec 60 --machine-id dev --out results/raw/dev/bench.csv
```
