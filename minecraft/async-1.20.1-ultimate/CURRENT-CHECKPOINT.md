# HariMultiThread Vulkan Hybrid — current work checkpoint

Updated: 2026-10-01. Target: Minecraft 1.20.1 / Forge 47.4.23 / Java 17. Canonical branch: `async-1.20.1-ultimate-2.4.0-vulkan-hybrid-20260926`.

The repaired 2.4.1 performance candidate is implemented at product commit `22f4bda73d7cc7b8631c497fd0dfb1bd7fa28ca8`. Its JAR is 30,707,838 bytes, SHA-256 `73c952ec7105f0ad971a55d880f4152db40bc22ae4eaf0de267a2678432709ac`. Compile/repro run 36820325316 and full native-server/C2ME run 36820325255 passed with identical JARs. Native server proof includes sustained GPU collision, exact block inventory unload/reload/restart, intentional entity-failure reporting and saved-world recovery with all 256 tagged entities. Default C2ME unsafe-access detection stayed active, and normal server/reopen logs have zero error rows.

Packaged clients run 36820325336 and Khronos synchronization-validation/C2ME travel run 36821367192 remain in progress. The latter requires actual strict RNG enforcement and two native entity workers. Its disposable C2ME config includes schema version 3, so the provider does not reset the strict setting. The candidate is not yet promoted. Complete working source has 985 files and matches a fresh 23-step immutable-source reconstruction byte for byte, including all 44 restored builder files.

Earlier performance candidates are rejected: 418ed90 had an outer passenger-spawn dimension-lock cycle; f98d69e repaired that but had a C2ME chunk-future queue dependency during server recovery. Live JVM/watchdog evidence is preserved. Product 22f4bda hands off the whole passenger tree before its lock and uses Minecraft managed blocking while joining C2ME workers. Do not rerun unchanged rejected code, skip ticks, or disable C2ME safety checks.

See PERFORMANCE-RELEASE-CHECKPOINT.json for exact pending actions and VULKAN-PERFORMANCE-2.4.1.md for implementation and primary-source research. Preserve the previous verified 2.4.0 integrity release and every shader, ownership, index, failure, config, graphics and packaging correction. Native proof is Linux Mesa software; no Windows/RTX 4090 FPS claim.
