#!/usr/bin/env python3
from __future__ import annotations

import argparse
import os
import queue
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

# In this CI lane Vulkan is expected to be available through Mesa/lavapipe. These
# are valid fallback messages in normal production use, but they are immediate QA
# failures here because the point of this harness is to execute the real GPU path.
EXPECTED_VULKAN_FAILURE_PATTERNS = (
    "GPU Entity Module unavailable",
    "Vulkan backend unavailable; vanilla collision fallback active",
)


def run_server(server_dir: Path, log_path: Path, phase: int) -> None:
    lines: list[str] = []
    events: queue.Queue[str] = queue.Queue()

    env = os.environ.copy()
    cpu_vulkan_opt = "-Dharimt.vulkan.allowCpuDevice=true"
    existing_java_opts = env.get("JDK_JAVA_OPTIONS", "").strip()
    if cpu_vulkan_opt not in existing_java_opts.split():
        env["JDK_JAVA_OPTIONS"] = f"{existing_java_opts} {cpu_vulkan_opt}".strip()

    proc = subprocess.Popen(
        ["bash", "run.sh", "nogui"],
        cwd=server_dir,
        stdin=subprocess.PIPE,
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        text=True,
        bufsize=1,
        env=env,
    )
    assert proc.stdout is not None
    assert proc.stdin is not None

    def reader() -> None:
        for line in proc.stdout:
            print(line, end="", flush=True)
            lines.append(line)
            events.put(line)

    thread = threading.Thread(target=reader, name=f"harimt-qa-reader-{phase}", daemon=True)
    thread.start()

    def send(command: str) -> None:
        if proc.poll() is not None:
            raise RuntimeError(f"server exited before command: {command}")
        print(f"[HMT-QA] > {command}", flush=True)
        proc.stdin.write(command + "\n")
        proc.stdin.flush()

    def inspect_line(line: str, waiting_for: str) -> None:
        for pattern in FATAL_PATTERNS + EXPECTED_VULKAN_FAILURE_PATTERNS:
            if pattern in line:
                raise RuntimeError(
                    f"runtime failed while waiting for {waiting_for!r}: observed {pattern!r}"
                )

    def wait_for(text: str, timeout: float) -> None:
        # Queue reads are destructive, but QA events are not ordered relative to
        # command submission/ticks. Always consult the durable in-memory log first
        # so a marker consumed by an earlier wait remains observable later.
        snapshot = list(lines)
        for line in snapshot:
            inspect_line(line, text)
            if text in line:
                print(f"[HMT-QA] marker (recorded): {text}", flush=True)
                return

        deadline = time.monotonic() + timeout
        while time.monotonic() < deadline:
            if proc.poll() is not None and events.empty():
                raise RuntimeError(f"server exited while waiting for marker: {text}")
            remaining = max(0.05, min(1.0, deadline - time.monotonic()))
            try:
                line = events.get(timeout=remaining)
            except queue.Empty:
                # A different wait may have consumed the queue event immediately
                # before this call; re-check the durable log instead of timing out.
                snapshot = list(lines)
                for recorded in snapshot:
                    inspect_line(recorded, text)
                    if text in recorded:
                        print(f"[HMT-QA] marker (recorded): {text}", flush=True)
                        return
                continue
            inspect_line(line, text)
            if text in line:
                print(f"[HMT-QA] marker: {text}", flush=True)
                return
        raise TimeoutError(f"timed out waiting for marker: {text}")

    try:
        wait_for("Done (", 180)

        if phase == 1:
            send("gamerule doMobSpawning false")
            send("difficulty normal")
            send("forceload add 0 0")
            send("time set noon")
            send("kill @e[tag=harimt_qa]")

            # Build a 1x2-block interior collision cage. The walls prevent crowd
            # pushing from dispersing the test population while leaving the mobs in
            # valid air blocks. 192 fully-overlapping cows have 18,336 true pairs,
            # deliberately above the old 16,384 output probe size.
            send("fill 0 199 0 2 202 2 minecraft:stone hollow")
            summon = (
                'execute in minecraft:overworld run summon minecraft:cow 1.5 200 1.5 '
                '{Tags:["harimt_qa"],NoAI:1b,NoGravity:1b,Silent:1b,'
                'PersistenceRequired:1b,Invulnerable:1b}'
            )
            for _ in range(192):
                send(summon)

            # Both markers may occur while the summon burst is still being
            # processed; wait_for is intentionally order-independent.
            wait_for("Vulkan push broad-phase is active:", 90)
            wait_for("Vulkan broad-phase learned pair capacity", 90)
            wait_for("Vulkan push broad-phase sustained: 10 consecutive verified batches completed", 90)
            send("async gpu test")
            wait_for("Live GPU Verification PASS", 60)
            send("async gpu")

            # Live-disable Vulkan; deferred vanilla push replay must continue.
            send("async gpu toggle")
            wait_for("Vanilla push replay fallback is active: GPU collision is disabled", 60)

            send('execute if entity @e[tag=harimt_qa] run say HMT_QA_ENTITIES_PRESENT')
            wait_for("HMT_QA_ENTITIES_PRESENT", 20)

            # Exercise palette mutation + save while the async entity workload is live.
            send("fill 0 180 0 15 195 15 minecraft:stone")
            send("fill 0 180 0 15 195 15 minecraft:air")
            send("save-all flush")
            time.sleep(3.0)
            send("stop")
        else:
            # GPU-disable state and entities must survive restart; this also verifies
            # that server shutdown cleared only runtime references, not world state.
            wait_for("Vanilla push replay fallback is active: GPU collision is disabled", 90)
            send('execute if entity @e[tag=harimt_qa] run say HMT_QA_RESTART_ENTITIES_PRESENT')
            wait_for("HMT_QA_RESTART_ENTITIES_PRESENT", 20)

            # Re-enable Vulkan live and prove capacity learning + repeated command-
            # buffer reuse again after a full JVM/server restart. These markers can
            # race with one another, so the durable-log wait is required here too.
            send("async gpu toggle")
            wait_for("Vulkan push broad-phase is active:", 90)
            wait_for("Vulkan broad-phase learned pair capacity", 90)
            wait_for("Vulkan push broad-phase sustained: 10 consecutive verified batches completed", 90)
            send("async gpu test")
            wait_for("Live GPU Verification PASS", 60)
            send("async gpu")
            send("save-all flush")
            time.sleep(2.0)
            send("stop")

        rc = proc.wait(timeout=90)
        thread.join(timeout=5)
        while not events.empty():
            try:
                events.get_nowait()
            except queue.Empty:
                break
        if rc != 0:
            raise RuntimeError(f"Forge server exited with code {rc}")
    except BaseException:
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
                    proc.wait(timeout=15)
        thread.join(timeout=5)
        raise
    finally:
        log_path.parent.mkdir(parents=True, exist_ok=True)
        log_path.write_text("".join(lines), encoding="utf-8")

    joined = "".join(lines)
    bad = [pattern for pattern in FATAL_PATTERNS if pattern in joined]
    if bad:
        raise RuntimeError(f"fatal runtime markers found in {log_path}: {bad}")
    if "Stopping server" not in joined:
        raise RuntimeError(f"clean shutdown marker missing from {log_path}")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("server_dir", type=Path)
    parser.add_argument("evidence_dir", type=Path)
    args = parser.parse_args()

    server_dir = args.server_dir.resolve()
    evidence_dir = args.evidence_dir.resolve()
    if not (server_dir / "run.sh").is_file():
        raise SystemExit(f"Forge run.sh missing: {server_dir / 'run.sh'}")

    run_server(server_dir, evidence_dir / "packaged-server-gpu-and-fallback.log", phase=1)
    run_server(server_dir, evidence_dir / "packaged-server-restart.log", phase=2)
    print("[HMT-QA] packaged Forge dense-pair/adaptive-capacity/sustained-GPU/fallback/save/restart gate PASSED", flush=True)
    return 0


if __name__ == "__main__":
    sys.exit(main())
