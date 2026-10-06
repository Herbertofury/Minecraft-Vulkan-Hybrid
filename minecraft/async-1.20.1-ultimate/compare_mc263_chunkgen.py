#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
import statistics
from pathlib import Path


def load(path: Path) -> dict:
    return json.loads(path.read_text(encoding="utf-8"))


def pct_gain(baseline: float, candidate: float) -> float:
    return ((baseline - candidate) / baseline * 100.0) if baseline else 0.0


def check_pair(left: dict, right: dict, pair_name: str) -> list[dict[str, object]]:
    bs = left.get("samples", [])
    cs = right.get("samples", [])
    if len(bs) != len(cs) or not bs:
        raise SystemExit(f"{pair_name}: A/B sample counts differ or are empty")
    parity = []
    for base, cand in zip(bs, cs):
        item = {
            "pair": pair_name,
            "index": base["index"],
            "same_index": base["index"] == cand["index"],
            "same_chunks": base["chunks"] == cand["chunks"],
            "same_terrain_hash": base["hash"] == cand["hash"],
            "baseline_hash": base["hash"],
            "candidate_hash": cand["hash"],
        }
        parity.append(item)
    if not all(p["same_index"] and p["same_chunks"] and p["same_terrain_hash"] for p in parity):
        raise SystemExit(f"{pair_name}: terrain parity failed; refusing performance promotion")
    return parity


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("baseline_a", type=Path)
    parser.add_argument("candidate_a", type=Path)
    parser.add_argument("baseline_b", type=Path)
    parser.add_argument("candidate_b", type=Path)
    parser.add_argument("output", type=Path)
    args = parser.parse_args()

    ba = load(args.baseline_a)
    ca = load(args.candidate_a)
    bb = load(args.baseline_b)
    cb = load(args.candidate_b)
    parity = check_pair(ba, ca, "A") + check_pair(bb, cb, "B")

    baseline_samples = ba["samples"] + bb["samples"]
    candidate_samples = ca["samples"] + cb["samples"]
    bms = [float(s["ms"]) for s in baseline_samples]
    cms = [float(s["ms"]) for s in candidate_samples]
    bmed = statistics.median(bms)
    cmed = statistics.median(cms)
    bmean = statistics.fmean(bms)
    cmean = statistics.fmean(cms)
    median_gain = pct_gain(bmed, cmed)
    mean_gain = pct_gain(bmean, cmean)

    # A default-on worldgen optimization must show a real median gain and no
    # aggregate mean regression. Otherwise it remains available but defaults off.
    promote_default_on = median_gain >= 1.0 and mean_gain >= 0.0
    result = {
        "terrain_parity": True,
        "design": "ABBA same-runner, same-seed, identical fresh chunk coordinates",
        "baseline_samples": len(bms),
        "candidate_samples": len(cms),
        "baseline_median_ms": bmed,
        "candidate_median_ms": cmed,
        "median_gain_percent": median_gain,
        "baseline_mean_ms": bmean,
        "candidate_mean_ms": cmean,
        "mean_gain_percent": mean_gain,
        "promotion_threshold": {
            "median_gain_percent_min": 1.0,
            "mean_gain_percent_min": 0.0,
        },
        "recommendation": "DEFAULT_ON" if promote_default_on else "DEFAULT_OFF",
        "parity_samples": parity,
    }
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(result, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    print(json.dumps(result, indent=2, sort_keys=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
