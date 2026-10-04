#!/usr/bin/env python3
"""Exploratory production FPS/JFR capture using the accepted native actor.

Performance disables instrumentation-heavy Vulkan validation on both variants;
the separate correctness lane retains every validation check. No game work changes.
This short startup sample identifies bottlenecks; it is not the final A/B benchmark.
"""
from pathlib import Path

actor = Path(__file__).with_name("hari249_current_validation_qa.py")
source = actor.read_text()

def replace_once(old, new):
    global source
    assert source.count(old) == 1, old
    source = source.replace(old, new)

replace_once('"VK_INSTANCE_LAYERS": "VK_LAYER_KHRONOS_validation",', '"VK_INSTANCE_LAYERS": "",')
replace_once('"VK_LAYER_ENABLES": "VK_VALIDATION_FEATURE_ENABLE_SYNCHRONIZATION_VALIDATION_EXT",', '"VK_LAYER_ENABLES": "",')
replace_once('"--jvm-arg=-Dharimt.qa.captureFrames=true",', '''"--jvm-arg=-XX:StartFlightRecording=filename=" + str(evidence / "minecraft.jfr"),
            "--jvm-arg=-Dharimt.qa.captureFrames=true",''')
start = source.index('        if args.expect == "vulkan":\n            expected_library =')
end = source.index('\n        shot =', start)
source = source[:start] + source[end:]
branch = '''        import hashlib
        table = [row.split() for row in subprocess.check_output(["ps", "-eo", "pid=,ppid=,comm="], text=True).splitlines()]
        family = {proc.pid}
        changed = True
        while changed:
            changed = False
            for pid, ppid, command in table:
                if int(ppid) in family and int(pid) not in family:
                    family.add(int(pid)); changed = True
        pids = [int(pid) for pid, ppid, command in table if int(pid) in family and command == "java"]
        if len(pids) != 1: raise RuntimeError("Benchmark needs exactly one original Minecraft JVM")
        pid = pids[0]
        maps = Path(f"/proc/{pid}/maps").read_text()
        if "libVkLayer_khronos_validation" in maps:
            raise RuntimeError("Performance baseline unexpectedly loaded validation")
        (evidence / "native-process-maps.txt").write_text(maps)
        (evidence / "native-process-status.txt").write_text(Path(f"/proc/{pid}/status").read_text())
        (evidence / "options-measured.txt").write_text(options_path.read_text())
        subprocess.run(["xdotool", "windowactivate", "--sync", wid, "key", "--clearmodifiers", "alt+F4"], env=env, check=True, timeout=15)
        rc = proc.wait(timeout=120)
        thread.join(timeout=5); file_thread.join(timeout=5)
        if rc != 0: raise RuntimeError(f"Production benchmark client exited {rc}")
        for line in lines: inspect(line, "benchmark completion")
        error_ledger(list(lines))
        if {p.name: (p.stat().st_mtime_ns,p.stat().st_size) for p in (mc_dir / "crash-reports").glob("*.txt")} != previous_crashes:
            raise RuntimeError("New crash report during benchmark")
        jfr = evidence / "minecraft.jfr"
        if not jfr.is_file() or jfr.stat().st_size == 0: raise RuntimeError("Original JVM did not dump JFR")
        slow_count = max(1, math.ceil(len(samples) / 100))
        (evidence / "result.json").write_text(json.dumps({
            "schema":1,"status":"exploratory_baseline_pass","native_jvm_pid":pid,
            "validation_layers_loaded":False,"jfr_profiled":True,
            "performance_scope":"Linux software renderer; startup 300-frame exploration, not final A/B",
            "jar_sha256":hashlib.sha256((mc_dir / "mods/HariMultiThread-Ultimate.jar").read_bytes()).hexdigest(),
            "fps":1000 / (sum(samples) / len(samples)),
            "one_percent_low_fps":1000 / (sum(sorted(samples)[-slow_count:]) / slow_count),
            "frame_p95_ms":sorted(samples)[math.ceil(len(samples)*0.95)-1],
            "scene_screenshot":shot.name,"normal_exit":True},indent=2)+"\\n")
        print("[HARI-FPS] exploratory production baseline passed",flush=True)
        return 0

'''
replace_once('        input_settle =', branch + '        input_settle =')
compile(source, str(actor), "exec")
exec(compile(source, str(actor), "exec"), {"__name__":"__main__", "__file__":str(actor)})
