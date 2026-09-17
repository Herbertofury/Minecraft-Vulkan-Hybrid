#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
import os
import re
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
    "HMT_MC263_IDLE_FAIL",
)

LOCATE_PATTERN = re.compile(r"\[(-?\d+),\s*(?:~|-?\d+),\s*(-?\d+)\].*blocks away", re.IGNORECASE)
IDLE_PATTERN = re.compile(r"HMT_MC263_IDLE_PASS.*maxNoActionTime=(\d+)")


def run_server(server_dir: Path, evidence_dir: Path) -> None:
    lines: list[str] = []
    env = os.environ.copy()
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

    thread = threading.Thread(target=reader, name="harimt-mc263-qa-reader", daemon=True)
    thread.start()

    def send(command: str) -> None:
        if proc.poll() is not None:
            raise RuntimeError(f"server exited before command: {command}")
        print(f"[HMT-MC263-QA] > {command}", flush=True)
        proc.stdin.write(command + "\n")
        proc.stdin.flush()

    def inspect(text: str) -> None:
        for pattern in FATAL_PATTERNS:
            if pattern in text:
                raise RuntimeError(f"runtime observed fatal marker {pattern!r}")

    def wait_for_text(text: str, timeout: float, start: int = 0) -> str:
        deadline = time.monotonic() + timeout
        cursor = start
        while time.monotonic() < deadline:
            snapshot = list(lines)
            while cursor < len(snapshot):
                line = snapshot[cursor]
                cursor += 1
                inspect(line)
                if text in line:
                    print(f"[HMT-MC263-QA] marker: {text}", flush=True)
                    return line
            if proc.poll() is not None:
                raise RuntimeError(f"server exited while waiting for marker: {text}")
            time.sleep(0.05)
        raise TimeoutError(f"timed out waiting for marker: {text}")

    def wait_for_regex(pattern: re.Pattern[str], timeout: float, start: int) -> tuple[re.Match[str], str]:
        deadline = time.monotonic() + timeout
        cursor = start
        while time.monotonic() < deadline:
            snapshot = list(lines)
            while cursor < len(snapshot):
                line = snapshot[cursor]
                cursor += 1
                inspect(line)
                match = pattern.search(line)
                if match:
                    print(f"[HMT-MC263-QA] regex marker: {pattern.pattern}", flush=True)
                    return match, line
            if proc.poll() is not None:
                raise RuntimeError(f"server exited while waiting for regex: {pattern.pattern}")
            time.sleep(0.05)
        raise TimeoutError(f"timed out waiting for regex: {pattern.pattern}")

    metrics: dict[str, object] = {}
    try:
        wait_for_text("Done (", 180)

        joined_startup = "".join(lines)
        expected_startup = (
            "Terrain renderer: vanilla 1.20.1 owner (Hari core remains renderer-neutral)",
            "Shader pipeline: vanilla 1.20.1 owner (Hari core does not force OIT/ShaderC)",
        )
        missing = [marker for marker in expected_startup if marker not in joined_startup]
        if missing:
            raise RuntimeError(f"26.3 provider-precedence startup markers missing: {missing}")

        send("gamerule doMobSpawning false")
        send("forceload add 0 0")
        send("kill @e[tag=harimt_mc263_idle]")
        send('summon minecraft:cow 1.5 200 1.5 {Tags:["harimt_mc263_idle"],NoGravity:1b,Silent:1b,PersistenceRequired:1b,Invulnerable:1b}')

        # Let serverAiStep/checkDespawn run enough times that a vanilla 1.20.1
        # persistent mob would still be pinned at zero while the backport climbs.
        time.sleep(5.0)
        status_start = len(lines)
        send("async mc263")
        wait_for_text("Minecraft 26.3 Backports", 30, status_start)

        idle_start = len(lines)
        send("async mc263 test")
        idle_match, _ = wait_for_regex(IDLE_PATTERN, 30, idle_start)
        max_no_action = int(idle_match.group(1))
        if max_no_action <= 0:
            raise RuntimeError(f"persistent-mob idle counter did not advance: {max_no_action}")
        metrics["persistent_mob_idle_max_no_action_time"] = max_no_action

        locate1_start = len(lines)
        t0 = time.monotonic()
        send("locate structure minecraft:stronghold")
        locate1, _ = wait_for_regex(LOCATE_PATTERN, 90, locate1_start)
        t1 = time.monotonic()
        first_pos = (int(locate1.group(1)), int(locate1.group(2)))

        locate2_start = len(lines)
        t2 = time.monotonic()
        send("locate structure minecraft:stronghold")
        locate2, _ = wait_for_regex(LOCATE_PATTERN, 90, locate2_start)
        t3 = time.monotonic()
        second_pos = (int(locate2.group(1)), int(locate2.group(2)))
        if first_pos != second_pos:
            raise RuntimeError(f"repeated locate changed result: first={first_pos}, second={second_pos}")
        metrics["structure_locate_position_xz"] = list(first_pos)
        metrics["structure_locate_first_seconds"] = round(t1 - t0, 6)
        metrics["structure_locate_second_seconds"] = round(t3 - t2, 6)

        # Force genuinely fresh, distant terrain through NoiseChunk Cache2D and
        # then put a command behind it on the server thread. This is an execution
        # gate, not a noisy CI speed threshold.
        chunk_start = len(lines)
        send("forceload add 16384 16384 16447 16447")
        send("say HMT_MC263_CHUNKGEN_PASS")
        wait_for_text("HMT_MC263_CHUNKGEN_PASS", 120, chunk_start)
        send("save-all flush")
        time.sleep(3.0)
        send("stop")

        rc = proc.wait(timeout=90)
        thread.join(timeout=5)
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
        evidence_dir.mkdir(parents=True, exist_ok=True)
        (evidence_dir / "mc263-runtime.log").write_text("".join(lines), encoding="utf-8")
        (evidence_dir / "mc263-runtime-metrics.json").write_text(
            json.dumps(metrics, indent=2, sort_keys=True) + "\n", encoding="utf-8"
        )

    joined = "".join(lines)
    inspect(joined)
    if "Stopping server" not in joined:
        raise RuntimeError("clean shutdown marker missing from Minecraft 26.3 runtime QA")
    if "HMT_MC263_CHUNKGEN_PASS" not in joined:
        raise RuntimeError("fresh chunk-generation marker missing")

    print("[HMT-MC263-QA] persistent-idle/locate/chunkgen/provider-precedence gate PASSED", flush=True)


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("server_dir", type=Path)
    parser.add_argument("evidence_dir", type=Path)
    args = parser.parse_args()
    server_dir = args.server_dir.resolve()
    if not (server_dir / "run.sh").is_file():
        raise SystemExit(f"Forge run.sh missing: {server_dir / 'run.sh'}")
    run_server(server_dir, args.evidence_dir.resolve())
    return 0


if __name__ == "__main__":
    sys.exit(main())
