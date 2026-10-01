# Hari 2.4.1 Vulkan performance and C2ME compatibility

Requested scope: recommendations 1 (worker scheduling), 2 (chunk uploads) and 5 (evaluate newer mature implementations). Minecraft 1.20.1, Forge 47.4.23, Java 17. The accepted 2.4.0 shader, failure, ownership and settings corrections remain prerequisites.

## Implemented

- Snapshot-indexed entity queues remove per-entity linked-list nodes while preserving affinity and stealing. Worker identity uses the actual thread subtype, with the public registration fallback retained.
- Automatic entity and mesh worker budgets leave capacity for the main/render threads and C2ME. User-configured entity caps remain authoritative. C2ME owns generation, lighting and chunk I/O. Provider detection also covers c2mef and c2me_base.
- Chunk worker offer/poll/wait use one monitor, preventing missed wakeups. Shutdown discards an already-polled task instead of losing it.
- Chunk uploads reuse primitive region storage and emit multiple disjoint regions in one Vulkan copy command. Barriers remain on actual overlapping writes and buffer-copy dependencies. Staging rollover, deferred frees, submission and fence ownership remain intact.
- Null transparency sort-state cancellation backported from xCollateral/VulkanMod e5dad791ca89b4f40ecef0155148711a4980b3b5 under the existing LGPL renderer license.
- C2ME runtime repair: despawn checks stay on the owner thread, and spawn/removal/movement side effects hand off before acquiring entity locks. World RNG calls retain C2ME's checked delegate and exact RNG sequence on the real owner queue; entity simulation remains parallel. No C2ME safety check is disabled.
- Source archives exclude generated build roots by exact path, preserving legitimate Java packages named build.

## Bounded primary-source review

| Source | Reviewed revision | Disposition |
|---|---|---|
| xCollateral/VulkanMod | 087ef24e58e5522dec5ccb44f279768786bfb6da | Preserve 1.20.1 lineage; adopt cancelled-sort fix. Current AreaBuffer still scans segments; existing JOML culling is retained. |
| thr3343/VulkanMod | e83e2d613f7fe8be9ef114bdf39f93361be8b75c | Adapt grouped-copy/barrier principles to the existing Vulkan 1.0 upload architecture. Do not transplant 1.21 BDA/FP16 formats or require timeline semaphores. |
| VLOD-ZDOV/VulkanMod-Next | a28623a285f3150ad4b4ac8510bcedab6daac592 | 1.12/1.16 GL-mirror architecture differs. Existing JOML frustum and off-thread mesh construction already cover the useful concepts. |
| CriticalRange/vulkanmod-extra | be047b4b2eccf24040d525b39a424c9d25edb577 | Current 1.21 modules largely disable visual features. Do not remove animations, particles, sky or beams. Chunk improvements listed as roadmap are not treated as shipped code. |
| TrulyRin/VulkanMod-Reforged | 3a76314a4eb23175b8c125b43cf89f7c4cebac92 | Current 1.21 merge/UI work is not a drop-in 1.20.1 optimization. |
| SebastianDanielFrenz/c2me-forge | forge/1.20.1, reviewed 2026-10-01 | Respect generation/I/O ownership; no second generation implementation added. |
| Makki132 C2ME Forge | 0.2.0-forge.9.6, CurseForge file 8929972 | Exact production compatibility fixture; chunk block-entity NBT unload/reload/restart gates added. |

Primary URLs:
- https://github.com/xCollateral/VulkanMod/commit/e5dad791ca89b4f40ecef0155148711a4980b3b5
- https://github.com/thr3343/VulkanMod/commit/82b39a76c539f36469f5a91cd6f7f5c7d32712c7
- https://github.com/VLOD-ZDOV/VulkanMod-Next
- https://github.com/CriticalRange/vulkanmod-extra
- https://github.com/TrulyRin/VulkanMod-Reforged
- https://github.com/SebastianDanielFrenz/c2me-forge
- https://www.curseforge.com/minecraft/mc-mods/concurrent-chunk-management-engine-forge/files/8929972

## Checkpoint validation

Actual production Java focused tests passed: exact-once concurrent work, snapshot/null semantics, identity, CPU budget cases, byte-exact upload ordering/staging rollover/compaction and range reuse; a forced missed-wakeup race, 4000 concurrent chunk tasks, bounded upload drain, restart and closure; all eight real-future failure/barrier cases.

Alternating equivalent-work queue benchmark (7 samples, 5,120,000 items/sample): baseline median 56,882,043 ns versus candidate 12,487,106 ns; baseline 123,360,000 allocated bytes versus candidate 21,120,000. This measures queue construction/draining, not overall Minecraft FPS. 128 disjoint upload regions recorded one copy command and zero redundant write barriers in the checked command harness. Native Vulkan execution is a separate gate.

Packaged production client, real C2ME client/server coexistence, NBT persistence, resource reload/resize, deliberate failure surfacing and reproducible JAR build are pending CI at this checkpoint. No Windows/RTX 4090 FPS claim is made from software-driver CI.

C2ME fixture bytes: 1,343,566; SHA-256: 97401e625906dc7dbe7719c4915d5aa88830e64e5aa6f90831ee45a61373f8d3. C2ME is optional and is not bundled into Hari.

## Runtime discoveries and repair checkpoint

The initial native server passed 256-entity preservation and exact block-entity inventory unload/reload/restart without C2ME. C2ME live GPU collision passed, then the new harness exposed a missing evidence-directory creation; this QA-only error was repaired. The packaged C2ME client exposed real off-thread despawn/tracker mutation and bee access to C2ME's checked world RNG. Owner-thread lifecycle/RNG handoffs were implemented instead of disabling C2ME checks or replacing its random sequence. A new actual-Java test passed 1,400 exact RNG sequence cases, 1,100 lifecycle owner calls on real 1/2/8 workers, zero off-owner delegate accesses and original exception/Error identity preservation. Native proof of this repair remains pending.

C2ME Forge reconfigures client console appenders. QA now reads the fresh Forge latest.log as well as stdout, saves both, and rejects off-thread C2ME diagnostics. This repairs observation without changing logging or hiding errors.

The packaged C2ME client then exposed a passenger-spawn lock cycle: an entity worker held the outer dimension monitor while waiting for owner-thread spawning, and the owner thread needed that monitor. The live JVM dump identified both sides. The whole self-and-passenger spawn tree now hands off before acquiring the outer monitor. A regression compiles the actual mixin, reproduces the old lock cycle as a negative control, and checks the fixed path with 553 passenger trees / 2212 exact-once entities, ordering, original failures and disabled/non-C2ME paths. Full native acceptance remains pending.

The passenger fix passed packaged C2ME gameplay and same-world reopen. A dedicated-server fault-recovery watchdog then captured a separate queue dependency: an entity worker waited inside C2ME's off-thread chunk request while Hari polled only the current world's chunk queue. C2ME entity batches now use MinecraftServer.managedBlock, allowing server and other-world owner work to progress after the tick budget expires. Every worker is still joined, and original worker/owner failures, interruption and cancellation remain visible. The actual production waiter regression reproduces the old chunk-only stall and completes 1100 multi-stage cross-queue requests on 1/2/8 workers. Native QA additionally forces C2ME's optional safe-world-RNG enforcement to true in disposable fixtures; production does not change C2ME settings.

The broader native strict-C2ME travel challenge reached an inherited executable CFR throw stub in NearestLivingEntitySensor's comparator. Source-wide auditing found three such failures: living entities, items and players. All three now compare cached squared distances, freezing the observer at sensor entry and each target at its first comparison. This preserves stable-world vanilla ordering and stable ties while preventing movement from breaking the sorting contract; the disabled path keeps the original extractor. Real Java regressions reproduce all three old crashes and verify 759 stable-world entities, identity-colliding targets, movement snapshots, fresh comparator refresh, original extractor failures and 330 parallel sorts. Recipe and packaged-JAR gates now reject executable decompiler failures. Native acceptance must be rerun for this final sensor repair; earlier builds are superseded.

The exact old/new uploader comparison additionally passed: the accepted source emitted 128 copies and 64 broad write barriers for 128 disjoint 16-byte writes. The candidate emitted one copy and zero barriers, with every destination byte identical (2048 checked bytes). The compiled CI queue benchmark measured 59,350,819 ns baseline versus 14,142,559 ns candidate (76.2% less time) with the same 82.9% allocation reduction. These remain component measurements rather than overall FPS.

## Owner chunk-path repair (current candidate ba05e958)

The stricter central-terrain challenge at run 36825404697 timed out before far-travel acknowledgement. Its live JVM dump shows the server inside C2meTaskWaiter/Minecraft managed blocking, an async spider tick inside C2ME getChunkOffThread/CFUtil.join, and idle C2ME generation workers. Earlier seven-client/server passes are therefore insufficient to promote product 1707842.

The exact 0.2.0-forge.9.6 bytecode schedules its off-thread request through ChunkHolder.getOrScheduleFuture, whereas its re-entrant loading fix wraps the normal owner getChunk future path. A whole-method wrapper now intercepts only Hari's own async workers before that off-thread HEAD injector. Completed FULL chunk reads use the non-creating getChunkNow fast path; loading and other-status requests execute the original whole getChunk on the actual owner. C2ME still owns generation, lighting, I/O, ticketing and its re-entrant currently-loading handling. Other providers' threads, non-C2ME operation and the owner path remain unchanged.

Actual Java regression: 4400 requests on 1/2/8 workers; 1100 cached reads without owner handoff; 3300 owner loading/status requests; old incomplete raw-chain negative control; null/non-creating semantics; original RuntimeException/Error identity. Native acceptance is pending compile 36866930953, server 36866930874, clients 36866930792 and a newly pinned strict Khronos travel challenge. Complete source is now 986 files, byte-identical to fresh reconstruction.
