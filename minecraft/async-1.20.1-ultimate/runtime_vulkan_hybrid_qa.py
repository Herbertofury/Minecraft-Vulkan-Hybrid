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
    "ModLoadingException",
    "OutOfMemoryError",
    "VK_ERROR_DEVICE_LOST",
    "UnsatisfiedLinkError",
    "A fatal error has been detected by the Java Runtime Environment",
    "Failed to create Vulkan instance",
    "No Vulkan-capable GPU was detected",
    "Cannot acquire next swap chain image",
    "Failed to present rendered frame",
)

MIN_RENDER_WIDTH = 800
MIN_RENDER_HEIGHT = 450
MIN_RENDER_COLORS = 64
MIN_GRAY_STDDEV = 0.050
RENDER_READY_TIMEOUT = 90.0


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
        raise RuntimeError("ImageMagick magick/convert is required for screenshot QA")
    stddev = float(
        subprocess.check_output(
            [convert, str(path), "-colorspace", "Gray", "-format", "%[fx:standard_deviation]", "info:"],
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
    return ready, (
        f"{width}x{height}, colors={colors}, grayStdDev={stddev:.6f}, "
        f"bytes={path.stat().st_size}"
    )


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("project_root", type=Path)
    parser.add_argument("server_world", type=Path)
    parser.add_argument("evidence_dir", type=Path)
    parser.add_argument("--expect", choices=("vulkan", "opengl"), required=True)
    parser.add_argument("--display", default=":99")
    parser.add_argument("--world-name", default="HMT-2.4-QA")
    parser.add_argument("--extra-marker", action="append", default=[])
    args = parser.parse_args()

    root = args.project_root.resolve()
    server_world = args.server_world.resolve()
    evidence = args.evidence_dir.resolve()
    forge_run = root / "forge" / "run"
    saves = forge_run / "saves"
    qa_world = saves / args.world_name
    evidence.mkdir(parents=True, exist_ok=True)
    saves.mkdir(parents=True, exist_ok=True)

    if not server_world.is_dir():
        raise SystemExit(f"server QA world missing: {server_world}")
    if qa_world.exists():
        shutil.rmtree(qa_world)
    shutil.copytree(server_world, qa_world)

    # Force every lane to make a fresh renderer decision. The production cache remains
    # enabled by default; QA must not inherit the previous lane's cached decision.
    compat_cache = forge_run / "config" / "harimt-vulkan-compat.properties"
    compat_cache.unlink(missing_ok=True)

    env = os.environ.copy()
    env.update(
        {
            "DISPLAY": args.display,
            "LIBGL_ALWAYS_SOFTWARE": "1",
            "ALSOFT_DRIVERS": "null",
        }
    )
    java_opts = [env.get("JAVA_TOOL_OPTIONS", "").strip(), "-Dharimt.vulkan.compatCache=false"]
    if args.expect == "vulkan":
        # CI uses Mesa llvmpipe/lavapipe. Allow Hari's collision backend to use
        # that CPU Vulkan device so the same integrated-client JVM proves both
        # the renderer and collision pipeline without changing user defaults.
        java_opts.append("-Dharimt.vulkan.allowCpuDevice=true")
    env["JAVA_TOOL_OPTIONS"] = " ".join(x for x in java_opts if x)

    if args.expect == "vulkan":
        # Deterministic CPU Vulkan in GitHub Actions. User systems are not forced to
        # lavapipe; they continue to use VulkanMod's normal physical-device selection.
        for candidate in (
            Path("/usr/share/vulkan/icd.d/lvp_icd.x86_64.json"),
            Path("/usr/share/vulkan/icd.d/lvp_icd.i686.json"),
        ):
            if candidate.is_file():
                env["VK_DRIVER_FILES"] = str(candidate)
                env["VK_ICD_FILENAMES"] = str(candidate)
                break

    xvfb = subprocess.Popen(
        ["Xvfb", args.display, "-screen", "0", "1280x720x24", "-nolisten", "tcp"],
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
        wait_x(args.display)
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
            f"--args=--width 1280 --height 720 --quickPlaySingleplayer {args.world_name}",
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

        thread = threading.Thread(target=reader, name="hari24-client-qa-reader", daemon=True)
        thread.start()

        def inspect_line(line: str, waiting_for: str) -> None:
            for pattern in FATAL_PATTERNS:
                if pattern in line:
                    raise RuntimeError(
                        f"client/runtime failed while waiting for {waiting_for!r}: observed {pattern!r}"
                    )
            if args.expect == "vulkan" and "renderer=OPENGL_FALLBACK" in line:
                raise RuntimeError("Vulkan lane unexpectedly selected OpenGL fallback")
            if args.expect == "opengl" and "renderer=VULKAN" in line:
                raise RuntimeError("OpenGL fallback lane unexpectedly selected Vulkan")

        def wait_for(marker: str, timeout: float) -> None:
            for recorded in list(lines):
                inspect_line(recorded, marker)
                if marker in recorded:
                    print(f"[HARI24-QA] marker (recorded): {marker}", flush=True)
                    return
            deadline = time.monotonic() + timeout
            while time.monotonic() < deadline:
                if proc is not None and proc.poll() is not None and events.empty():
                    raise RuntimeError(f"client exited while waiting for marker: {marker}")
                remaining = max(0.05, min(1.0, deadline - time.monotonic()))
                try:
                    line = events.get(timeout=remaining)
                except queue.Empty:
                    continue
                inspect_line(line, marker)
                if marker in line:
                    print(f"[HARI24-QA] marker: {marker}", flush=True)
                    return
            raise TimeoutError(f"timed out waiting for client marker: {marker}")

        if args.expect == "vulkan":
            wait_for("[Hari/Vulkan] renderer=VULKAN", 300)
            wait_for("Hari 2.4 selected merged Vulkan renderer:", 300)
            wait_for("Selected Vulkan device:", 300)
        else:
            wait_for("[Hari/Vulkan] renderer=OPENGL_FALLBACK", 300)
            wait_for("Hari 2.4 selected OpenGL compatibility renderer:", 300)

        for marker in args.extra_marker:
            wait_for(marker, 300)
        wait_for(" joined the game", 300)

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

        screenshot = evidence / f"forge-client-{args.expect}.png"
        candidate = evidence / "forge-client-candidate.png"
        metrics_log: list[str] = []
        deadline = time.monotonic() + RENDER_READY_TIMEOUT
        attempt = 0
        while time.monotonic() < deadline:
            attempt += 1
            if proc.poll() is not None:
                raise RuntimeError("Forge client exited before a rendered-world frame was captured")
            subprocess.run(
                ["import", "-display", args.display, "-window", window_id, str(candidate)],
                env=env,
                check=True,
                stdout=subprocess.DEVNULL,
                stderr=subprocess.DEVNULL,
                timeout=20,
            )
            ready, metric_text = rendered_world_ready(candidate, env)
            entry = f"attempt={attempt} ready={str(ready).lower()} {metric_text}"
            metrics_log.append(entry)
            print(f"[HARI24-QA] rendered-world probe: {entry}", flush=True)
            if ready:
                candidate.replace(screenshot)
                break
            time.sleep(1.0)
        else:
            if candidate.is_file():
                shutil.copy2(candidate, evidence / "forge-client-last-unready-frame.png")
            raise TimeoutError("client joined but never produced a rendered-world frame")
        candidate.unlink(missing_ok=True)
        (evidence / "forge-client-render-readiness.txt").write_text(
            "\n".join(metrics_log) + "\n", encoding="utf-8"
        )

        time.sleep(3.0)
        subprocess.run(
            ["xdotool", "windowactivate", "--sync", window_id, "key", "--clearmodifiers", "alt+F4"],
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
        if args.expect == "vulkan":
            if "[Hari/Vulkan] renderer=VULKAN" not in joined:
                raise RuntimeError("Vulkan renderer-selection proof missing")
            if "Selected Vulkan device:" not in joined:
                raise RuntimeError("Vulkan device-selection proof missing")
            if "renderer=OPENGL_FALLBACK" in joined:
                raise RuntimeError("Vulkan lane fell back to OpenGL")
        else:
            if "[Hari/Vulkan] renderer=OPENGL_FALLBACK" not in joined:
                raise RuntimeError("OpenGL fallback proof missing")
            if "renderer=VULKAN" in joined:
                raise RuntimeError("OpenGL lane unexpectedly enabled Vulkan")

        print(f"[HARI24-QA] real Forge client {args.expect} rendered-world gate PASSED", flush=True)
        return 0
    except Exception:
        (evidence / "harness-error.txt").write_text(traceback.format_exc(), encoding="utf-8")
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

        (evidence / "forge-client-console.log").write_text("".join(lines), encoding="utf-8")
        log_path = forge_run / "logs" / "latest.log"
        if log_path.is_file():
            shutil.copy2(log_path, evidence / "forge-client-latest.log")


if __name__ == "__main__":
    sys.exit(main())
