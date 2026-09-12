#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
import os
import queue
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
    "VK_ERROR_DEVICE_LOST",
    "A fatal error has been detected by the Java Runtime Environment",
    "Failed to start the minecraft server",
    "GPU VERIFICATION FAILED",
    "Vulkan backend initialization failed",
)

MSPT_RE = re.compile(r"MSPT:\s*([0-9]+(?:\.[0-9]+)?)ms")
ENTITIES_RE = re.compile(r"Entities:\s*([0-9]+)")
LAST_DISPATCH_RE = re.compile(r"Last dispatch:\s*([0-9]+(?:\.[0-9]+)?) ms")
GPU_BATCHES_RE = re.compile(r"Deferred push GPU batches:\s*([0-9]+)")
PAIR_RE = re.compile(r"Candidate pairs:\s*([0-9]+)")


def percentile(values: list[float], p: float) -> float:
    if not values:
        return 0.0
    ordered = sorted(values)
    if len(ordered) == 1:
        return ordered[0]
    pos = (len(ordered) - 1) * p
    lo = int(pos)
    hi = min(lo + 1, len(ordered) - 1)
    frac = pos - lo
    return ordered[lo] * (1.0 - frac) + ordered[hi] * frac


def summarize(values: list[float]) -> dict[str, float]:
    return {
        "samples": len(values),
        "mean": statistics.fmean(values) if values else 0.0,
        "median": statistics.median(values) if values else 0.0,
        "min": min(values) if values else 0.0,
        "max": max(values) if values else 0.0,
        "p95": percentile(values, 0.95),
    }


class ServerHarness:
    def __init__(self, server_dir: Path, log_path: Path) -> None:
        self.server_dir = server_dir
        self.log_path = log_path
        self.lines: list[str] = []
        self.events: queue.Queue[str] = queue.Queue()
        self.proc: subprocess.Popen[str] | None = None
        self.reader_thread: threading.Thread | None = None

    def start(self) -> None:
        env = os.environ.copy()
        opts = env.get("JDK_JAVA_OPTIONS", "").strip()
        required = (
            "-Dharimt.vulkan.allowCpuDevice=true",
            "-Xms3G",
            "-Xmx3G",
            "-XX:+AlwaysPreTouch",
        )
        for opt in required:
            if opt not in opts.split():
                opts = f"{opts} {opt}".strip()
        env["JDK_JAVA_OPTIONS"] = opts

        self.proc = subprocess.Popen(
            ["bash", "run.sh", "nogui"],
            cwd=self.server_dir,
            stdin=subprocess.PIPE,
            stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT,
            text=True,
            bufsize=1,
            env=env,
        )
        assert self.proc.stdout is not None
        assert self.proc.stdin is not None

        def reader() -> None:
            assert self.proc is not None and self.proc.stdout is not None
            for line in self.proc.stdout:
                print(line, end="", flush=True)
                self.lines.append(line)
                self.events.put(line)

        self.reader_thread = threading.Thread(target=reader, name="harimt-perf-reader", daemon=True)
        self.reader_thread.start()
        self.wait_for_text("Done (", 240)

    def inspect(self, line: str, context: str) -> None:
        for pattern in FATAL_PATTERNS:
            if pattern in line:
                raise RuntimeError(f"runtime failed during {context}: observed {pattern!r}")

    def send(self, command: str) -> int:
        if self.proc is None or self.proc.poll() is not None:
            raise RuntimeError(f"server exited before command: {command}")
        assert self.proc.stdin is not None
        start = len(self.lines)
        print(f"[HMT-PERF] > {command}", flush=True)
        self.proc.stdin.write(command + "\n")
        self.proc.stdin.flush()
        return start

    def wait_for_text(self, text: str, timeout: float, start: int = 0) -> str:
        deadline = time.monotonic() + timeout
        cursor = start
        while time.monotonic() < deadline:
            while cursor < len(self.lines):
                line = self.lines[cursor]
                cursor += 1
                self.inspect(line, text)
                if text in line:
                    return line
            if self.proc is not None and self.proc.poll() is not None:
                raise RuntimeError(f"server exited while waiting for {text!r}")
            time.sleep(0.05)
        raise TimeoutError(f"timed out waiting for {text!r}")

    def wait_for_regex(self, pattern: re.Pattern[str], timeout: float, start: int) -> re.Match[str]:
        deadline = time.monotonic() + timeout
        cursor = start
        while time.monotonic() < deadline:
            while cursor < len(self.lines):
                line = self.lines[cursor]
                cursor += 1
                self.inspect(line, pattern.pattern)
                match = pattern.search(line)
                if match:
                    return match
            if self.proc is not None and self.proc.poll() is not None:
                raise RuntimeError(f"server exited while waiting for /{pattern.pattern}/")
            time.sleep(0.05)
        raise TimeoutError(f"timed out waiting for /{pattern.pattern}/")

    def collect_stats(self, count: int = 7, interval: float = 6.0) -> tuple[list[float], list[int]]:
        mspt: list[float] = []
        entity_counts: list[int] = []
        for i in range(count):
            start = self.send("async stats")
            m = self.wait_for_regex(MSPT_RE, 20, start)
            mspt.append(float(m.group(1)))
            # The entity count is normally part of the same multi-line command
            # response. It is useful evidence but not a benchmark requirement.
            try:
                e = self.wait_for_regex(ENTITIES_RE, 5, start)
                entity_counts.append(int(e.group(1)))
            except TimeoutError:
                pass
            print(f"[HMT-PERF] sample {i + 1}/{count}: {mspt[-1]:.3f} MSPT", flush=True)
            if i + 1 < count:
                time.sleep(interval)
        return mspt, entity_counts

    def collect_gpu_status(self) -> dict[str, float | int]:
        start = self.send("async gpu")
        result: dict[str, float | int] = {}
        for key, regex, cast in (
            ("last_dispatch_ms", LAST_DISPATCH_RE, float),
            ("gpu_batches", GPU_BATCHES_RE, int),
            ("candidate_pairs", PAIR_RE, int),
        ):
            try:
                match = self.wait_for_regex(regex, 8, start)
                result[key] = cast(match.group(1))
            except TimeoutError:
                pass
        return result

    def stop(self) -> None:
        if self.proc is None:
            return
        try:
            if self.proc.poll() is None:
                self.send("stop")
                self.proc.wait(timeout=120)
        finally:
            if self.proc.poll() is None:
                self.proc.terminate()
                try:
                    self.proc.wait(timeout=20)
                except subprocess.TimeoutExpired:
                    self.proc.kill()
                    self.proc.wait(timeout=10)
            if self.reader_thread is not None:
                self.reader_thread.join(timeout=5)
            self.log_path.parent.mkdir(parents=True, exist_ok=True)
            self.log_path.write_text("".join(self.lines), encoding="utf-8")


def prepare_common(h: ServerHarness) -> None:
    h.send("gamerule doMobSpawning false")
    h.send("gamerule maxEntityCramming 0")
    h.send("difficulty normal")
    h.send("forceload add 0 0")
    h.send("time set noon")
    h.send("kill @e[tag=harimt_perf]")
    h.send("fill 0 198 0 15 198 15 minecraft:stone")
    h.send("fill 0 199 0 15 203 15 minecraft:air")
    time.sleep(2.0)


def summon_spread(h: ServerHarness) -> None:
    # 256 live cows in one entity section, one per block. Their AABBs do not
    # initially intersect. This heavily exercises EntitySection lookup while
    # avoiding an O(N^2) pile of actual push responses, making query/allocation
    # overhead visible in real server MSPT.
    for x in range(16):
        for z in range(16):
            h.send(
                f'execute in minecraft:overworld run summon minecraft:cow {x + 0.5:.1f} 199 {z + 0.5:.1f} '
                '{Tags:["harimt_perf"],NoAI:1b,NoGravity:1b,Silent:1b,'
                'PersistenceRequired:1b,Invulnerable:1b}'
            )


def summon_dense(h: ServerHarness) -> None:
    # Same entity count, same dimension/chunk, but all overlapping. This stresses
    # the deferred push replay and Vulkan broad phase using the production path.
    for _ in range(256):
        h.send(
            'execute in minecraft:overworld run summon minecraft:cow 8.5 199 8.5 '
            '{Tags:["harimt_perf"],NoAI:1b,NoGravity:1b,Silent:1b,'
            'PersistenceRequired:1b,Invulnerable:1b}'
        )


def run_scenario(h: ServerHarness, name: str, summon) -> dict[str, object]:
    print(f"[HMT-PERF] preparing scenario={name}", flush=True)
    h.send("kill @e[tag=harimt_perf]")
    time.sleep(3.0)
    summon(h)
    h.wait_for_text("Vulkan push broad-phase sustained: 10 consecutive verified batches completed", 120)
    # Long enough to fill MinecraftServer's moving average with the scenario and
    # let HotSpot compile the hot paths. The exact same warm-up is used for A/B.
    time.sleep(20.0)
    mspt, entity_counts = h.collect_stats()
    gpu = h.collect_gpu_status()
    return {
        "name": name,
        "mspt": mspt,
        "mspt_summary": summarize(mspt),
        "entity_counts": entity_counts,
        "gpu": gpu,
    }


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("server_dir", type=Path)
    parser.add_argument("evidence_dir", type=Path)
    parser.add_argument("--label", default="candidate")
    args = parser.parse_args()

    server_dir = args.server_dir.resolve()
    evidence_dir = args.evidence_dir.resolve()
    evidence_dir.mkdir(parents=True, exist_ok=True)
    if not (server_dir / "run.sh").is_file():
        raise SystemExit(f"Forge run.sh missing: {server_dir / 'run.sh'}")

    h = ServerHarness(server_dir, evidence_dir / "server.log")
    result: dict[str, object] = {
        "label": args.label,
        "benchmark": "HariMultiThread Ultimate real Forge 47.4.23 entity workload",
        "java": 17,
        "workloads": [],
    }
    try:
        h.start()
        prepare_common(h)
        workloads = result["workloads"]
        assert isinstance(workloads, list)
        workloads.append(run_scenario(h, "spread-256-single-section", summon_spread))
        workloads.append(run_scenario(h, "dense-256-vulkan-push", summon_dense))
        # Final live verifier proves the GPU candidate set still contains every
        # exact double-precision overlap under the benchmark workload.
        start = h.send("async gpu test")
        h.wait_for_text("Live GPU Verification PASS", 60, start)
        h.send("save-all flush")
        time.sleep(2.0)
    finally:
        h.stop()

    (evidence_dir / "benchmark.json").write_text(
        json.dumps(result, indent=2, sort_keys=True) + "\n",
        encoding="utf-8",
    )
    print(json.dumps(result, indent=2, sort_keys=True), flush=True)
    return 0


if __name__ == "__main__":
    sys.exit(main())
