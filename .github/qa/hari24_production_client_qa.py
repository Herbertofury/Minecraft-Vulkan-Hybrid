#!/usr/bin/env python3
"""Real packaged Forge production-client QA for Hari 2.4.

This intentionally launches the installed Forge profile through PortableMC's normal
production launcher path. It rejects forgeclientuserdev and requires a real integrated
world plus a rendered Minecraft window from the exact packaged Hari JAR.
"""
from __future__ import annotations

import argparse
import json
import math
import os
import queue
import shutil
import subprocess
import sys
import threading
import time
import traceback
from pathlib import Path

FATAL = (
    "minecraft:null.vsh",
    "createLegacyShader FAILED",
    "Error on shader",
    "Cannot create Vulkan shader",
    "Using Vulkan fallback shader",
    "zero-filling",
    "not present in uniform map",
    "Suppressed external failure",
    "Error parsing option value",
    "[Hari/QA] frame capture failed",
    "MixinApplyError",
    "InvalidMixinException",
    "InjectionError",
    "NoClassDefFoundError",
    "AbstractMethodError",
    "NoSuchMethodError",
    "NoSuchFieldError",
    "UnsatisfiedLinkError",
    "VK_ERROR_DEVICE_LOST",
    "A fatal error has been detected by the Java Runtime Environment",
    "Timed out trying to setup the Game Window",
    "Failed to initialize graphics window with current settings",
)
READY_TIMEOUT = 420.0


def wait_x(display: str, env: dict[str, str], timeout: float = 20.0) -> None:
    deadline = time.monotonic() + timeout
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
    width, height, colors = map(
        int,
        subprocess.check_output(
            ["identify", "-format", "%w %h %k", str(path)],
            text=True,
            env=env,
            timeout=10,
        ).split(),
    )
    convert = shutil.which("magick") or shutil.which("convert")
    if convert is None:
        raise RuntimeError("ImageMagick is required")
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


def main() -> int:
    p = argparse.ArgumentParser()
    p.add_argument("--portablemc", type=Path, required=True)
    p.add_argument("--main-dir", type=Path, required=True)
    p.add_argument("--mc-dir", type=Path, required=True)
    p.add_argument("--world", required=True)
    p.add_argument("--evidence", type=Path, required=True)
    p.add_argument("--display", default=":97")
    p.add_argument("--java", default="java")
    p.add_argument("--expect", choices=("vulkan", "opengl"), default="vulkan")
    p.add_argument("--tick-fault", action="store_true")
    args = p.parse_args()

    pmc = args.portablemc.resolve()
    main_dir = args.main_dir.resolve()
    mc_dir = args.mc_dir.resolve()
    evidence = args.evidence.resolve()
    evidence.mkdir(parents=True, exist_ok=True)
    frame_report = mc_dir / "harimt-frame-sample.json"
    frame_report.unlink(missing_ok=True)

    env = os.environ.copy()
    env.update(
        {
            "DISPLAY": args.display,
            "LIBGL_ALWAYS_SOFTWARE": "1",
            "ALSOFT_DRIVERS": "null",
        }
    )
    for candidate in (
        Path("/usr/share/vulkan/icd.d/lvp_icd.x86_64.json"),
        Path("/usr/share/vulkan/icd.d/lvp_icd.i686.json"),
    ):
        if candidate.is_file():
            env["VK_DRIVER_FILES"] = str(candidate)
            env["VK_ICD_FILENAMES"] = str(candidate)
            break

    xvfb = subprocess.Popen(
        # Leave room for window decorations so both client areas remain 1280x720.
        ["Xvfb", args.display, "-screen", "0", "1600x900x24", "-nolisten", "tcp"],
        stdout=subprocess.DEVNULL,
        stderr=subprocess.PIPE,
        text=True,
        env=env,
    )
    wm = None
    wm_log = None
    proc = None
    lines: list[str] = []
    events: queue.Queue[tuple[int, str]] = queue.Queue()

    try:
        wait_x(args.display, env)
        # Warm the software GL driver before Forge's fixed ten-second early
        # window deadline and retain the actual renderer/capability evidence.
        gl = subprocess.run(["glxinfo", "-B"], env=env, text=True, capture_output=True, timeout=60, check=True)
        (evidence / "software-opengl.txt").write_text(gl.stdout + gl.stderr, encoding="utf-8")
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
            raise RuntimeError("Openbox exited before production client launch")

        cmd = [
            str(pmc),
            "--main-dir", str(main_dir),
            "-vv",
            "start",
            "forge::1.20.1-47.4.23",
            "--mc-dir", str(mc_dir),
            "--jvm", args.java,
            "--resolution", "1280x720",
            "--join-world", args.world,
            "-u", "HariProdQA",
            "--jvm-arg=-Dharimt.vulkan.compatCache=false",
            "--jvm-arg=-Dharimt.vulkan.allowCpuDevice=true",
            "--jvm-arg=-Dharimt.qa.captureFrames=true",
            "--jvm-arg=-Dharimt.qa.lifecycle=true",
        ]
        (evidence / "launch-command.txt").write_text(" ".join(cmd) + "\n", encoding="utf-8")
        proc = subprocess.Popen(
            cmd,
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
                events.put((len(lines) - 1, line))

        thread = threading.Thread(target=reader, name="hari24-production-reader", daemon=True)
        thread.start()

        def inspect(line: str, marker: str) -> None:
            for fatal in FATAL:
                if fatal in line:
                    raise RuntimeError(f"production client failed waiting for {marker!r}: {fatal}")
            if "forgeclientuserdev" in line:
                raise RuntimeError("production gate accidentally launched forgeclientuserdev")
            if args.expect == "vulkan" and "renderer=OPENGL_FALLBACK" in line:
                raise RuntimeError("clean production Vulkan lane unexpectedly selected OpenGL fallback")
            if args.expect == "opengl" and "renderer=VULKAN" in line:
                raise RuntimeError("OpenGL compatibility lane unexpectedly selected Vulkan")

        def wait_for(marker: str, timeout: float = READY_TIMEOUT, start_at: int = 0) -> None:
            for line in list(lines)[start_at:]:
                inspect(line, marker)
                if marker in line:
                    return
            deadline = time.monotonic() + timeout
            while time.monotonic() < deadline:
                if proc is not None and proc.poll() is not None and events.empty():
                    raise RuntimeError(f"PortableMC/Minecraft exited waiting for {marker!r}")
                try:
                    index, line = events.get(timeout=min(1.0, max(0.05, deadline - time.monotonic())))
                except queue.Empty:
                    continue
                inspect(line, marker)
                if index >= start_at and marker in line:
                    return
            raise TimeoutError(f"timed out waiting for {marker!r}")

        wait_for("--launchTarget, forgeclient")
        if args.expect == "vulkan":
            wait_for("[Hari/Vulkan] renderer=VULKAN")
            wait_for("Hari 2.4 selected merged Vulkan renderer:")
            wait_for("VulkanMod: WindowMixin initialization finished.")
            wait_for("VulkanMod: RenderSystemMixin.initRenderer called.")
            wait_for("Selected Vulkan device:")
        else:
            wait_for("[Hari/Vulkan] renderer=OPENGL_FALLBACK")
            wait_for("Hari 2.4 selected OpenGL compatibility renderer:")
        wait_for(" joined the game")
        wait_for("Compile-checked Vulkan collision backend initialized on")
        wait_for("Vulkan push broad-phase sustained: 10 consecutive verified batches completed")

        windows = subprocess.run(
            ["xdotool", "search", "--onlyvisible", "--name", "Minecraft"],
            env=env,
            text=True,
            stdout=subprocess.PIPE,
            stderr=subprocess.DEVNULL,
            timeout=10,
        )
        ids = [x.strip() for x in windows.stdout.splitlines() if x.strip()]
        if not ids:
            raise RuntimeError("visible packaged Minecraft window not found")
        wid = ids[-1]
        subprocess.run(["xdotool", "windowactivate", "--sync", wid], env=env, check=True, timeout=15)

        wait_for("[Hari/QA] captured 300 world frames after 120 warmup frames")
        frames = json.loads(frame_report.read_text(encoding="utf-8"))
        expected_renderer = "VULKAN" if args.expect == "vulkan" else "OPENGL_FALLBACK"
        samples = frames.get("frame_ms", [])
        if frames.get("renderer") != expected_renderer or frames.get("sample_count") != 300 or len(samples) != 300:
            raise RuntimeError("frame capture has wrong renderer or sample count")
        if not all(isinstance(x, (float, int)) and math.isfinite(x) and x > 0 for x in samples):
            raise RuntimeError("frame capture contains invalid timing samples")
        if (frames.get("width"), frames.get("height"), frames.get("render_distance")) != (1280, 720, 4):
            raise RuntimeError("frame capture does not match the fixed 1280x720 / distance-4 fixture")
        shutil.copyfile(frame_report, evidence / "frame-sample.json")

        shot = evidence / f"forge-production-client-{args.expect}.png"
        candidate = evidence / "candidate.png"
        deadline = time.monotonic() + 90.0
        metrics: list[str] = []
        while time.monotonic() < deadline:
            subprocess.run(
                ["import", "-display", args.display, "-window", wid, str(candidate)],
                env=env,
                check=True,
                stdout=subprocess.DEVNULL,
                stderr=subprocess.DEVNULL,
                timeout=20,
            )
            width, height, colors, stddev = image_metrics(candidate, env)
            ready = width >= 800 and height >= 450 and colors >= 64 and stddev >= 0.05
            metrics.append(
                f"ready={str(ready).lower()} width={width} height={height} "
                f"colors={colors} grayStdDev={stddev:.6f} bytes={candidate.stat().st_size}"
            )
            if ready:
                candidate.replace(shot)
                break
            time.sleep(1.0)
        else:
            raise TimeoutError("packaged production client never produced a rendered-world frame")
        (evidence / "render-readiness.txt").write_text("\n".join(metrics) + "\n", encoding="utf-8")

        if args.expect == "vulkan":
            wait_for("legacy shader 'forge:rendertype_entity_unlit_translucent'")

        def error_ledger(snapshot: list[str]) -> None:
            records = []
            for line in snapshot:
                if "[ERROR]" not in line and "/ERROR]" not in line:
                    continue
                if "com.mojang.authlib.exceptions.InvalidCredentialsException: Status: 401" in line:
                    classification = "offline-launcher authentication diagnostic"
                elif "Mod mixin into Embeddium internals detected. This instance is now tainted." in line:
                    classification = "Embeddium support notice for retained Hari GPU bridge"
                else:
                    raise RuntimeError("unexpected production error: " + line.strip())
                records.append({"classification": classification, "line": line.strip()})
            (evidence / "error-ledger.json").write_text(json.dumps(records, indent=2) + "\n", encoding="utf-8")

        command_file = mc_dir / "harimt-qa-command.txt"
        cursor = len(lines)
        command_file.write_text("reload\n", encoding="utf-8")
        wait_for("[Hari/QA] resource reload completed and world rendered", start_at=cursor)
        subprocess.run(["import", "-display", args.display, "-window", wid,
                        str(evidence / "after-resource-reload.png")], env=env, check=True, timeout=20)
        for width, height in ((960, 540), (1280, 720)):
            cursor = len(lines)
            subprocess.run(["xdotool", "windowsize", "--sync", wid, str(width), str(height)],
                           env=env, check=True, timeout=20)
            wait_for(f"[Hari/QA] rendered world width={width} height={height}", start_at=cursor)
            resized = evidence / f"after-resize-{width}x{height}.png"
            subprocess.run(["import", "-display", args.display, "-window", wid, str(resized)],
                           env=env, check=True, timeout=20)
            actual_width, actual_height, colors, stddev = image_metrics(resized, env)
            if (actual_width, actual_height) != (width, height) or colors < 64 or stddev < 0.05:
                raise RuntimeError("resized client did not render the real world")

        error_ledger(list(lines))
        if args.tick_fault:
            cursor = len(lines)
            command_file.write_text("tick-fault\n", encoding="utf-8")
            wait_for("HARI_QA_INTENTIONAL_CLIENT_TICK_FAULT", start_at=cursor)
            proc.wait(timeout=120)
            thread.join(timeout=5)
            if not list((mc_dir / "crash-reports").glob("*.txt")):
                raise RuntimeError("injected client tick failure did not create a crash report")
            joined = "".join(lines)
            for fatal in FATAL:
                if fatal in joined:
                    raise RuntimeError(f"unexpected error during intentional tick-fault lane: {fatal}")
            shutil.copytree(mc_dir / "crash-reports", evidence / "intentional-crash-reports", dirs_exist_ok=True)
            print("[HARI24-QA] intentional tick failure surfaced without suppression", flush=True)
            return 0

        subprocess.run(
            ["xdotool", "windowactivate", "--sync", wid, "key", "--clearmodifiers", "alt+F4"],
            env=env,
            check=True,
            timeout=15,
        )
        rc = proc.wait(timeout=120)
        thread.join(timeout=5)
        if rc != 0:
            raise RuntimeError(f"PortableMC production launch exited with code {rc}")

        error_ledger(list(lines))
        joined = "".join(lines)
        required = [
            "--launchTarget, forgeclient",
            " joined the game",
            "Compile-checked Vulkan collision backend initialized on",
            "Vulkan push broad-phase sustained: 10 consecutive verified batches completed",
        ]
        if args.expect == "vulkan":
            required.extend([
                "[Hari/Vulkan] renderer=VULKAN",
                "Hari 2.4 selected merged Vulkan renderer:",
                "VulkanMod: WindowMixin initialization finished.",
                "VulkanMod: RenderSystemMixin.initRenderer called.",
                "Selected Vulkan device:",
            ])
        else:
            required.extend([
                "[Hari/Vulkan] renderer=OPENGL_FALLBACK",
                "Hari 2.4 selected OpenGL compatibility renderer:",
            ])
        for marker in required:
            if marker not in joined:
                raise RuntimeError(f"missing production marker {marker!r}")
        if args.expect == "vulkan" and "renderer=OPENGL_FALLBACK" in joined:
            raise RuntimeError("clean production Vulkan lane fell back to OpenGL")
        if args.expect == "opengl":
            for forbidden in (
                "renderer=VULKAN",
                "VulkanMod: WindowMixin initialization finished.",
                "VulkanMod: RenderSystemMixin.initRenderer called.",
                "Selected Vulkan device:",
            ):
                if forbidden in joined:
                    raise RuntimeError(f"OpenGL compatibility lane activated Vulkan path: {forbidden}")
        for fatal in FATAL:
            if fatal in joined:
                raise RuntimeError(f"fatal packaged-client marker found: {fatal}")
        if "forgeclientuserdev" in joined:
            raise RuntimeError("mapped userdev launcher appeared in production log")

        print(f"[HARI24-QA] packaged Forge production-client {args.expect} gate PASSED", flush=True)
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
        if wm_log is not None:
            wm_log.close()
        if xvfb.poll() is None:
            xvfb.terminate()
            try:
                xvfb.wait(timeout=10)
            except subprocess.TimeoutExpired:
                xvfb.kill()
        (evidence / "production-client-console.log").write_text("".join(lines), encoding="utf-8")


if __name__ == "__main__":
    raise SystemExit(main())
