# HariMultiThread Vulkan hybrid — current checkpoint

Updated 2026-10-01. Minecraft 1.20.1 / Forge 47.4.23 / Java 17. Canonical branch: async-1.20.1-ultimate-2.4.0-vulkan-hybrid-20260926.

Recommendations 1, 2 and 5 are implemented. Current product commit: ba05e9589960f3ab255926c50b007f9d7ce1b4ea. Complete source contains 986 files, byte-identical to a fresh 23-step immutable-source reconstruction. New actual Java regression passed 4400 chunk requests on 1/2/8 workers, including 1100 loaded reads without owner handoff, 3300 owner load/status requests, original failures and unchanged other-provider/non-C2ME behavior.

The prior sensor-repaired product 1707842 passed compile, server, seven packaged clients and a two-worker C2ME server challenge, but broader strict travel run 36825404697 stalled a spider in C2ME's getChunkOffThread/CFUtil.join while generation workers were idle. It is superseded. The new whole-method wrapper preserves completed FULL chunk reads and sends loading/status misses through the server-owner getChunk path, before C2ME's off-thread HEAD injector, retaining its re-entrant loading fixes. Managed blocking/full worker barriers, checked RNG and earlier lifecycle/sensor repairs are preserved.

New compile/repro run 36866930953, native server run 36866930874 and seven-client run 36866930792 are running. Strict Khronos chunk travel must be repinned to the new compile artifact. Do not promote before all native gates pass. No tick errors are swallowed and no C2ME safety checks are disabled. Native proof is Linux Mesa, not Windows/RTX4090 FPS proof.

See PERFORMANCE-RELEASE-CHECKPOINT.json for precise next actions. Current source checkpoint must be persisted to Drive before continuing lengthy CI. Historical artifacts remain immutable.
