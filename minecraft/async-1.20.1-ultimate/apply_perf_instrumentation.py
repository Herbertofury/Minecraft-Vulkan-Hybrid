#!/usr/bin/env python3
from pathlib import Path
import sys

if len(sys.argv) != 2:
    raise SystemExit("usage: apply_perf_instrumentation.py <merged-upstream-root>")

root = Path(sys.argv[1]).resolve()


def file(rel: str) -> Path:
    path = root / rel
    if not path.is_file():
        raise SystemExit(f"missing transformed source: {path}")
    return path


def replace_once(path: Path, old: str, new: str, label: str) -> None:
    text = path.read_text(encoding="utf-8")
    found = text.count(old)
    if found != 1:
        raise SystemExit(f"{label}: expected one source marker, found {found}")
    path.write_text(text.replace(old, new, 1), encoding="utf-8")

# Benchmark-only telemetry. This layer is applied identically to baseline and
# candidate after every production transform, so it cannot create an A/B code
# asymmetry in the measured hot path. It emits only when explicit benchmark
# commands are issued, after the timed workload has already run. Use direct
# stdout rather than the logger so the external dedicated-server harness can
# deterministically observe each marker through the process pipe.
stats = file("common/src/main/java/com/axalotl/async/common/commands/StatsCommand.java")
replace_once(
    stats,
'''        int totalEntities = 0;
        int asyncEntities = 0;
        for (ServerLevel world : server.getAllLevels()) {''',
'''        int totalEntities = 0;
        int asyncEntities = 0;
        int perfTagged = 0;
        int noiseTagged = 0;
        for (ServerLevel world : server.getAllLevels()) {''',
    "StatsCommand tagged population counters",
)
replace_once(
    stats,
'''                if (!entity.isAlive()) continue;
                ++totalEntities;''',
'''                if (!entity.isAlive()) continue;
                if (entity.getTags().contains("harimt_perf")) ++perfTagged;
                if (entity.getTags().contains("harimt_noise")) ++noiseTagged;
                ++totalEntities;''',
    "StatsCommand tagged population sampling",
)
replace_once(
    stats,
'''        source.sendSuccess(() -> message, false);
    }

    private static void showEntityStats''',
'''        System.out.println(
                "HMT_PERF_STATS mspt=" + mspt
                        + " entities=" + totalEntities
                        + " asyncEntities=" + asyncEntities
                        + " perfTagged=" + perfTagged
                        + " noiseTagged=" + noiseTagged);
        source.sendSuccess(() -> message, false);
    }

    private static void showEntityStats''',
    "StatsCommand telemetry",
)

gpu = file("common/src/main/java/com/axalotl/async/common/commands/GpuCommand.java")
replace_once(
    gpu,
'''        source.sendSuccess(() -> message, false);
    }

    /**
     * Compares the live GPU candidate set''',
'''        System.out.println(
                "HMT_PERF_GPU lastDispatchMs=" + d.getLastDispatchMillis()
                        + " gpuBatches=" + GpuPushBatch.getGpuBatches()
                        + " candidatePairs=" + GpuPushBatch.getGpuPairs());
        source.sendSuccess(() -> message, false);
    }

    /**
     * Compares the live GPU candidate set''',
    "GpuCommand status telemetry",
)
replace_once(
    gpu,
'''        if (!missing.isEmpty()) {
            AsyncConfig.enableGpuCollision.setValue(false);
            PlatformUtils.saveConfig();
            source.sendFailure(AsyncCommand.prefix.copy().append(''',
'''        if (!missing.isEmpty()) {
            AsyncConfig.enableGpuCollision.setValue(false);
            PlatformUtils.saveConfig();
            System.out.println("HMT_PERF_GPU_VERIFY FAIL missingPairs=" + missing.size());
            source.sendFailure(AsyncCommand.prefix.copy().append(''',
    "GpuCommand verifier failure telemetry",
)
replace_once(
    gpu,
'''        double gpuMs = d.getLastDispatchMillis();
        double cpuMs = cpuNanos / 1_000_000.0;
        source.sendSuccess(() -> AsyncCommand.prefix.copy()''',
'''        double gpuMs = d.getLastDispatchMillis();
        double cpuMs = cpuNanos / 1_000_000.0;
        System.out.println(
                "HMT_PERF_GPU_VERIFY PASS exactPairs=" + exact.size()
                        + " gpuCandidates=" + gpu.size()
                        + " extras=" + extras.size()
                        + " gpuDispatchMs=" + gpuMs
                        + " cpuReferenceMs=" + cpuMs);
        source.sendSuccess(() -> AsyncCommand.prefix.copy()''',
    "GpuCommand verifier pass telemetry",
)

for path, markers in (
    (stats, ("HMT_PERF_STATS", "perfTagged=", "noiseTagged=")),
    (gpu, ("HMT_PERF_GPU lastDispatchMs", "HMT_PERF_GPU_VERIFY PASS", "HMT_PERF_GPU_VERIFY FAIL")),
):
    text = path.read_text(encoding="utf-8")
    for marker in markers:
        if text.count(marker) != 1:
            raise SystemExit(f"telemetry invariant failed for {path.name}: {marker}")

print("HariMultiThread Ultimate symmetric direct-stdout performance telemetry applied")
