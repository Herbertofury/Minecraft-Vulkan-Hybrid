#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
import re
import statistics
from pathlib import Path

NOISE = "dense-256-plus-512-far-markers"
DENSE = "dense-256-vulkan-push"
SPREAD = "spread-256-single-section"
WORKLOAD_ORDER = (SPREAD, DENSE, NOISE)
SAMPLES_PER_WORKLOAD = 7
EXPECTED_PERF_TAGGED = 256
EXPECTED_NOISE_TAGGED = {SPREAD: 0, DENSE: 0, NOISE: 512}
TAGGED_STATS_RE = re.compile(
    r"HMT_PERF_STATS mspt=([0-9]+(?:\.[0-9]+)?) "
    r"entities=([0-9]+) asyncEntities=([0-9]+) "
    r"perfTagged=([0-9]+) noiseTagged=([0-9]+)"
)


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


def tagged_samples(benchmark_json: Path) -> dict[str, list[dict[str, int]]]:
    log_path = benchmark_json.parent / "server.log"
    if not log_path.is_file():
        raise SystemExit(f"benchmark server log missing: {log_path}")
    matches = list(TAGGED_STATS_RE.finditer(log_path.read_text(encoding="utf-8", errors="replace")))
    expected = len(WORKLOAD_ORDER) * SAMPLES_PER_WORKLOAD
    if len(matches) != expected:
        raise SystemExit(
            f"expected exactly {expected} tagged stats samples in {log_path}, found {len(matches)}"
        )
    result: dict[str, list[dict[str, int]]] = {}
    for index, name in enumerate(WORKLOAD_ORDER):
        chunk = matches[index * SAMPLES_PER_WORKLOAD:(index + 1) * SAMPLES_PER_WORKLOAD]
        result[name] = [
            {
                "entities": int(match.group(2)),
                "async_entities": int(match.group(3)),
                "perf_tagged": int(match.group(4)),
                "noise_tagged": int(match.group(5)),
            }
            for match in chunk
        ]
    return result


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("baseline", type=Path)
    parser.add_argument("candidate", type=Path)
    parser.add_argument("output", type=Path)
    args = parser.parse_args()

    base = workloads(load(args.baseline))
    cand = workloads(load(args.candidate))
    base_tags = tagged_samples(args.baseline)
    cand_tags = tagged_samples(args.candidate)
    missing = set(WORKLOAD_ORDER) - set(base) | (set(WORKLOAD_ORDER) - set(cand))
    if missing:
        raise SystemExit(f"benchmark workload(s) missing: {sorted(missing)}")

    report: dict[str, object] = {"pass": True, "workloads": {}, "gates": []}
    gates: list[dict[str, object]] = report["gates"]  # type: ignore[assignment]

    def add_gate(name: str, passed: bool, detail: str) -> None:
        gates.append({"name": name, "pass": passed, "detail": detail})
        if not passed:
            report["pass"] = False

    for name in WORKLOAD_ORDER:
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
            "baseline_total_entity_median": statistics.median(b.get("entity_counts", [])),
            "candidate_total_entity_median": statistics.median(c.get("entity_counts", [])),
            "baseline_perf_tagged": [sample["perf_tagged"] for sample in base_tags[name]],
            "candidate_perf_tagged": [sample["perf_tagged"] for sample in cand_tags[name]],
            "baseline_noise_tagged": [sample["noise_tagged"] for sample in base_tags[name]],
            "candidate_noise_tagged": [sample["noise_tagged"] for sample in cand_tags[name]],
        }

        expected_noise = EXPECTED_NOISE_TAGGED[name]
        b_perf = [sample["perf_tagged"] for sample in base_tags[name]]
        c_perf = [sample["perf_tagged"] for sample in cand_tags[name]]
        b_noise = [sample["noise_tagged"] for sample in base_tags[name]]
        c_noise = [sample["noise_tagged"] for sample in cand_tags[name]]
        add_gate(
            f"{name}: exact tagged benchmark population",
            all(v == EXPECTED_PERF_TAGGED for v in b_perf + c_perf)
            and all(v == expected_noise for v in b_noise + c_noise),
            f"expected perf={EXPECTED_PERF_TAGGED} noise={expected_noise}; "
            f"baseline perf={b_perf} noise={b_noise}; candidate perf={c_perf} noise={c_noise}",
        )
        add_gate(
            f"{name}: baseline/candidate tagged population parity",
            b_perf == c_perf and b_noise == c_noise,
            f"baseline perf={b_perf} noise={b_noise}; candidate perf={c_perf} noise={c_noise}",
        )

    # Ordinary workloads must not regress materially. A 5% MSPT band and 8% GPU
    # dispatch band account for normal variance between sequential JVM/server runs.
    for name in (SPREAD, DENSE):
        b_med = metric(base[name], "median")
        c_med = metric(cand[name], "median")
        b_mean = metric(base[name], "mean")
        c_mean = metric(cand[name], "mean")
        add_gate(
            f"{name}: median MSPT no-regression",
            c_med <= b_med * 1.05,
            f"baseline={b_med:.3f} ms candidate={c_med:.3f} ms change={pct_change(b_med, c_med):+.2f}%",
        )
        add_gate(
            f"{name}: mean MSPT no-regression",
            c_mean <= b_mean * 1.05,
            f"baseline={b_mean:.3f} ms candidate={c_mean:.3f} ms change={pct_change(b_mean, c_mean):+.2f}%",
        )
        b_dispatch = base[name].get("gpu", {}).get("last_dispatch_ms")
        c_dispatch = cand[name].get("gpu", {}).get("last_dispatch_ms")
        if b_dispatch is None or c_dispatch is None:
            add_gate(
                f"{name}: Vulkan dispatch telemetry present",
                False,
                f"baseline={b_dispatch!r} candidate={c_dispatch!r}",
            )
        else:
            b_dispatch = float(b_dispatch)
            c_dispatch = float(c_dispatch)
            add_gate(
                f"{name}: Vulkan dispatch no-regression",
                c_dispatch <= b_dispatch * 1.08,
                f"baseline={b_dispatch:.3f} ms candidate={c_dispatch:.3f} ms change={pct_change(b_dispatch, c_dispatch):+.2f}%",
            )

    # The optimization exists specifically to stop far unrelated entities from
    # inflating the local push broad phase. Require real game-loop improvements,
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
        "far-noise workload: >=10% mean MSPT gain",
        c_noise_mean <= b_noise_mean * 0.90,
        f"baseline={b_noise_mean:.3f} ms candidate={c_noise_mean:.3f} ms gain={-pct_change(b_noise_mean, c_noise_mean):.2f}%",
    )

    b_dispatch = base[NOISE].get("gpu", {}).get("last_dispatch_ms")
    c_dispatch = cand[NOISE].get("gpu", {}).get("last_dispatch_ms")
    if b_dispatch is None or c_dispatch is None:
        add_gate(
            "far-noise workload: Vulkan dispatch telemetry present",
            False,
            f"baseline={b_dispatch!r} candidate={c_dispatch!r}",
        )
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
