#!/usr/bin/env python3
"""Real two-JVM dedicated-server dimension, mod AI, unload and NBT acceptance."""
from __future__ import annotations
import argparse
import json
import queue
import shutil
import subprocess
import threading
import time
import traceback
from pathlib import Path
from hari24_dimension_fixture_diagnostic import install

FATAL = ("Exception ticking world", "Off-thread world random access", "Decompilation failed",
         "MixinApplyError", "InvalidMixinException", "NoSuchMethodError", "NoClassDefFoundError",
         "AbstractMethodError", "UnsatisfiedLinkError", "A fatal error has been detected")

def boot(server: Path, evidence: Path, scenes: list[dict], reopen: bool):
    phase = "restart" if reopen else "first"
    rows = []
    events = queue.Queue()
    proc = subprocess.Popen(["./run.sh", "nogui"], cwd=server, stdin=subprocess.PIPE,
                            stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True, bufsize=1)
    def reader():
        for row in proc.stdout:
            rows.append(row); events.put((len(rows)-1, row)); print(row, end="", flush=True)
    thread = threading.Thread(target=reader, daemon=True); thread.start()
    def inspect(row):
        if any(f in row for f in FATAL) or "/ERROR]" in row or "[ERROR]" in row:
            raise RuntimeError("Unclassified dedicated-server error: " + row.strip())
    def wait(marker, start=0, timeout=900):
        for row in list(rows)[start:]:
            inspect(row)
            if marker in row: return
        deadline = time.monotonic() + timeout
        while time.monotonic() < deadline:
            if proc.poll() is not None and events.empty():
                raise RuntimeError("Server exited before " + marker)
            try: index, row = events.get(timeout=1)
            except queue.Empty: continue
            inspect(row)
            if index >= start and marker in row: return
        raise TimeoutError("Server predicate did not complete: " + marker)
    def command(text):
        cursor = len(rows)
        proc.stdin.write(text + "\n"); proc.stdin.flush()
        return cursor
    def predicate(text, marker, timeout=45):
        deadline = time.monotonic()+timeout
        while time.monotonic()<deadline:
            cursor = command(text)
            try: wait(marker,cursor,timeout=3); return
            except TimeoutError: pass
        raise TimeoutError("Original server state never satisfied " + marker)
    records = []
    try:
        wait("Done (")
        wait("Initialized Pool with 2 threads")
        wait("Enforcing safe world random access")
        wait("externalChunks=true")
        if not reopen:
            command("gamerule doMobSpawning false")
            command("gamerule doDaylightCycle false")
            command("gamerule doWeatherCycle false")
            command("difficulty normal")
            command("function hmtdim:init")
        for scene in scenes:
            ns, dimension = scene["namespace"], scene["dimension"]
            prefix = f"execute in {dimension} run "
            command(prefix + "forceload add -64 -64 64 64")
            conditions = " ".join(f"if loaded {x*16} 80 {z*16}"
                                  for x in range(-3,4) for z in range(-3,4))
            predicate(f"execute in {dimension} {conditions} run say HMT_SERVER_LOADED_{ns}_49",
                      f"HMT_SERVER_LOADED_{ns}_49")
            if not reopen:
                cursor = command(prefix + f"function hmtdim:setup_{ns}")
                wait(f"HMT_DIM_CREATED_{ns}_{scene['expected_entities']}",cursor)
            # Original mob AI runs throughout, including SmartBrainLib and boss idle behavior.
            time.sleep(6)
            command(prefix + f"function hmtdim:audit_{ns}")
            predicate(prefix + f"function hmtdim:verify_{ns}",
                      f"HMT_DIM_VERIFIED_{ns}_{scene['expected_entities']}")
            command(prefix + "forceload remove all")
            predicate(f"execute in {dimension} unless loaded 0 98 0 run say HMT_SERVER_UNLOADED_{ns}",
                      f"HMT_SERVER_UNLOADED_{ns}")
            command(prefix + "forceload add 4080 4080 4112 4112")
            predicate(f"execute in {dimension} if loaded 4096 80 4096 run say HMT_SERVER_FAR_{ns}",
                      f"HMT_SERVER_FAR_{ns}")
            command(prefix + "forceload remove all")
            command(prefix + "forceload add -64 -64 64 64")
            predicate(f"execute in {dimension} {conditions} run say HMT_SERVER_RETURN_{ns}_49",
                      f"HMT_SERVER_RETURN_{ns}_49")
            predicate(prefix + f"function hmtdim:verify_{ns}",
                      f"HMT_DIM_VERIFIED_{ns}_{scene['expected_entities']}")
            records.append({**scene,"reopen":reopen,"exact_mob_catalogue_present":True,
                            "block_nbt_retained":True,"real_unload_return":True})
            (evidence/f"server-{phase}-results.json").write_text(json.dumps(records,indent=2)+"\n")
        cursor=command("save-all flush"); wait("Saved the game",cursor,timeout=180)
        command("stop"); rc=proc.wait(timeout=180);thread.join(timeout=5)
        if rc!=0:raise RuntimeError("Dedicated server failed clean exit: "+str(rc))
        for row in rows:inspect(row)
        # C2ME may route exceptions to the Forge file appender. Inspect the full
        # fresh file as well as stdout before accepting either JVM.
        for row in (server/"logs/latest.log").read_text().splitlines():inspect(row)
        if list((server/"crash-reports").glob("*.txt")):
            raise RuntimeError("Dedicated server wrote a crash report")
        print(f"HMT_DIM_SERVER_{phase.upper()}_PASSED",flush=True)
    finally:
        if proc.poll() is None:
            try:command("stop");proc.wait(timeout=30)
            except Exception:proc.kill();proc.wait(timeout=10)
        thread.join(timeout=5)
        (evidence/f"server-{phase}.log").write_text("".join(rows))
        if (server/"logs/latest.log").is_file():
            shutil.copyfile(server/"logs/latest.log",evidence/f"forge-{phase}.log")
        for report in (server/"crash-reports").glob("*.txt"):
            shutil.copyfile(report,evidence/(phase+"-"+report.name))

def main():
    p=argparse.ArgumentParser();p.add_argument("server",type=Path);p.add_argument("evidence",type=Path)
    a=p.parse_args(); server=a.server.resolve();evidence=a.evidence.resolve();evidence.mkdir(parents=True,exist_ok=True)
    scenes=install(server/"world",server/"mods",evidence)
    if {s['namespace'] for s in scenes}!={'aether','midnight'}:raise RuntimeError("Missing dimension provider")
    try:
        boot(server,evidence,scenes,False);boot(server,evidence,scenes,True)
    except Exception:
        (evidence/"harness-error.txt").write_text(traceback.format_exc());raise

if __name__=="__main__":main()
