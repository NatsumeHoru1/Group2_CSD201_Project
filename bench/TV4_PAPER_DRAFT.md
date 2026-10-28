# TV4 paper contribution draft: RQ2/RQ3

This is a source-anchored draft based on the implementation and sealed
`results/raw/official/rq23_results.csv`. TV3 should review the tombstone/rebuild
description before it is merged into the paper. Do not edit the sealed CSV to
change any conclusion.

## Methods: deletion-insertion workload and control

For a table of capacity \(M=2^p\), the initial live set contains
\(N=\operatorname{round}(\text{load}\cdot M)\) unique keys. Each of the \(C\)
cycles chooses a live-key slot uniformly using a deterministic seed, deletes
that key, and inserts the next previously unused key. The generator constructs
one unique key pool of size \(N+C\), so inserted keys do not repeat earlier
keys. A compact `liveIdx` array tracks which pool indices are live; selecting a
victim and replacing its index takes constant time per cycle, without a
`HashSet` in the workload execution loop.

After generation, the benchmark samples present queries with replacement from
the \(N\) keys live at cycle \(C\). It generates absent queries from IDs outside
the complete \(N+C\)-key pool. The seed fixes the pool, victim sequence, and
query samples. The table is first built from the initial \(N\) keys and then
receives exactly \(C\) delete-insert cycles.

For RQ2, the control is a separate, newly created table with the same
algorithm, capacity, and rebuild parameter as the measured table. It is
populated only with the measured workload's live keys at \(C\), in the order
returned by `liveKeys()`. Thus it has the same logical key set but no deletion
history or accumulated tombstones. Both tables receive the same present and
absent query arrays. The comparison isolates the effect of table history
within this implementation and query setup; it is not a comparison between
different key sets.

The measured workload phase records a block every 5,000 cycles (10,000
remove/put operations), plus a possibly shorter final block. It reports the
median and maximum block duration, along with tombstone ratio, empty-slot
ratio, rebuild count, and accumulated rebuild time. These block measurements
include any rebuild triggered during a block; a maximum-block outlier alone
does not identify its cause.

## Results: RQ2

The following values are medians from the time runs in `summary.csv` for
`p=20`, live load 0.5, and nine runs per configuration. `C/M` is the number of
delete-insert cycles divided by capacity. Query durations are milliseconds per
one-million-query phase. The no-rebuild configuration uses `tau=inf`.

| C/M | Tombstone fraction after workload | Workload (ms) | Present aged (ms) | Present control (ms) | Absent aged (ms) | Absent control (ms) |
|---:|---:|---:|---:|---:|---:|---:|
| 0 | 0.000 | 0.017 | 256.760 | 150.893 | 142.358 | 98.825 |
| 0.25 | 0.188 | 131.446 | 286.492 | 165.003 | 210.458 | 104.149 |
| 0.5 | 0.302 | 269.292 | 299.544 | 168.319 | 286.805 | 106.827 |
| 1 | 0.419 | 639.161 | 313.093 | 170.493 | 526.541 | 107.115 |
| 2 | 0.486 | 2,202.584 | 328.585 | 169.124 | 2,058.896 | 108.504 |

As tombstones accumulate, unsuccessful lookups grow more costly in the aged
table: at \(C=2M\), the median absent phase is about 19 times the control
median, while the present-query medians are about 1.9 times the control. At
\(C=4M\), only 5 of 9 workload runs completed successfully (4 timed out); the
query phases also had timeouts, so this point should be reported as censored
incomplete timing data, not as a successful median comparison. At \(C=0\),
the two query paths have non-identical measured medians despite identical
logical contents; preserve the observed values and avoid interpreting that
baseline gap as an effect of aging.

## Results: RQ3 rebuild and block latency

For `lp-tomb` with finite \(\tau\), each row below summarizes nine time runs
(three seeds by three repeats), with \(M=2^{20}\). The rebuild parameter is
chosen by load in the official matrix. `block max` is the median, across runs,
of each run's slowest 10,000-operation block.

| Live load | C/M | Median workload (ms) | Median rebuild count | Median rebuild time (ms) | Median block (ms) | Median of run maxima (ms) |
|---:|---:|---:|---:|---:|---:|---:|
| 0.5 | 1 | 707.287 | 3 | 203.332 | 2.342 | 75.485 |
| 0.5 | 2 | 1,483.994 | 7 | 480.421 | 2.368 | 75.730 |
| 0.5 | 4 | 3,085.412 | 14 | 876.162 | 2.637 | 75.558 |
| 0.8 | 1 | 2,026.236 | 5 | 558.395 | 7.003 | 124.194 |
| 0.8 | 2 | 4,164.020 | 11 | 1,214.506 | 7.025 | 123.316 |
| 0.8 | 4 | 9,413.388 | 23 | 2,436.625 | 8.288 | 121.106 |

The run-level maximum blocks substantially exceed the corresponding median
blocks, making the block distribution useful for exposing latency spikes that
are hidden by total or average runtime. The maxima are not proof that each
spike was caused by a rebuild: the block also includes ordinary operations,
and scheduler, JIT, and garbage-collection effects can contribute.

## Threats to validity and implementation note

The current tombstone rebuild does not allocate and swap a second complete
table. It allocates temporary `String[liveCount]` and
`int[liveCount]` arrays, scans the current table to copy live entries, clears
the existing key/value/state arrays, and reinserts those entries in place.
These \(O(N)\) temporary arrays add transient allocation and can increase peak
heap pressure or garbage-collection activity. This is a plausible contributor
to latency or heap peaks near rebuilds, not a causal conclusion established by
the block measurements. The implementation and CSV should be rechecked if the
rebuild strategy changes.

The official measurements use one recorded machine/runtime configuration and
a finite number of seeds and repeats. The CSV also contains one documented
variance violation in an RQ2 `ctl_present` phase: 314.931 ms versus a validator
median of 152.481 ms (2.065x). The matching rerun recorded in
`results/raw/rerun_log.md` does not erase the original deviation. Keep that
qualification with any paper-level interpretation.

## Figures and source data

- RQ2 tombstone/control comparison: `results/figures/rq2_tombstones.pdf`
- RQ3 total workload time: `results/figures/rq3_total_time.pdf`
- RQ3 block latency: `results/figures/rq3_blocks.pdf`
- Aggregated values: `results/tables/summary.csv`
- Sealed measurements: `results/raw/official/rq23_results.csv`
