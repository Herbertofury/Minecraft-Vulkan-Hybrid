#!/usr/bin/env python3
from pathlib import Path
import subprocess
import sys

if len(sys.argv) != 2:
    raise SystemExit("usage: apply_world_batch.py <merged-upstream-root>")

root = Path(sys.argv[1]).resolve()
parallel = root / "common/src/main/java/com/axalotl/async/common/ParallelProcessor.java"
if not parallel.is_file():
    raise SystemExit(f"missing merged file: {parallel}")

text = parallel.read_text(encoding="utf-8")


def replace(old: str, new: str, count: int = 1) -> None:
    global text
    found = text.count(old)
    if found != count:
        raise SystemExit(
            f"ParallelProcessor source drift: expected {count}, found {found}: {old[:180]!r}"
        )
    text = text.replace(old, new, count)

# HMT dispatches async workers before ticking synchronous fallback entities on the
# dimension thread. Therefore worker-only push deferral is insufficient: a sync
# entity can query crowding while async neighbors are still moving. Mark the whole
# mixed batch active before dispatch so GpuPushBatch defers both sides.
replace(
    "        List<Future<Void>> futures = Collections.emptyList();\n",
    '''        List<Future<Void>> futures = Collections.emptyList();
        boolean pushBatchActive = !asyncEntities.isEmpty();
        boolean workersJoined = false;
        if (pushBatchActive) GpuPushBatch.beginBatch(world);

        try {
'''
)

replace(
'''        // Wait for all async workers, then replay deferred LivingEntity push logic
        // on the server thread. Vulkan is used only for this stable post-barrier phase.
        waitForFutures(futures, world);
        GpuPushBatch.flush(world);
        TickStats.RECORDING_TICKS_LEFT.decrementAndGet();''',
'''            // The world batch remains active through synchronous fallback ticks and
            // every async worker. Only after the join may vanilla crowding replay.
            waitForFutures(futures, world);
            workersJoined = true;
        } finally {
            if (pushBatchActive) {
                // If an unexpected exception bypassed the normal join, converge any
                // already-submitted workers before exposing their final positions.
                if (!workersJoined) waitForFutures(futures, world);
                GpuPushBatch.endBatch(world);
                GpuPushBatch.flush(world);
            }
        }
        TickStats.RECORDING_TICKS_LEFT.decrementAndGet();'''
)

parallel.write_text(text, encoding="utf-8")

# This is the final deterministic source stage in CI. Run the independent spawn
# lock convergence after apply_spawn_interop has produced its supported interface
# overwrite, so all entity-operation callers can share one per-dimension monitor.
spawn_lock = Path(__file__).with_name("apply_spawn_lock.py")
if not spawn_lock.is_file():
    raise SystemExit(f"missing spawn-lock hardening stage: {spawn_lock}")
subprocess.run([sys.executable, str(spawn_lock), str(root)], check=True)

print("HariMultiThread Ultimate mixed sync/async world-batch push barrier applied successfully")
