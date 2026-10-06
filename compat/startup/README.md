# Complete-pack startup work

The latest official ModernFix5.27.85 was already installed and its hash matches the publisher. It was tested rather than duplicated or upgraded to an incompatible game version. [Pinned publisher/source review](UPSTREAM.json).

Four actual complete352-JAR A/B/A/B launches tested the optional `mixin.perf.dynamic_resources=true`. Median game-to-world161.674735 ->137.080535s (-15.21%) with repeated local caches. FPS ranges overlap, the final pair regresses and broader model/first-use parity remains unresolved. The option is rejected for retention even though the separate reload/death/dimension smoke passes. [Raw matched measurements and limits](../../benchmarks/results/fullpack-20261005/REPORT.md).

The user's subsequent request removes all20 auxiliary performance mods from the isolated candidate, including ModernFix, FastBoot, FastSuite, FerriteCore and the separate grass helper. Original gameplay mods remain. No optimizer is automatically re-added; a fresh matched purpose-specific benefit and feature parity are required. [New baseline and recoverable removal decisions](../../benchmarks/results/fullpack-20261005/no-aux-performance/REPORT.md). The personal pack remains unchanged.

LightLoad and WarpLoad offer resource indexing, and Fastload defers spawn-region work. Their initial official-source review is recorded; none is installed or credited with a gain. Failure suppression, AI gating and unreviewed agent binaries are not acceptable substitutes for repairing errors and preserving features. New upstream-source improvements should follow a local startup profile identifying a remaining hotspot after the measured option trial.

ModernFix's Spark integration uploads by default. A future diagnostic must explicitly use its verified local-file mode or local JFR and restore diagnostic flags afterward. Measurements must state JVM start versus game-ready versus world-ready, cold versus warm cache, identical mod/assets/settings, and continued external workloads.
