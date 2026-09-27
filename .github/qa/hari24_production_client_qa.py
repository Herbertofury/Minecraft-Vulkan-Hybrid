#!/usr/bin/env python3
"""Real packaged Forge production-client QA for Hari 2.4.

This intentionally launches the installed Forge profile through PortableMC's normal
production launcher path. It rejects forgeclientuserdev and requires a real integrated
world plus a rendered Minecraft window from the exact packaged Hari JAR.
"""
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

FATAL = (
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
    args = p.parse_args()

    pmc = args.portablemc.resolve()
    main_dir = args.main_dir.resolve()
    mc_dir = args.mc_dir.resolve()
    evidence = args.evidence.resolve()
    evidence.mkdir(parents=True, exist_ok=True)

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
        ["Xvfb", args.display, "-screen", "0", "1280x720x24", "-nolisten", "tcp"],
        stdout=subprocess.DEVNULL,
        stderr=subprocess.PIPE,
        text=True,
        env=env,
    )
    wm = None
    wm_log = None
    proc = None
    lines: list[str] = []
    events: queue.Queue[str] = queue.Queue()

    try:
        wait_x(args.display, env)
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
                events.put(line)

        thread = threading.Thread(target=reader, name="hari24-production-reader", daemon=True)
        thread.start()

        def inspect(line: str, marker: str) -> None:
            for fatal in FATAL:
                if fatal in line:
                    raise RuntimeError(f"production client failed waiting for {marker!r}: {fatal}")
            if "forgeclientuserdev" in line:
                raise RuntimeError("production gate accidentally launched forgeclientuserdev")
            if "renderer=OPENGL_FALLBACK" in line:
                raise RuntimeError("clean production Vulkan lane unexpectedly selected OpenGL fallback")

        def wait_for(marker: str, timeout: float = READY_TIMEOUT) -> None:
            for line in list(lines):
                inspect(line, marker)
                if marker in line:
                    return
            deadline = time.monotonic() + timeout
            while time.monotonic() < deadline:
                if proc is not None and proc.poll() is not None and events.empty():
                    raise RuntimeError(f"PortableMC/Minecraft exited waiting for {marker!r}")
                try:
                    line = events.get(timeout=min(1.0, max(0.05, deadline - time.monotonic())))
                except queue.Empty:
                    continue
                inspect(line, marker)
                if marker in line:
                    return
            raise TimeoutError(f"timed out waiting for {marker!r}")

        wait_for("--launchTarget, forgeclient")
        wait_for("[Hari/Vulkan] renderer=VULKAN")
        wait_for("Hari 2.4 selected merged Vulkan renderer:")
        wait_for("VulkanMod: WindowMixin initialization finished.")
        wait_for("VulkanMod: RenderSystemMixin.initRenderer called.")
        wait_for("Selected Vulkan device:")
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

        shot = evidence / "forge-production-client.png"
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

        joined = "".join(lines)
        required = (
            "--launchTarget, forgeclient",
            "[Hari/Vulkan] renderer=VULKAN",
            "Hari 2.4 selected merged Vulkan renderer:",
            "VulkanMod: WindowMixin initialization finished.",
            "VulkanMod: RenderSystemMixin.initRenderer called.",
            "Selected Vulkan device:",
            " joined the game",
            "Compile-checked Vulkan collision backend initialized on",
            "Vulkan push broad-phase sustained: 10 consecutive verified batches completed",
        )
        for marker in required:
            if marker not in joined:
                raise RuntimeError(f"missing production marker {marker!r}")
        for fatal in FATAL:
            if fatal in joined:
                raise RuntimeError(f"fatal packaged-client marker found: {fatal}")
        if "forgeclientuserdev" in joined:
            raise RuntimeError("mapped userdev launcher appeared in production log")

        print("[HARI24-QA] packaged Forge production-client gate PASSED", flush=True)
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
