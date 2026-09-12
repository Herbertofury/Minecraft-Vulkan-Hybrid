#!/usr/bin/env python3
from __future__ import annotations

import argparse
import os
import queue
import shutil
import subprocess
import sys
import threading
import time
import traceback
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

MIN_RENDER_WIDTH = 800
MIN_RENDER_HEIGHT = 450
MIN_RENDER_COLORS = 64
MIN_GRAY_STDDEV = 0.050
RENDER_READY_TIMEOUT = 60.0


def wait_x(display: str, timeout: float = 20.0) -> None:
    deadline = time.monotonic() + timeout
    env = os.environ.copy()
    env["DISPLAY"] = display
    while time.monotonic() < deadline:
        if subprocess.run(
            ["xdpyinfo"],
            env=env,
            stdout=subprocess.DEVNULL,
            stderr=subprocess.DEVNULL,
        ).returncode == 0:
            return
        time.sleep(0.2)
    raise TimeoutError(f"Xvfb {display} did not become ready")


def image_metrics(path: Path, env: dict[str, str]) -> tuple[int, int, int, float]:
    geometry = subprocess.check_output(
        ["identify", "-format", "%w %h %k", str(path)],
        text=True,
        env=env,
        timeout=10,
    ).strip().split()
    if len(geometry) != 3:
        raise RuntimeError(f"unexpected ImageMagick identify output for {path}: {geometry!r}")
    width, height, colors = map(int, geometry)

    convert = shutil.which("magick") or shutil.which("convert")
    if convert is None:
        raise RuntimeError("ImageMagick magick/convert is required for screenshot readiness QA")
    cmd = [convert, str(path), "-colorspace", "Gray", "-format", "%[fx:standard_deviation]", "info:"]
    stddev = float(
        subprocess.check_output(
            cmd,
            text=True,
            env=env,
            stderr=subprocess.DEVNULL,
            timeout=10,
        ).strip()
    )
    return width, height, colors, stddev


def rendered_world_ready(path: Path, env: dict[str, str]) -> tuple[bool, str]:
    if not path.is_file():
        return False, "capture missing"
    width, height, colors, stddev = image_metrics(path, env)
    ready = (
        width >= MIN_RENDER_WIDTH
        and height >= MIN_RENDER_HEIGHT
        and colors >= MIN_RENDER_COLORS
        and stddev >= MIN_GRAY_STDDEV
    )
    reason = (
        f"{width}x{height}, colors={colors}, grayStdDev={stddev:.6f}, "
        f"bytes={path.stat().st_size}"
    )
    return ready, reason


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
    env.update(
        {
            "DISPLAY": display,
            "LIBGL_ALWAYS_SOFTWARE": "1",
            "ALSOFT_DRIVERS": "null",
            # Mesa/lavapipe is a CPU Vulkan device in CI. Real user hardware does not
            # need this opt-in and will prefer discrete/integrated GPUs automatically.
            "JAVA_TOOL_OPTIONS": (
                env.get("JAVA_TOOL_OPTIONS", "") + " -Dharimt.vulkan.allowCpuDevice=true"
            ).strip(),
        }
    )

    xvfb = subprocess.Popen(
        ["Xvfb", display, "-screen", "0", "1280x720x24", "-nolisten", "tcp"],
        stdout=subprocess.DEVNULL,
        stderr=subprocess.PIPE,
        text=True,
        env=env,
    )
    wm: subprocess.Popen[str] | None = None
    wm_log = None
    proc: subprocess.Popen[str] | None = None
    lines: list[str] = []
    events: queue.Queue[str] = queue.Queue()

    try:
        wait_x(display)

        if shutil.which("openbox") is None:
            raise RuntimeError("openbox is required for native client window QA")
        wm_log = (evidence / "openbox.log").open("w", encoding="utf-8")
        wm = subprocess.Popen(
            ["openbox", "--sm-disable"],
            stdout=wm_log,
            stderr=subprocess.STDOUT,
            text=True,
            env=env,
        )
        time.sleep(0.75)
        if wm.poll() is not None:
            raise RuntimeError("Openbox exited before Forge client launch")

        cmd = [
            "./gradlew",
            "--no-daemon",
            ":forge:runClient",
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

        thread = threading.Thread(
            target=reader,
            name="harimt-client-qa-reader",
            daemon=True,
        )
        thread.start()

        def inspect_line(line: str, waiting_for: str) -> None:
            for pattern in FATAL_PATTERNS:
                if pattern in line:
                    raise RuntimeError(
                        f"client/runtime failed while waiting for {waiting_for!r}: observed {pattern!r}"
                    )

        def wait_for(text: str, timeout: float) -> None:
            # Marker waits are order-independent. A previous wait may consume a
            # queue event just before the next marker wait begins, so always scan
            # the durable console transcript as well as new queue events.
            for recorded in list(lines):
                inspect_line(recorded, text)
                if text in recorded:
                    print(f"[HMT-CLIENT-QA] marker (recorded): {text}", flush=True)
                    return

            deadline = time.monotonic() + timeout
            while time.monotonic() < deadline:
                if proc is not None and proc.poll() is not None and events.empty():
                    raise RuntimeError(f"client exited while waiting for marker: {text}")
                remaining = max(0.05, min(1.0, deadline - time.monotonic()))
                try:
                    line = events.get(timeout=remaining)
                except queue.Empty:
                    for recorded in list(lines):
                        inspect_line(recorded, text)
                        if text in recorded:
                            print(f"[HMT-CLIENT-QA] marker (recorded): {text}", flush=True)
                            return
                    continue
                inspect_line(line, text)
                if text in line:
                    print(f"[HMT-CLIENT-QA] marker: {text}", flush=True)
                    return
            raise TimeoutError(f"timed out waiting for client marker: {text}")

        # These markers prove the real client, successful local login and sustained
        # Vulkan work. A joined player does not by itself prove that the client has
        # left the Loading terrain screen, so visual readiness is checked separately.
        wait_for("[Render thread/INFO]", 240)
        wait_for(" joined the game", 180)
        wait_for("Vulkan push broad-phase is active:", 180)
        wait_for(
            "Vulkan push broad-phase sustained: 10 consecutive verified batches completed",
            90,
        )

        windows = subprocess.run(
            ["xdotool", "search", "--onlyvisible", "--name", "Minecraft"],
            env=env,
            text=True,
            stdout=subprocess.PIPE,
            stderr=subprocess.DEVNULL,
            timeout=10,
        )
        ids = [line.strip() for line in windows.stdout.splitlines() if line.strip()]
        if not ids:
            raise RuntimeError("visible Minecraft X11 window not found")
        window_id = ids[-1]
        subprocess.run(
            ["xdotool", "windowactivate", "--sync", window_id],
            env=env,
            check=True,
            timeout=15,
        )

        # Capture only after the actual Minecraft window becomes visually complex
        # enough to be a rendered world. The previous gate incorrectly used PNG
        # byte size and captured the 9-color 'Loading terrain...' screen. The new
        # gate retries the real window until dimensions, palette diversity and
        # grayscale variance prove a rendered frame. It does not OCR or synthesize
        # imagery and therefore remains independent of localized UI text.
        screenshot = evidence / "forge-client-integrated-server.png"
        candidate = evidence / "forge-client-integrated-server-candidate.png"
        metrics_log: list[str] = []
        render_deadline = time.monotonic() + RENDER_READY_TIMEOUT
        attempt = 0
        while time.monotonic() < render_deadline:
            attempt += 1
            if proc.poll() is not None:
                raise RuntimeError("Forge client exited before a rendered-world frame was captured")
            subprocess.run(
                ["import", "-display", display, "-window", window_id, str(candidate)],
                env=env,
                check=True,
                stdout=subprocess.DEVNULL,
                stderr=subprocess.DEVNULL,
                timeout=20,
            )
            ready, metric_text = rendered_world_ready(candidate, env)
            entry = f"attempt={attempt} ready={str(ready).lower()} {metric_text}"
            metrics_log.append(entry)
            print(f"[HMT-CLIENT-QA] rendered-world probe: {entry}", flush=True)
            if ready:
                candidate.replace(screenshot)
                break
            time.sleep(1.0)
        else:
            if candidate.is_file():
                shutil.copy2(candidate, evidence / "forge-client-last-unready-frame.png")
            raise TimeoutError(
                "client joined and sustained Vulkan work but never produced a rendered-world frame"
            )
        candidate.unlink(missing_ok=True)
        (evidence / "forge-client-render-readiness.txt").write_text(
            "\n".join(metrics_log) + "\n",
            encoding="utf-8",
        )
        identify = subprocess.check_output(
            ["identify", str(screenshot)],
            text=True,
            env=env,
        )
        (evidence / "forge-client-screenshot-identify.txt").write_text(
            identify,
            encoding="utf-8",
        )

        # Keep the rendered world alive briefly after acceptance so client-side
        # render/event work has time to execute before the clean close.
        time.sleep(3.0)
        subprocess.run(
            [
                "xdotool",
                "windowactivate",
                "--sync",
                window_id,
                "key",
                "--clearmodifiers",
                "alt+F4",
            ],
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
        if " joined the game" not in joined:
            raise RuntimeError("integrated player join proof missing")
        if (
            "Vulkan push broad-phase sustained: 10 consecutive verified batches completed"
            not in joined
        ):
            raise RuntimeError("sustained integrated-server Vulkan proof missing")

        print(
            "[HMT-CLIENT-QA] real Forge client + integrated-server rendered-world/Vulkan gate PASSED",
            flush=True,
        )
        return 0
    except Exception:
        (evidence / "harness-error.txt").write_text(
            traceback.format_exc(),
            encoding="utf-8",
        )
        raise
    finally:
        if proc is not None and proc.poll() is None:
            proc.terminate()
            try:
                proc.wait(timeout=20)
            except subprocess.TimeoutExpired:
                proc.kill()
                proc.wait(timeout=10)
        if wm is not None and wm.poll() is None:
            wm.terminate()
            try:
                wm.wait(timeout=10)
            except subprocess.TimeoutExpired:
                wm.kill()
                wm.wait(timeout=5)
        if wm_log is not None:
            wm_log.close()
        if xvfb.poll() is None:
            xvfb.terminate()
            try:
                xvfb.wait(timeout=10)
            except subprocess.TimeoutExpired:
                xvfb.kill()
                xvfb.wait(timeout=5)

        # Preserve evidence on success and failure so the first causal boundary is
        # always recoverable without rerunning a heavyweight native client gate.
        (evidence / "forge-client-console.log").write_text(
            "".join(lines),
            encoding="utf-8",
        )
        log_path = forge_run / "logs" / "latest.log"
        if log_path.is_file():
            shutil.copy2(log_path, evidence / "forge-client-latest.log")


if __name__ == "__main__":
    sys.exit(main())
