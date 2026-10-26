#!/usr/bin/env python3
from pathlib import Path
import sys

if len(sys.argv) != 2:
    raise SystemExit("usage: apply_lifecycle.py <merged-upstream-root>")

root = Path(sys.argv[1]).resolve()
path = root / "common/src/main/java/com/axalotl/async/common/ParallelProcessor.java"
if not path.is_file():
    raise SystemExit(f"missing merged file: {path}")

text = path.read_text(encoding="utf-8")
old = '''        gpuCollisionTicks.reset();
        gpuCollisionSavedMs.reset();
        GpuEntityModule.shutdown();
        TickStats.resetEntityTickStats();'''
new = '''        gpuCollisionTicks.reset();
        gpuCollisionSavedMs.reset();
        GpuEntityModule.shutdown();
        GpuPushBatch.clear();
        currentEntities.set(0);
        mcThreadTracker.clear();
        tickPool = null;
        server = null;
        TickStats.resetEntityTickStats();'''
count = text.count(old)
if count != 1:
    raise SystemExit(f"source drift in ParallelProcessor.stop: expected 1 stop block, found {count}")
path.write_text(text.replace(old, new, 1), encoding="utf-8")
print("HariMultiThread Ultimate lifecycle cleanup applied successfully")
