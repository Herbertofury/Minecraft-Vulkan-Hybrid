# Hari integrated dimension scheduler

Development source for one Forge 1.20.1 artifact containing Hari's native Vulkan renderer/entity engine and the pinned DimThreads dimension engine. The compatibility mod identity `dimthread` and public packages remain available to the pack's C2ME, Alex's Mobs and Scorched Guns interoperation patches. Install this artifact in place of both standalone Hari and standalone DimThreads; do not load all three.

The accepted `source/` tree remains byte-identical. `assemble.py` verifies its complete 1001-file manifest and the vendored upstream manifest, then builds a fresh recoverable tree with the reviewed overlay. Upstream DimThreads source is pinned to `542b900e6a1f634d4bf339c38d90189de538e6d9` from SrRapero720's 1.20.1 branch. Its LGPL-3.0 license and author credits are retained. The original pack's custom v5 JAR remains untouched in backups; this integration replaces its scheduler with rebuildable upstream source and the exception-safe per-object owner transfer required by its interoperation interfaces.

The integration removes DimThreads' duplicate block-entity redirect and redundant cross-world chunk-thread spoofing. Hari supplies the shared hook. World and chunk owners transfer together, with identity-deduplicated ordered locks and restoration even when a tick or owner setter throws. The global Minecraft server owner is never changed. Only the global owner may pump its global packet queue. A dimension's C2ME barrier pumps its own chunk queue, preventing the repeated movement-packet rescheduling observed with the earlier two-mod combination. The ordinary server-owner wait retains its cross-queue progress behavior.

Dimension and entity pools share one simulation worker budget, with separate queues so dimensions awaiting nested entity batches cannot exhaust a single executor. On the measured 32-thread client with external C2ME, the budget is three dimension workers plus seven entity workers. C2ME keeps its own chunk scheduler; other projects' CPU/GPU workloads are neither governed nor interrupted by this budget. Dimension task failures finish the barrier and propagate; tick crashes are not silently skipped.

Build from the repository root with Java 17:

```powershell
python integration/dimensions/assemble.py ../hari-dimensions-build
.\source\gradlew.bat -p ../hari-dimensions-build :forge:build --no-daemon --max-workers=4
```

The result is `../hari-dimensions-build/forge/build/libs/harimt-forge-1.20.1-2.4.11-noxviola.2-dimensions-vulkan-hybrid-all.jar`, targeting Forge 47.4.26. A new output path is required on each assembly; existing trees are never overwritten.

Status: local source build, resource metadata and focused ownership/failure regressions pass. The integrated `.2` artifact passed seven actual full-pack smoke checks including resource reload, animated entity death and Nether round trip, and saved/exited normally. The log reported three dimension workers and seven entity workers with no audited ownership exception. However, [matched native performance](../../benchmarks/results/noxviola-matched-20261005/REPORT.md) regressed from median 219.39 to 175.22 FPS despite startup improving 166.40→152.82 seconds. Promotion is rejected. Cumulative mod-addition and isolated vanilla capability comparisons remain in progress; this artifact is not ready for personal saves or a 2,400 FPS claim.
