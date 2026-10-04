#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
import os
import re
import statistics
import subprocess
import sys
import threading
import time
from pathlib import Path

FATAL_PATTERNS = (
    "MixinApplyError",
    "InvalidMixinException",
    "InjectionError",
    "Exception in server tick loop",
    "OutOfMemoryError",
    "A fatal error has been detected by the Java Runtime Environment",
    "Failed to start the minecraft server",
)
SAMPLE_RE = re.compile(
    r"HMT_MC263_CHUNK_SAMPLE enabled=(true|false) index=(\d+) chunks=(\d+) ms=([0-9]+(?:\.[0-9]+)?) hash=([0-9a-fA-F]+)"
)
DONE_RE = re.compile(r"HMT_MC263_CHUNK_BENCH_DONE enabled=(true|false) samples=(\d+)")


def percentile(values: list[float], p: float) -> float:
    ordered = sorted(values)
    if not ordered:
        return 0.0
    if len(ordered) == 1:
        return ordered[0]
    pos = (len(ordered) - 1) * p
    lo = int(pos)
    hi = min(lo + 1, len(ordered) - 1)
    frac = pos - lo
    return ordered[lo] * (1.0 - frac) + ordered[hi] * frac


def summarize(values: list[float]) -> dict[str, float | int]:
    return {
        "samples": len(values),
        "mean_ms": statistics.fmean(values) if values else 0.0,
        "median_ms": statistics.median(values) if values else 0.0,
        "min_ms": min(values) if values else 0.0,
        "max_ms": max(values) if values else 0.0,
        "p95_ms": percentile(values, 0.95),
    }


def run(server_dir: Path, evidence_dir: Path, label: str) -> dict[str, object]:
    lines: list[str] = []
    proc = subprocess.Popen(
        ["bash", "run.sh", "nogui"],
        cwd=server_dir,
        stdin=subprocess.PIPE,
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        text=True,
        bufsize=1,
        env=os.environ.copy(),
    )
    assert proc.stdin is not None and proc.stdout is not None

    def reader() -> None:
        for line in proc.stdout:
            print(line, end="", flush=True)
            lines.append(line)

    thread = threading.Thread(target=reader, daemon=True, name="harimt-mc263-chunkgen-reader")
    thread.start()

    def inspect(line: str) -> None:
        for marker in FATAL_PATTERNS:
            if marker in line:
                raise RuntimeError(f"runtime observed fatal marker {marker!r}")

    def wait_text(marker: str, timeout: float, start: int = 0) -> str:
        deadline = time.monotonic() + timeout
        cursor = start
        while time.monotonic() < deadline:
            snapshot = list(lines)
            while cursor < len(snapshot):
                line = snapshot[cursor]
                cursor += 1
                inspect(line)
                if marker in line:
                    return line
            if proc.poll() is not None:
                raise RuntimeError(f"server exited while waiting for {marker!r}")
            time.sleep(0.05)
        raise TimeoutError(f"timed out waiting for {marker!r}")

    def send(command: str) -> int:
        if proc.poll() is not None:
            raise RuntimeError(f"server exited before command: {command}")
        start = len(lines)
        print(f"[HMT-MC263-CHUNK] > {command}", flush=True)
        proc.stdin.write(command + "\n")
        proc.stdin.flush()
        return start

    result: dict[str, object] = {"label": label, "samples": []}
    try:
        wait_text("Done (", 240)
        wait_text("=== HariMultiThread Mod Compatibility ===", 30)
        start = send("async mc263 chunkbench")
        wait_text("HMT_MC263_CHUNK_BENCH_DONE", 600, start)

        joined = "".join(lines[start:])
        samples = []
        for match in SAMPLE_RE.finditer(joined):
            samples.append({
                "enabled": match.group(1) == "true",
                "index": int(match.group(2)),
                "chunks": int(match.group(3)),
                "ms": float(match.group(4)),
                "hash": match.group(5).lower(),
            })
        done = DONE_RE.search(joined)
        if done is None:
            raise RuntimeError("chunk benchmark completion marker missing")
        expected = int(done.group(2))
        if len(samples) != expected:
            raise RuntimeError(f"expected {expected} chunk samples, parsed {len(samples)}")
        if sorted(s["index"] for s in samples) != list(range(1, expected + 1)):
            raise RuntimeError("chunk benchmark sample indexes are incomplete or duplicated")

        enabled_values = {bool(s["enabled"]) for s in samples}
        if len(enabled_values) != 1:
            raise RuntimeError(f"mixed density-cache config state in benchmark: {enabled_values}")

        ms = [float(s["ms"]) for s in samples]
        result["density_cache_enabled"] = enabled_values.pop()
        result["samples"] = samples
        result["summary"] = summarize(ms)

        send("save-all flush")
        time.sleep(2.0)
        send("stop")
        rc = proc.wait(timeout=120)
        thread.join(timeout=5)
        if rc != 0:
            raise RuntimeError(f"Forge server exited with code {rc}")
    finally:
        if proc.poll() is None:
            try:
                send("stop")
                proc.wait(timeout=30)
            except BaseException:
                proc.terminate()
                try:
                    proc.wait(timeout=15)
                except subprocess.TimeoutExpired:
                    proc.kill()
                    proc.wait(timeout=10)
        thread.join(timeout=5)
        evidence_dir.mkdir(parents=True, exist_ok=True)
        (evidence_dir / "server.log").write_text("".join(lines), encoding="utf-8")
        (evidence_dir / "benchmark.json").write_text(
            json.dumps(result, indent=2, sort_keys=True) + "\n", encoding="utf-8"
        )
    return result


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("server_dir", type=Path)
    parser.add_argument("evidence_dir", type=Path)
    parser.add_argument("--label", required=True)
    args = parser.parse_args()
    server_dir = args.server_dir.resolve()
    if not (server_dir / "run.sh").is_file():
        raise SystemExit(f"Forge run.sh missing: {server_dir / 'run.sh'}")
    result = run(server_dir, args.evidence_dir.resolve(), args.label)
    print(json.dumps(result, indent=2, sort_keys=True), flush=True)
    return 0


if __name__ == "__main__":
    sys.exit(main())
