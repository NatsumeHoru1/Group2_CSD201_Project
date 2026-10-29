#!/usr/bin/env python3
"""bench/analyze.py - Phan tich ket qua CSD201 De 2 (Nhom 2).

Chay tu thu muc goc repo:   python bench/analyze.py
Doc  : results/raw/official/rq1_results.csv, results/raw/official/rq23_results.csv
Ghi  : results/tables/summary.csv, results/figures/*.png (+ .pdf)
Quy tac:
  * Chi dung dong status == ok de tinh median/min/max (elapsed_ms).
  * Nhom khong co dong ok nhung co timeout -> khong ve diem, chu thich "> 60 s".
  * Khong sua tay results/raw/. Moi so trong paper lay tu summary.csv hoac hinh.
Can: Python 3, matplotlib (khong can pandas).
"""
import csv
import os
import statistics as st
import sys
from collections import defaultdict

import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt  # noqa: E402

RQ1_CSV = os.path.join("results", "raw", "official", "rq1_results.csv")
RQ23_CSV = os.path.join("results", "raw", "official", "rq23_results.csv")
FIG_DIR = os.path.join("results", "figures")
TAB_DIR = os.path.join("results", "tables")
M20 = 1 << 20
QUERY_PHASES = ("present", "absent", "ctl_present", "ctl_absent")


# ----------------------------------------------------------------- doc du lieu
def num(x):
    try:
        return float(x) if x not in (None, "") else None
    except ValueError:
        return None


def parse_params(s):
    d = {}
    for kv in (s or "").split(";"):
        if "=" in kv:
            k, v = kv.split("=", 1)
            d[k] = v
    return d


def label_of(r):
    a, v = r["algorithm"], r["variant"]
    lab = a if v in ("", "base") else a + "-" + v
    if a == "lp-tomb":
        tau = r["P"].get("tau", "inf")
        # tau (0.2 hoac 0.1 theo muc tai) van nam trong cot params cua CSV
        lab = "lp-tomb (no rebuild)" if tau == "inf" else "lp-tomb+rebuild"
    return lab


def load(path):
    rows = []
    with open(path, newline="", encoding="utf-8") as f:
        for r in csv.DictReader(f):
            r["P"] = parse_params(r["params"])
            r["kind"] = "count" if "-count_" in r["run_id"] else "time"
            r["label"] = label_of(r)
            rows.append(r)
    return rows


def med(v):
    return st.median(v) if v else None


def summarize(rows):
    groups = defaultdict(list)
    for r in rows:
        P = r["P"]
        key = (r["rq"], r["dataset"], r["label"], int(float(r["n"])),
               int(float(P.get("p", 0))), float(P.get("alpha", 0)),
               int(float(P.get("c", 0))), float(P.get("load", 0)),
               r["phase"], r["kind"])
        groups[key].append(r)
    out = []
    for key, rs in groups.items():
        rq, ds, lab, n, p, alpha, c, load_, phase, kind = key
        ok = [r for r in rs if r["status"] == "ok"]
        e = [num(r["elapsed_ms"]) for r in ok if num(r["elapsed_ms"]) is not None]
        q = num(rs[0]["P"].get("queries"))

        def col(name):
            return med([num(r[name]) for r in ok if num(r[name]) is not None])

        d = dict(rq=rq, dataset=ds, label=lab, n=n, p=p, alpha=alpha, c=c,
                 load=load_, phase=phase, kind=kind, runs=len(rs), n_ok=len(ok),
                 n_timeout=sum(r["status"] == "timeout" for r in rs),
                 n_not_started=sum(r.get("phase_note") == "not_started" for r in rs),
                 n_other=sum(r["status"] not in ("ok", "timeout") for r in rs),
                 med_ms=med(e), min_ms=min(e) if e else None,
                 max_ms=max(e) if e else None, queries=q)
        div = q if phase in QUERY_PHASES else (n if phase == "build" else None)
        for nm in ("med", "min", "max"):
            v = d[nm + "_ms"]
            d["ns_" + nm] = v * 1e6 / div if (v is not None and div) else None
        pr = [num(r["ops_count"]) / q for r in ok
              if kind == "count" and phase in QUERY_PHASES and q
              and num(r["ops_count"]) is not None]
        d["probes"] = med(pr)
        d["tomb"], d["empty"] = col("tomb_ratio"), col("empty_ratio")
        d["rb_count"], d["rb_ms"] = col("rebuild_count"), col("rebuild_ms")
        d["bmed"], d["bmax"] = col("block_median_ms"), col("block_max_ms")
        d["dib_mean"], d["dib_max"] = col("dib_mean"), col("dib_max")
        out.append(d)
    out.sort(key=lambda d: (d["rq"], d["dataset"], d["label"], d["kind"],
                            d["load"], d["alpha"], d["p"], d["c"], d["phase"]))
    return out


FIELDS = ["rq", "dataset", "label", "kind", "n", "p", "alpha", "c", "load", "phase",
          "runs", "n_ok", "n_timeout", "n_not_started", "n_other",
          "med_ms", "min_ms", "max_ms", "ns_med", "ns_min", "ns_max", "probes",
          "tomb", "empty", "rb_count", "rb_ms", "bmed", "bmax", "dib_mean", "dib_max"]


def write_summary(S):
    os.makedirs(TAB_DIR, exist_ok=True)
    path = os.path.join(TAB_DIR, "summary.csv")
    with open(path, "w", newline="", encoding="utf-8") as f:
        w = csv.DictWriter(f, fieldnames=FIELDS, extrasaction="ignore")
        w.writeheader()
        for d in S:
            w.writerow({k: ("" if d[k] is None else
                            (("%.4f" % d[k]) if isinstance(d[k], float) else d[k]))
                        for k in FIELDS})
    print("Wrote", path)


# ---------------------------------------------------------------------- ve hinh
def save(fig, name, src):
    os.makedirs(FIG_DIR, exist_ok=True)
    fig.text(0.01, 0.005, "Data: %s | script: bench/analyze.py" % src,
             fontsize=6, color="gray")
    fig.tight_layout(rect=(0, 0.03, 1, 1))
    for ext in ("png", "pdf"):
        fig.savefig(os.path.join(FIG_DIR, name + "." + ext), dpi=200)
    plt.close(fig)
    print("Wrote", os.path.join(FIG_DIR, name + ".png"))


def draw(ax, rows, xkey, label):
    pts = sorted([r for r in rows if r["ns_med"] is not None], key=lambda r: r[xkey])
    if pts:
        x = [r[xkey] for r in pts]
        y = [r["ns_med"] for r in pts]
        lo = [r["ns_med"] - r["ns_min"] for r in pts]
        hi = [r["ns_max"] - r["ns_med"] for r in pts]
        ax.errorbar(x, y, yerr=[lo, hi], marker="o", capsize=3, label=label)
    note_partial(ax, pts, xkey, "ns_med")
    mark_timeouts(ax, rows, xkey)


def note_partial(ax, rows, xkey, ykey):
    """Nhom co ca dong ok lan timeout: median chi tinh tren cac luot da xong,
    nen ghi ro 'k/N ok' de khong bi hieu la du 9 luot."""
    for r in rows:
        if r["n_timeout"] > 0 and 0 < r["n_ok"] < r["runs"] and r[ykey] is not None:
            ax.annotate("%d/%d ok" % (r["n_ok"], r["runs"]), (r[xkey], r[ykey]),
                        textcoords="offset points", xytext=(0, 7), ha="center",
                        fontsize=6, color="red")


def mark_timeouts(ax, rows, xkey):
    for r in rows:
        if r["n_ok"] == 0 and r["n_timeout"] > 0:
            ax.text(r[xkey], 0.97, "> 60 s", transform=ax.get_xaxis_transform(),
                    ha="center", va="top", color="red", rotation=90, fontsize=7)


def fig_rq1_alpha(S):
    sel = [r for r in S if r["rq"] == "RQ1" and r["dataset"] == "rq1a" and r["kind"] == "time"]
    labels = sorted({r["label"] for r in sel})
    fig, axs = plt.subplots(1, 2, figsize=(7, 2.8))
    for ax, ph in zip(axs, ("present", "absent")):
        for lab in labels:
            draw(ax, [r for r in sel if r["phase"] == ph and r["label"] == lab], "alpha", lab)
        ax.set_title("(%s keys)" % ph, fontsize=9)
        ax.set_xlabel("load factor")
        ax.set_ylabel("ns / query")
        ax.set_yscale("log")
    axs[0].legend(fontsize=7)
    save(fig, "rq1_time_vs_alpha", "rq1_results.csv (dataset rq1a, M=2^20)")


def fig_rq1_size(S):
    sel = [r for r in S if r["rq"] == "RQ1" and r["dataset"] == "rq1b" and r["kind"] == "time"]
    labels = sorted({r["label"] for r in sel})
    fig, axs = plt.subplots(1, 3, figsize=(7.2, 2.6))
    for ax, ph, yl in zip(axs, ("present", "absent", "build"),
                          ("ns / query", "ns / query", "ns / key (build)")):
        for lab in labels:
            draw(ax, [r for r in sel if r["phase"] == ph and r["label"] == lab], "p", lab)
        ax.set_title(ph, fontsize=9)
        ax.set_xlabel("p  (M = 2^p)")
        ax.set_ylabel(yl)
    axs[0].legend(fontsize=7)
    save(fig, "rq1_time_vs_M", "rq1_results.csv (dataset rq1b, load 0.75)")


def fig_rq1_probes(S):
    sel = [r for r in S if r["rq"] == "RQ1" and r["dataset"] == "rq1a"
           and r["kind"] == "count" and r["probes"] is not None]
    labels = sorted({r["label"] for r in sel})
    fig, axs = plt.subplots(1, 2, figsize=(7, 2.8))
    grid = [0.45 + 0.01 * i for i in range(52)]
    for ax, ph in zip(axs, ("present", "absent")):
        for lab in labels:
            pts = sorted([r for r in sel if r["phase"] == ph and r["label"] == lab],
                         key=lambda r: r["alpha"])
            ax.plot([r["alpha"] for r in pts], [r["probes"] for r in pts], "o-", label=lab)
        if ph == "present":
            lp = [0.5 * (1 + 1 / (1 - a)) for a in grid]
            sc = [1 + a / 2 for a in grid]
        else:
            lp = [0.5 * (1 + 1 / (1 - a) ** 2) for a in grid]
            sc = list(grid)
        ax.plot(grid, lp, "k--", lw=0.8, label="Knuth LP")
        ax.plot(grid, sc, "k:", lw=0.8, label="Knuth SC (nodes)")
        ax.set_title("(%s keys)" % ph, fontsize=9)
        ax.set_xlabel("load factor")
        ax.set_ylabel("probes / query")
        ax.set_yscale("log")
    axs[0].legend(fontsize=6)
    save(fig, "rq1_probes_vs_alpha", "rq1_results.csv (--count runs, ops_count/queries)")


def fig_rq2(S):
    sel = [r for r in S if r["rq"] == "RQ2"]
    cs = sorted({r["c"] for r in sel})
    xpos = {c: i for i, c in enumerate(cs)}
    for r in sel:
        r["xi"] = xpos[r["c"]]
    ticks = ["%g" % (c / M20) for c in cs]
    t = [r for r in sel if r["kind"] == "time"]
    fig, axs = plt.subplots(1, 3, figsize=(7.4, 2.7))
    ax = axs[0]
    wl = sorted([r for r in t if r["phase"] == "workload" and r["tomb"] is not None],
                key=lambda r: r["xi"])
    ax.plot([r["xi"] for r in wl], [r["tomb"] for r in wl], "o-", label="TOMB")
    ax.plot([r["xi"] for r in wl], [r["empty"] for r in wl], "s-", label="EMPTY")
    note_partial(ax, wl, "xi", "tomb")
    mark_timeouts(ax, [r for r in t if r["phase"] == "workload"], "xi")
    ax.set_ylabel("fraction of slots")
    ax.legend(fontsize=7)
    for ax, (a, b), ttl in ((axs[1], ("absent", "ctl_absent"), "absent keys"),
                            (axs[2], ("present", "ctl_present"), "present keys")):
        draw(ax, [r for r in t if r["phase"] == a], "xi", "lp-tomb (aged)")
        draw(ax, [r for r in t if r["phase"] == b], "xi", "fresh rebuild (control)")
        ax.set_title(ttl, fontsize=9)
        ax.set_ylabel("ns / query")
        ax.set_yscale("log")
        ax.legend(fontsize=6)
    for ax in axs:
        ax.set_xticks(range(len(cs)))
        ax.set_xticklabels(ticks)
        ax.set_xlabel("C / M  (delete-insert cycles)")
    save(fig, "rq2_tombstones", "rq23_results.csv (rq=RQ2, load 0.5)")


def grouped_bars(ax, S3, loads_val, cs, labels, field, scale=1.0):
    w = 0.8 / max(1, len(labels))
    for i, lab in enumerate(labels):
        for j, c in enumerate(cs):
            rs = [r for r in S3 if r["label"] == lab and r["c"] == c and r["load"] == loads_val]
            if not rs:
                continue
            r = rs[0]
            x = j + (i - (len(labels) - 1) / 2) * w
            v = field(r)
            if v is None:
                if r["n_timeout"] > 0:
                    ax.text(x, 0.02, "> 60 s", transform=ax.get_xaxis_transform(),
                            ha="center", va="bottom", color="red", rotation=90, fontsize=6)
                continue
            ax.bar(x, v * scale, w, color="C%d" % i, label=lab if j == 0 else None)
            if r["n_timeout"] > 0 and 0 < r["n_ok"] < r["runs"]:
                ax.text(x, v * scale, "%d/%d" % (r["n_ok"], r["runs"]), ha="center",
                        va="bottom", fontsize=6, color="red")
    ax.set_xticks(range(len(cs)))
    ax.set_xticklabels(["%g" % (c / M20) for c in cs])
    ax.set_xlabel("C / M")


def fig_rq3(S):
    S3 = [r for r in S if r["rq"] == "RQ3" and r["kind"] == "time" and r["phase"] == "workload"]
    if not S3:
        return
    labels = sorted({r["label"] for r in S3})
    cs = sorted({r["c"] for r in S3})
    loads = sorted({r["load"] for r in S3})
    fig, axs = plt.subplots(1, len(loads), figsize=(7, 2.8), squeeze=False)
    for ax, ld in zip(axs[0], loads):
        grouped_bars(ax, S3, ld, cs, labels, lambda r: r["med_ms"], 1e-3)
        ax.set_title("live load %g" % ld, fontsize=9)
        ax.set_ylabel("workload time (s)")
    axs[0][0].legend(fontsize=6)
    save(fig, "rq3_total_time", "rq23_results.csv (rq=RQ3, phase=workload)")

    fig, axs = plt.subplots(2, len(loads), figsize=(7, 4.6), squeeze=False)
    for k, ld in enumerate(loads):
        grouped_bars(axs[0][k], S3, ld, cs, labels, lambda r: r["bmax"])
        axs[0][k].set_ylabel("max 10^4-op block (ms)")
        axs[0][k].set_title("live load %g" % ld, fontsize=9)
        grouped_bars(axs[1][k], S3, ld, cs, labels,
                     lambda r: (r["bmax"] / r["bmed"]) if (r["bmax"] and r["bmed"]) else None)
        axs[1][k].set_ylabel("block max / block median")
        axs[1][k].set_yscale("log")
    axs[0][0].legend(fontsize=6)
    save(fig, "rq3_blocks", "rq23_results.csv (block_median_ms, block_max_ms)")


# ------------------------------------------------------------------------ main
def status_report(name, rows):
    cnt = defaultdict(int)
    for r in rows:
        cnt[r["status"]] += 1
    print("%s: %d dong; %s" % (name, len(rows), dict(cnt)))
    procs = defaultdict(set)
    for r in rows:
        procs[r["status"]].add(r["proc_id"])
    print("   tien trinh theo status:", {k: len(v) for k, v in procs.items()})


def main():
    for p in (RQ1_CSV, RQ23_CSV):
        if not os.path.exists(p):
            sys.exit("Khong thay %s - hay chay tu thu muc goc repo." % p)
    r1, r23 = load(RQ1_CSV), load(RQ23_CSV)
    status_report("rq1", r1)
    status_report("rq23", r23)
    S = summarize(r1 + r23)
    write_summary(S)
    fig_rq1_alpha(S)
    fig_rq1_size(S)
    fig_rq1_probes(S)
    fig_rq2(S)
    fig_rq3(S)
    print("\nKiem tra nhanh so lan do (LP present a=0.5 ~ 1.5; LP absent a=0.5 ~ 2.5):")
    for r in S:
        if r["dataset"] == "rq1a" and r["kind"] == "count" and r["probes"] is not None \
                and abs(r["alpha"] - 0.5) < 1e-9:
            print("  %-22s %-8s probes/query = %.3f" % (r["label"], r["phase"], r["probes"]))


if __name__ == "__main__":
    main()