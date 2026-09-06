#!/usr/bin/env python3
from __future__ import annotations

import argparse
import os
import queue
import shutil
import signal
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
    "GPU VERIFICATION FAILED",
    "Vulkan backend initialization failed",
    "GLFW error",
)


def wait_x(display: str, timeout: float = 20.0) -> None:
    deadline = time.monotonic() + timeout
    env = os.environ.copy()
    env["DISPLAY"] = display
    while time.monotonic() < deadline:
        if subprocess.run(["xdpyinfo"], env=env, stdout=subprocess.DEVNULL,
                          stderr=subprocess.DEVNULL).returncode == 0:
            return
        time.sleep(0.2)
    raise TimeoutError(f"Xvfb {display} did not become ready")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("project_root", type=Path)
    parser.add_argument("server_world", type=Path)
    parser.add_argument("evidence_dir", type=Path)
    args = parser.parse_args()

    root = args.project_root.resolve()
    server_world = args.server_world.resolve()
    evidence = args.evidence_dir.resolve()
    forge_run = root / "forge" / "run"
    saves = forge_run / "saves"
    qa_world = saves / "HMT-QA"
    evidence.mkdir(parents=True, exist_ok=True)
    saves.mkdir(parents=True, exist_ok=True)

    if not server_world.is_dir():
        raise SystemExit(f"server QA world missing: {server_world}")
    if qa_world.exists():
        shutil.rmtree(qa_world)
    shutil.copytree(server_world, qa_world)

    display = ":99"
    env = os.environ.copy()
    env.update({
        "DISPLAY": display,
        "LIBGL_ALWAYS_SOFTWARE": "1",
        "ALSOFT_DRIVERS": "null",
        # Mesa/lavapipe is a CPU Vulkan device in CI. Real user hardware does not
        # need this opt-in and will prefer discrete/integrated GPUs automatically.
        "JAVA_TOOL_OPTIONS": (env.get("JAVA_TOOL_OPTIONS", "")
                              + " -Dharimt.vulkan.allowCpuDevice=true").strip(),
    })

    xvfb = subprocess.Popen(
        ["Xvfb", display, "-screen", "0", "1280x720x24", "-nolisten", "tcp"],
        stdout=subprocess.DEVNULL,
        stderr=subprocess.PIPE,
        text=True,
        env=env,
    )
    proc: subprocess.Popen[str] | None = None
    lines: list[str] = []
    events: queue.Queue[str] = queue.Queue()

    try:
        wait_x(display)

        cmd = [
            "./gradlew", "--no-daemon", ":forge:runClient",
            "--args=--width 1280 --height 720 --quickPlaySingleplayer HMT-QA",
        ]
        proc = subprocess.Popen(
            cmd,
            cwd=root,
            stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT,
            text=True,
            bufsize=1,
            env=env,
        )
        assert proc.stdout is not None

        def reader() -> None:
            assert proc is not None and proc.stdout is not None
            for line in proc.stdout:
                print(line, end="", flush=True)
                lines.append(line)
                events.put(line)

        thread = threading.Thread(target=reader, name="harimt-client-qa-reader", daemon=True)
        thread.start()

        def wait_for(text: str, timeout: float) -> None:
            deadline = time.monotonic() + timeout
            while time.monotonic() < deadline:
                if proc is not None and proc.poll() is not None and events.empty():
                    raise RuntimeError(f"client exited while waiting for marker: {text}")
                remaining = max(0.05, min(1.0, deadline - time.monotonic()))
                try:
                    line = events.get(timeout=remaining)
                except queue.Empty:
                    continue
                if text in line:
                    print(f"[HMT-CLIENT-QA] marker: {text}", flush=True)
                    return
            raise TimeoutError(f"timed out waiting for client marker: {text}")

        # Render-thread evidence proves this is the real client classpath, not a
        # headless server masquerading as client QA.
        wait_for("[Render thread/INFO]", 240)
        wait_for("Vulkan push broad-phase is active:", 180)
        wait_for("Vulkan push broad-phase sustained: 10 consecutive verified batches completed", 90)

        # Capture the actual Xvfb Minecraft window. No generated imagery is used.
        screenshot = evidence / "forge-client-integrated-server.png"
        subprocess.run(
            ["import", "-display", display, "-window", "root", str(screenshot)],
            env=env,
            check=True,
            stdout=subprocess.DEVNULL,
            stderr=subprocess.DEVNULL,
            timeout=20,
        )
        if not screenshot.is_file() or screenshot.stat().st_size < 10_000:
            raise RuntimeError("client screenshot was missing or implausibly small")
        identify = subprocess.check_output(["identify", str(screenshot)], text=True, env=env)
        (evidence / "forge-client-screenshot-identify.txt").write_text(identify, encoding="utf-8")

        # Close the actual Minecraft window rather than killing Gradle. This gives
        # the integrated server its normal save/shutdown path.
        windows = subprocess.run(
            ["xdotool", "search", "--name", "Minecraft"],
            env=env,
            text=True,
            stdout=subprocess.PIPE,
            stderr=subprocess.DEVNULL,
            timeout=10,
        )
        ids = [line.strip() for line in windows.stdout.splitlines() if line.strip()]
        if not ids:
            raise RuntimeError("Minecraft X11 window not found for clean close")
        subprocess.run(
            ["xdotool", "windowactivate", "--sync", ids[-1], "key", "--clearmodifiers", "alt+F4"],
            env=env,
            check=True,
            timeout=15,
        )

        rc = proc.wait(timeout=120)
        thread.join(timeout=5)
        if rc != 0:
            raise RuntimeError(f"Forge runClient exited with code {rc}")

        joined = "".join(lines)
        bad = [pattern for pattern in FATAL_PATTERNS if pattern in joined]
        if bad:
            raise RuntimeError(f"fatal client/runtime markers found: {bad}")
        if "Vulkan push broad-phase sustained: 10 consecutive verified batches completed" not in joined:
            raise RuntimeError("sustained integrated-server Vulkan proof missing")

        log_path = forge_run / "logs" / "latest.log"
        if log_path.is_file():
            shutil.copy2(log_path, evidence / "forge-client-latest.log")
        (evidence / "forge-client-console.log").write_text(joined, encoding="utf-8")
        print("[HMT-CLIENT-QA] real Forge client + integrated-server Vulkan gate PASSED", flush=True)
        return 0
    finally:
        if proc is not None and proc.poll() is None:
            proc.terminate()
            try:
                proc.wait(timeout=20)
            except subprocess.TimeoutExpired:
                proc.kill()
                proc.wait(timeout=10)
        if xvfb.poll() is None:
            xvfb.terminate()
            try:
                xvfb.wait(timeout=10)
            except subprocess.TimeoutExpired:
                xvfb.kill()
                xvfb.wait(timeout=5)


if __name__ == "__main__":
    sys.exit(main())
