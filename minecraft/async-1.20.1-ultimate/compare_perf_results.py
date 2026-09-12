#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
from pathlib import Path

NOISE = "dense-256-plus-512-far-markers"
LIVING = "three-distant-dense-256-cow-farms"
DENSE = "dense-256-vulkan-push"
SPREAD = "spread-256-single-section"
REQUIRED = {SPREAD, DENSE, NOISE, LIVING}


def load(path: Path) -> dict:
    return json.loads(path.read_text(encoding="utf-8"))


def workloads(doc: dict) -> dict[str, dict]:
    return {entry["name"]: entry for entry in doc["workloads"]}


def pct_change(before: float, after: float) -> float:
    return 0.0 if before == 0 else (after - before) * 100.0 / before


def metric(entry: dict, name: str) -> float:
    return float(entry["mspt_summary"][name])


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("baseline", type=Path)
    parser.add_argument("candidate", type=Path)
    parser.add_argument("output", type=Path)
    args = parser.parse_args()

    base = workloads(load(args.baseline))
    cand = workloads(load(args.candidate))
    missing = (REQUIRED - set(base)) | (REQUIRED - set(cand))
    if missing:
        raise SystemExit(f"benchmark workload(s) missing: {sorted(missing)}")

    report: dict[str, object] = {"pass": True, "workloads": {}, "gates": []}
    gates: list[dict[str, object]] = report["gates"]  # type: ignore[assignment]

    def add_gate(name: str, passed: bool, detail: str) -> None:
        gates.append({"name": name, "pass": passed, "detail": detail})
        if not passed:
            report["pass"] = False

    for name in (SPREAD, DENSE, NOISE, LIVING):
        b = base[name]
        c = cand[name]
        b_med, c_med = metric(b, "median"), metric(c, "median")
        b_mean, c_mean = metric(b, "mean"), metric(c, "mean")
        report["workloads"][name] = {  # type: ignore[index]
            "baseline_median_mspt": b_med,
            "candidate_median_mspt": c_med,
            "median_change_percent": pct_change(b_med, c_med),
            "baseline_mean_mspt": b_mean,
            "candidate_mean_mspt": c_mean,
            "mean_change_percent": pct_change(b_mean, c_mean),
            "baseline_p95_mspt": metric(b, "p95"),
            "candidate_p95_mspt": metric(c, "p95"),
            "baseline_last_gpu_dispatch_ms": b.get("gpu", {}).get("last_dispatch_ms"),
            "candidate_last_gpu_dispatch_ms": c.get("gpu", {}).get("last_dispatch_ms"),
        }

    # Shared-host real JVM runs are noisy, so ordinary workloads get a narrow 5%
    # guard band. The candidate must never buy a headline win by slowing the common
    # single-neighborhood cases beyond that band.
    for name in (SPREAD, DENSE):
        b_med, c_med = metric(base[name], "median"), metric(cand[name], "median")
        add_gate(
            f"{name}: median MSPT no-regression",
            c_med <= b_med * 1.05,
            f"baseline={b_med:.3f} ms candidate={c_med:.3f} ms change={pct_change(b_med, c_med):+.2f}%",
        )

    def improvement_gates(name: str, label: str, median_gain: float, mean_gain: float, dispatch_gain: float) -> None:
        b_med, c_med = metric(base[name], "median"), metric(cand[name], "median")
        b_mean, c_mean = metric(base[name], "mean"), metric(cand[name], "mean")
        add_gate(
            f"{label}: >={median_gain:.0f}% median MSPT gain",
            c_med <= b_med * (1.0 - median_gain / 100.0),
            f"baseline={b_med:.3f} ms candidate={c_med:.3f} ms gain={-pct_change(b_med, c_med):.2f}%",
        )
        add_gate(
            f"{label}: >={mean_gain:.0f}% mean MSPT gain",
            c_mean <= b_mean * (1.0 - mean_gain / 100.0),
            f"baseline={b_mean:.3f} ms candidate={c_mean:.3f} ms gain={-pct_change(b_mean, c_mean):.2f}%",
        )
        b_dispatch = base[name].get("gpu", {}).get("last_dispatch_ms")
        c_dispatch = cand[name].get("gpu", {}).get("last_dispatch_ms")
        if b_dispatch is None or c_dispatch is None:
            add_gate(f"{label}: Vulkan dispatch telemetry present", False,
                     f"baseline={b_dispatch!r} candidate={c_dispatch!r}")
        else:
            b_dispatch, c_dispatch = float(b_dispatch), float(c_dispatch)
            add_gate(
                f"{label}: >={dispatch_gain:.0f}% Vulkan dispatch gain",
                c_dispatch <= b_dispatch * (1.0 - dispatch_gain / 100.0),
                f"baseline={b_dispatch:.3f} ms candidate={c_dispatch:.3f} ms gain={-pct_change(b_dispatch, c_dispatch):.2f}%",
            )

    improvement_gates(NOISE, "far-marker workload", 10.0, 8.0, 35.0)
    # This is the stronger real-world test: three independent dense living mob
    # neighborhoods. Require an actual game-loop win as well as a GPU win.
    improvement_gates(LIVING, "three-distant-mob-farms workload", 5.0, 3.0, 25.0)

    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(report, indent=2, sort_keys=True) + "\n", encoding="utf-8")

    print("HariMultiThread Ultimate real-runtime A/B result")
    for gate in gates:
        state = "PASS" if gate["pass"] else "FAIL"
        print(f"[{state}] {gate['name']}: {gate['detail']}")
    print(json.dumps(report, indent=2, sort_keys=True))
    return 0 if report["pass"] else 1


if __name__ == "__main__":
    raise SystemExit(main())
