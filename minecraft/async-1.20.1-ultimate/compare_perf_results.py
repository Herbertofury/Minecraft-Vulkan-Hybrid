#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
from pathlib import Path

NOISE = "dense-256-plus-512-far-markers"
DENSE = "dense-256-vulkan-push"
SPREAD = "spread-256-single-section"


def load(path: Path) -> dict:
    return json.loads(path.read_text(encoding="utf-8"))


def workloads(doc: dict) -> dict[str, dict]:
    return {entry["name"]: entry for entry in doc["workloads"]}


def pct_change(before: float, after: float) -> float:
    if before == 0:
        return 0.0
    return (after - before) * 100.0 / before


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
    missing = {SPREAD, DENSE, NOISE} - set(base) | ({SPREAD, DENSE, NOISE} - set(cand))
    if missing:
        raise SystemExit(f"benchmark workload(s) missing: {sorted(missing)}")

    report: dict[str, object] = {"pass": True, "workloads": {}, "gates": []}
    gates: list[dict[str, object]] = report["gates"]  # type: ignore[assignment]

    def add_gate(name: str, passed: bool, detail: str) -> None:
        gates.append({"name": name, "pass": passed, "detail": detail})
        if not passed:
            report["pass"] = False

    for name in (SPREAD, DENSE, NOISE):
        b = base[name]
        c = cand[name]
        b_med = metric(b, "median")
        c_med = metric(c, "median")
        b_mean = metric(b, "mean")
        c_mean = metric(c, "mean")
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

    # No-regression gates on the ordinary workloads. A 5% band accounts for the
    # unavoidable noise of sequential real JVM/server runs on a shared CI host.
    for name in (SPREAD, DENSE):
        b_med = metric(base[name], "median")
        c_med = metric(cand[name], "median")
        add_gate(
            f"{name}: median MSPT no-regression",
            c_med <= b_med * 1.05,
            f"baseline={b_med:.3f} ms candidate={c_med:.3f} ms change={pct_change(b_med, c_med):+.2f}%",
        )

    # The optimization exists specifically to stop far unrelated entities from
    # inflating the local push broad phase. Require an actual game-loop improvement,
    # not merely a synthetic dispatch reduction.
    b_noise_med = metric(base[NOISE], "median")
    c_noise_med = metric(cand[NOISE], "median")
    b_noise_mean = metric(base[NOISE], "mean")
    c_noise_mean = metric(cand[NOISE], "mean")
    add_gate(
        "far-noise workload: >=10% median MSPT gain",
        c_noise_med <= b_noise_med * 0.90,
        f"baseline={b_noise_med:.3f} ms candidate={c_noise_med:.3f} ms gain={-pct_change(b_noise_med, c_noise_med):.2f}%",
    )
    add_gate(
        "far-noise workload: >=8% mean MSPT gain",
        c_noise_mean <= b_noise_mean * 0.92,
        f"baseline={b_noise_mean:.3f} ms candidate={c_noise_mean:.3f} ms gain={-pct_change(b_noise_mean, c_noise_mean):.2f}%",
    )

    b_dispatch = base[NOISE].get("gpu", {}).get("last_dispatch_ms")
    c_dispatch = cand[NOISE].get("gpu", {}).get("last_dispatch_ms")
    if b_dispatch is None or c_dispatch is None:
        add_gate("far-noise workload: Vulkan dispatch telemetry present", False,
                 f"baseline={b_dispatch!r} candidate={c_dispatch!r}")
    else:
        b_dispatch = float(b_dispatch)
        c_dispatch = float(c_dispatch)
        add_gate(
            "far-noise workload: >=35% Vulkan dispatch gain",
            c_dispatch <= b_dispatch * 0.65,
            f"baseline={b_dispatch:.3f} ms candidate={c_dispatch:.3f} ms gain={-pct_change(b_dispatch, c_dispatch):.2f}%",
        )

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
