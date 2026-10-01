#!/usr/bin/env python3
"""Real packaged Forge production-client QA for Hari 2.4.

This intentionally launches the installed Forge profile through PortableMC's normal
production launcher path. It rejects forgeclientuserdev and requires a real integrated
world plus a rendered Minecraft window from the exact packaged Hari JAR.
"""
from __future__ import annotations

import argparse
import gzip
import struct
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
    "Validation Error:",
    "VUID-",
    "Unknown function hmtqa:",
    "Unknown function 'hmtqa:",
    "SYNC-HAZARD-",
    "HARI_QA_DIAGNOSTIC_THREAD_SNAPSHOT_COMPLETE",
    "Decompilation failed",
    "Exception ticking world",
    "Async entity unload",
    "Async entity load",
    "Off-thread world random access",
    "A fatal error has been detected by the Java Runtime Environment",
    "Timed out trying to setup the Game Window",
    "Failed to initialize graphics window with current settings",
)
READY_TIMEOUT = 1200.0


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


def terrain_fraction(path: Path, sky_color: str, env: dict[str, str]) -> float:
    """Count real scene pixels rather than a small tree against uniform sky."""
    convert = shutil.which("magick") or shutil.which("convert")
    if convert is None:
        raise RuntimeError("ImageMagick is required")
    return float(subprocess.check_output(
        [convert, str(path), "-alpha", "on", "-fuzz", "4%", "-transparent",
         sky_color, "-alpha", "extract", "-format", "%[fx:mean]", "info:"],
        text=True, env=env, timeout=10).strip())


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
    p.add_argument("--snapshot-and-stop", action="store_true", help="Diagnostic run only: checkpoint a live JVM dump after 180 seconds")
    args = p.parse_args()

    pmc = args.portablemc.resolve()
    main_dir = args.main_dir.resolve()
    mc_dir = args.mc_dir.resolve()
    evidence = args.evidence.resolve()
    evidence.mkdir(parents=True, exist_ok=True)
    frame_report = mc_dir / "harimt-frame-sample.json"
    frame_report.unlink(missing_ok=True)
    runtime_log = mc_dir / "logs/latest.log"
    previous_crashes = {p.name: (p.stat().st_mtime_ns, p.stat().st_size) for p in (mc_dir / "crash-reports").glob("*.txt")}
    if runtime_log.is_file():
        shutil.copyfile(runtime_log, evidence / "previous-latest.log")
        runtime_log.unlink()

    # Disposable generated fixture only. Strictly locate its existing NBT byte tag.
    level = mc_dir / "saves" / args.world / "level.dat"
    payload = bytearray(gzip.decompress(level.read_bytes()))
    name = b"allowCommands"
    needle = b"\x01" + struct.pack(">H", len(name)) + name
    if payload.count(needle) != 1:
        raise RuntimeError("QA fixture has ambiguous/missing allowCommands NBT")
    offset = payload.index(needle) + len(needle)
    if payload[offset] not in (0, 1):
        raise RuntimeError("QA fixture command permission is not a boolean byte")
    payload[offset] = 1
    level.write_bytes(gzip.compress(payload, mtime=0))

    # Real server-side execute predicates avoid the chat packet's length limit.
    # Check all 49 chunks within radius three, rather than only the arrival chunk.
    # These queries do not load chunks or generate fake completion markers.
    qa_pack = level.parent / "datapacks" / "harimt-terrain-qa"
    functions = qa_pack / "data" / "hmtqa" / "functions"
    functions.mkdir(parents=True, exist_ok=True)
    (qa_pack / "pack.mcmeta").write_text(json.dumps({"pack": {
        "pack_format": 15, "description": "Hari 1.20.1 native terrain readiness QA"}}) + "\n")
    function_proof = []
    for x, name, marker in ((4096, "far_ready", "HMT_QA_FAR_GENERATED"),
                            (0, "return_ready", "HMT_QA_RETURN_RENDERED")):
        positions = [(x + dx * 16, x + dz * 16) for dx in range(-3, 4) for dz in range(-3, 4)]
        condition = f"execute if entity @s[x={x-1},y=99,z={x-1},dx=2,dy=2,dz=2] "
        condition += " ".join(f"if loaded {cx} 80 {cz}" for cx, cz in positions)
        condition += f" run say {marker}\n"
        (functions / f"{name}.mcfunction").write_text(condition)
        function_proof.append({"function": f"hmtqa:{name}", "chunk_positions": positions,
                               "required_loaded_chunks": len(positions), "command": condition.strip()})
    (evidence / "terrain-readiness-functions.json").write_text(json.dumps(function_proof, indent=2) + "\n")

    env = os.environ.copy()
    env.update(
        {
            "DISPLAY": args.display,
            "LIBGL_ALWAYS_SOFTWARE": "1",
            "ALSOFT_DRIVERS": "null",
            "VK_INSTANCE_LAYERS": "VK_LAYER_KHRONOS_validation",
            "VK_LAYER_ENABLES": "VK_VALIDATION_FEATURE_ENABLE_SYNCHRONIZATION_VALIDATION_EXT",
            "VK_LOADER_DEBUG": "layer",
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

    (evidence / "validation-environment.json").write_text(json.dumps({k: v for k, v in env.items() if k.startswith("VK_")}, indent=2) + "\n")

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
            "--jvm-arg=-Dharimt.qa.performance=true",
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

        source_lock = threading.Lock()
        stdout_lines: list[str] = []
        def record(line: str) -> None:
            with source_lock:
                lines.append(line)
                events.put((len(lines) - 1, line))

        def reader() -> None:
            assert proc is not None and proc.stdout is not None
            for line in proc.stdout:
                print(line, end="", flush=True)
                stdout_lines.append(line)
                record(line)

        def read_runtime_log() -> None:
            # C2ME Forge can reconfigure client console appenders. The fresh Forge
            # file remains authoritative; preserve and inspect it without changing
            # its logger settings or muting any diagnostics.
            cursor = 0
            while True:
                if runtime_log.is_file():
                    rows = runtime_log.read_text(encoding="utf-8", errors="replace").splitlines(keepends=True)
                    if rows and not rows[-1].endswith("\n"):
                        rows.pop()
                    if len(rows) < cursor:
                        cursor = 0
                    for row in rows[cursor:]:
                        record(row)
                    cursor = len(rows)
                if proc is None or proc.poll() is not None:
                    return
                time.sleep(0.15)

        thread = threading.Thread(target=reader, name="hari24-production-reader", daemon=True)
        file_thread = threading.Thread(target=read_runtime_log, name="hari24-forge-log-reader", daemon=True)
        thread.start()
        file_thread.start()

        def capture_thread_state() -> None:
            # Preserve evidence while the JVM is still alive, including slow/hung
            # entity barriers. Do not interrupt or disable the tested workload.
            deadline = time.monotonic() + 180
            while time.monotonic() < deadline:
                if proc is None or proc.poll() is not None:
                    return
                time.sleep(1)
            jcmd = Path(args.java).parent / "jcmd"
            if not jcmd.is_file() or proc is None or proc.poll() is not None:
                return
            try:
                table = subprocess.check_output(["ps", "-eo", "pid=,ppid=,comm="], text=True, timeout=10)
                records = [row.split() for row in table.splitlines()]
                family = {proc.pid}
                changed = True
                while changed:
                    changed = False
                    for pid, ppid, command in records:
                        if int(ppid) in family and int(pid) not in family:
                            family.add(int(pid)); changed = True
                for pid, ppid, command in records:
                    if int(pid) in family and command == "java":
                        state = subprocess.run([str(jcmd), pid, "Thread.print", "-l"], text=True,
                                               capture_output=True, timeout=30)
                        (evidence / f"jvm-thread-state-{pid}.txt").write_text(state.stdout + state.stderr, encoding="utf-8")
            except Exception as diagnostic_failure:
                (evidence / "thread-capture-error.txt").write_text(repr(diagnostic_failure), encoding="utf-8")
            if args.snapshot_and_stop:
                record("HARI_QA_DIAGNOSTIC_THREAD_SNAPSHOT_COMPLETE\n")

        threading.Thread(target=capture_thread_state, name="hari24-thread-observer", daemon=True).start()

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
        if args.expect == "vulkan":
            regions, copies = frames.get("upload_regions", 0), frames.get("upload_copy_commands", 0)
            if not (0 < copies <= regions):
                raise RuntimeError("native Vulkan uploads did not record ordered region copies")
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
            for index, line in enumerate(snapshot):
                if "[ERROR]" not in line and "/ERROR]" not in line:
                    continue
                offline_401 = "com.mojang.authlib.exceptions.InvalidCredentialsException: Status: 401"
                if offline_401 in line or (line.strip().endswith("Failed to verify authentication")
                        and any(offline_401 in row for row in snapshot[index + 1:index + 12])):
                    classification = "offline-launcher authentication diagnostic (exact HTTP 401 cause)"
                elif "Mod mixin into Embeddium internals detected. This instance is now tainted." in line:
                    classification = "Embeddium support notice for retained Hari GPU bridge"
                else:
                    raise RuntimeError("unexpected production error: " + line.strip())
                records.append({"classification": classification, "line": line.strip()})
            (evidence / "error-ledger.json").write_text(json.dumps(records, indent=2) + "\n", encoding="utf-8")

        # GLFW receives events only when this software-rendered client pumps its
        # next frame. The validation fixture takes ~0.7 seconds per frame, so a
        # fixed 0.2-second chat-opening delay could type before ChatScreen exists.
        # Keep commands in the real client chat path and pace input by measured
        # frames; retain the typed command screenshot before pressing Enter.
        input_settle = max(1.0, min(5.0, 3 * sorted(samples)[284] / 1000))
        command_journal: list[dict[str, object]] = []

        def game_command(command: str) -> None:
            subprocess.run(["xdotool", "windowactivate", "--sync", wid, "key", "--clearmodifiers", "t"], env=env, check=True, timeout=15)
            time.sleep(input_settle)
            subprocess.run(["xdotool", "type", "--clearmodifiers", "--delay", "30", command], env=env, check=True, timeout=15)
            time.sleep(input_settle)
            entry = {"command": command, "input_settle_seconds": input_settle,
                     "screenshot": f"command-{len(command_journal):03d}.png"}
            subprocess.run(["import", "-display", args.display, "-window", wid,
                            str(evidence / str(entry["screenshot"]))], env=env, check=True, timeout=20)
            command_journal.append(entry)
            (evidence / "command-journal.json").write_text(json.dumps(command_journal, indent=2) + "\n")
            subprocess.run(["xdotool", "key", "--clearmodifiers", "Return"], env=env, check=True, timeout=15)
            time.sleep(input_settle)

        command_file = mc_dir / "harimt-qa-command.txt"
        audio_reloads: list[dict[str, object]] = []
        for cycle in range(3):
            cursor = len(lines)
            game_command("/playsound minecraft:music.menu master @s ~ ~ ~ 1 1")
            wait_for("Played sound minecraft:music.menu to HariProdQA", timeout=30, start_at=cursor)
            cursor = len(lines)
            command_file.write_text("reload\n", encoding="utf-8")
            wait_for("[Hari/QA] resource reload completed and world rendered", start_at=cursor)
            shot = evidence / f"after-resource-reload-{cycle + 1}.png"
            subprocess.run(["import", "-display", args.display, "-window", wid, str(shot)],
                           env=env, check=True, timeout=20)
            error_ledger(list(lines))
            audio_reloads.append({"cycle": cycle + 1, "sound": "minecraft:music.menu", "sound_command_acknowledged": True,
                                  "resource_reload_completed": True, "screenshot": shot.name, "unexpected_errors": 0})
            (evidence / "audio-reload-cycles.json").write_text(json.dumps(audio_reloads, indent=2) + "\n")
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

        cursor = len(lines)
        game_command("/reload")
        wait_for("Reloading!", timeout=30, start_at=cursor)
        cursor = len(lines)
        game_command("/gamemode spectator @s")
        wait_for("Set own game mode to Spectator Mode", timeout=30, start_at=cursor)
        subprocess.run(["xdotool", "key", "--clearmodifiers", "F1"], env=env, check=True, timeout=15)
        for x, marker in ((4096, "HMT_QA_FAR_GENERATED"), (0, "HMT_QA_RETURN_RENDERED")):
            cursor = len(lines)
            game_command(f"/tp @s {x} 100 {x} 0 60")
            wait_for("Teleported HariProdQA to", timeout=30, start_at=cursor)
            deadline = time.monotonic() + 180
            while True:
                game_command(f"/function hmtqa:{'far_ready' if x else 'return_ready'}")
                try:
                    wait_for(marker, timeout=5, start_at=cursor)
                    break
                except TimeoutError:
                    if time.monotonic() >= deadline:
                        raise RuntimeError(f"C2ME did not finish chunk travel: {marker}")
            travel_shot = evidence / f"chunk-travel-{x}.png"
            terrain_shot = evidence / f"chunk-travel-{x}-terrain.png"
            terrain_checks: list[dict[str, object]] = []
            stable_terrain = 0
            sky_color: str | None = None
            progression = evidence / f"chunk-travel-{x}-progression"
            progression.mkdir()
            render_deadline = time.monotonic() + 120
            while True:
                subprocess.run(["import", "-display", args.display, "-window", wid, str(travel_shot)], env=env, check=True, timeout=20)
                # Chat and the command bar can satisfy whole-window contrast even
                # when terrain has not arrived yet. Inspect only the central world
                # viewport above those overlays and retain every readiness sample.
                convert = shutil.which("magick") or shutil.which("convert")
                if convert is None:
                    raise RuntimeError("ImageMagick is required")
                if sky_color is None:
                    sky_color = subprocess.check_output(
                        [convert, str(travel_shot), "-format", "%[pixel:p{0,0}]", "info:"],
                        env=env, text=True, timeout=10).strip()
                subprocess.run([convert, str(travel_shot), "-crop", "800x350+240+200", "+repage", str(terrain_shot)], env=env, check=True, timeout=20)
                width, height, colors, stddev = image_metrics(terrain_shot, env)
                coverage = terrain_fraction(terrain_shot, sky_color, env)
                ready = (width, height) == (800, 350) and colors >= 64 and stddev >= 0.05 and coverage >= 0.60
                stable_terrain = stable_terrain + 1 if ready else 0
                frame_name = f"frame-{len(terrain_checks):03d}.png"
                shutil.copyfile(travel_shot, progression / frame_name)
                terrain_checks.append({"width": width, "height": height, "colors": colors,
                                       "gray_stddev": stddev, "non_sky_fraction": coverage,
                                       "sky_reference": sky_color, "ready": ready,
                                       "consecutive_ready_samples": stable_terrain,
                                       "screenshot": f"{progression.name}/{frame_name}"})
                (evidence / f"chunk-travel-{x}-readiness.json").write_text(json.dumps(terrain_checks, indent=2) + "\n")
                if stable_terrain >= 3:
                    break
                for line in list(lines)[cursor:]:
                    inspect(line, marker)
                if time.monotonic() >= render_deadline:
                    raise RuntimeError("C2ME chunk travel failed to render terrain while looking down, HUD hidden")
                time.sleep(2)
        subprocess.run(["xdotool", "key", "--clearmodifiers", "F1"], env=env, check=True, timeout=15)
        error_ledger(list(lines))
        if args.tick_fault:
            cursor = len(lines)
            command_file.write_text("tick-fault\n", encoding="utf-8")
            wait_for("HARI_QA_INTENTIONAL_CLIENT_TICK_FAULT", start_at=cursor)
            proc.wait(timeout=120)
            thread.join(timeout=5)
            file_thread.join(timeout=5)
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
        file_thread.join(timeout=5)
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
        for report in (mc_dir / "crash-reports").glob("*.txt"):
            if previous_crashes.get(report.name) != (report.stat().st_mtime_ns, report.stat().st_size):
                destination = evidence / "runtime-crash-reports" / report.name
                destination.parent.mkdir(exist_ok=True)
                shutil.copyfile(report, destination)
        if runtime_log.is_file():
            shutil.copyfile(runtime_log, evidence / "forge-latest.log")
        if 'stdout_lines' in locals():
            (evidence / "launcher-stdout.log").write_text("".join(stdout_lines), encoding="utf-8")
        (evidence / "production-client-console.log").write_text("".join(lines), encoding="utf-8")


if __name__ == "__main__":
    raise SystemExit(main())
