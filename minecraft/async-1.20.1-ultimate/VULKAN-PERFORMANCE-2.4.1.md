# Hari 2.4.1 Vulkan performance and C2ME compatibility

Recommendations 1, 2 and 5 are implemented for Minecraft 1.20.1 / Forge 47.4.23 / Java 17. Product commit: ba05e9589960f3ab255926c50b007f9d7ce1b4ea. JAR: HariMultiThread-Ultimate-1.20.1-2.4.1-vulkan-hybrid.jar, 30,710,062 bytes, SHA-256 bc91ce5c87610eaeb0a0c87f9b80d44633450adf487d17a0e0e71fb79b8ad446.

## Implementation

- Entity work uses a snapshot-indexed queue without per-entity linked nodes. Affinity, stealing and complete worker barriers are retained. Actual worker thread identity takes the fast path; public registration remains available.
- Automatic entity and mesh worker counts leave CPU capacity for the main/render threads and external chunk engines. Explicit user caps remain authoritative. C2ME owns world generation, lighting and chunk I/O; aliases c2meforge/c2mef/c2me_base/harichunk are detected.
- Chunk workers offer, poll and wait under the same monitor to prevent missed wakeups. Closing a worker safely discards an already-polled build task.
- Uploads reuse primitive staging-region storage and batch compatible disjoint writes into one Vulkan copy call. Overlap and buffer-copy dependencies retain barriers; staging rollover, alignment, bounds, fences, deferred frees and submission ownership are preserved. No new Vulkan feature requirement is introduced.
- Backport the upstream cancelled transparency-sort fix under the renderer's existing LGPL license. Graphics features, gameplay simulation and visibility remain enabled.

## C2ME correctness repairs

Real Minecraft runs exposed several defects; each was repaired and its evidence retained. Despawn, entity tracker mutations, spawn/removal/movement and whole passenger spawn trees execute owner-only effects before entity/dimension locks. World RNG retains C2ME's checked original delegate, sequence and forks on the real owner queue. Entity simulation remains parallel.

C2ME entity barriers use MinecraftServer.managedBlock so server and other-world owner queues can finish chunk dependencies. Every worker is joined; worker and owner errors, cancellation and interruption remain visible. Tick exceptions are not swallowed and emergency crash saves remain active.

A whole-method chunk wrapper intercepts only Hari's own async workers before C2ME's off-thread HEAD interception. Already completed FULL chunks use getChunkNow without creating or handing off. Other requests execute the original getChunk through the actual owner queue, retaining C2ME's re-entrant currently-loading path, tickets and generation. This repairs the native spider CFUtil.join stall captured for superseded product 1707842.

Three inherited executable CFR failure stubs in living/item/player sensor comparators were restored. Identity-cached squared distances preserve stable-world vanilla order and ties while preventing concurrently moving entities from violating the comparator contract. Disabled sensing retains the original extractor. Source and packaged-class gates reject executable decompiler failures.

Strict disposable C2ME fixtures use config version 3 and enforceSafeWorldRandomAccess=true, with actual enforcement logs required. The product does not alter C2ME configuration or disable checks. Optional compatibility fixture: c2meforge-0.2.0-forge.9.6-all.jar, CurseForge file 8929972, 1,343,566 bytes, SHA-256 97401e625906dc7dbe7719c4915d5aa88830e64e5aa6f90831ee45a61373f8d3.

## Verification of this exact binary

| Gate | Run | Result |
|---|---|---|
| Clean build, identical rebuild, packaged integrity, 13 focused Java suites | 36866930953 | Pass |
| Packaged server, strict C2ME, 256-entity preservation, GPU and CPU fallback, block NBT travel/restart, deliberate failure/recovery | 36866930874 | Pass |
| Seven installed Forge production-client configurations | 36866930792 | Pass |
| Extra Khronos synchronization validation, acknowledged far/return terrain travel and native two-worker C2ME server | 36887875665 | Pending |

All three passed jobs and the clean rebuild produce the identical JAR hash above. The seven production-client lanes cover Vulkan, deliberate tick crash, same-world recovery, strict C2ME first launch and reopen, Fabulous OpenGL with settings retained, and actual Embeddium OpenGL coexistence. Each lane captures 300 frames after 120 warmup frames and verifies resource reload and 960/1280 resize. Unexpected runtime errors fail; only the exact offline HTTP 401 authentication diagnostic and explicit Embeddium support notice are classified. The deliberate fault creates a real crash report.

The extra challenge requires real game-mode and teleport acknowledgements, a loaded far chunk, returned loaded chunk, and central terrain crops above chat with the HUD hidden. At least 60% of the 800x350 viewport must contain scene pixels in three consecutive captures; every progression screenshot is retained. The earlier first-tree capture is rejected by this strengthened classifier. Its previous attempt had no command acknowledgements despite continued server progress; the software validation frame p95 was 734ms versus a 200ms chat-open delay. The follow-up paces actual UI input by measured frames and retains each typed command screenshot. The acceptance criteria remain unchanged. Release promotion waits for this challenge.

Focused suites use actual production Java and checked interfaces where Minecraft/GPU resources require doubles. They include 4400 chunk requests on real 1/2/8 workers (1100 cached reads, 3300 owner loads), original failure identities and a negative control reproducing the prior raw-future stall; 1100 cross-queue waits; 553 passenger trees / 2212 entities with the old monitor cycle negative control; checked RNG/lifecycle paths; all three old sensor crashes and 330 parallel sorts. These support, and do not replace, native production testing.

## Measured component improvements

Seven alternating equivalent-work queue samples, 5,120,000 items per sample: baseline median 60,460,101ns; candidate 14,203,676ns (76.5% less time). Allocated bytes: 123,360,000 versus 21,120,000 (82.9% less). Actual old/new upload-manager comparison: 128 disjoint 16-byte writes produced 128 copies and 64 broad write barriers in the baseline versus one copy and zero barriers in the candidate, with all 2048 destination bytes identical. Native batching depends on buffer pair and ordering, so this is not a promised ratio for every frame.

These measure queue and upload components, not overall Minecraft FPS. Native execution uses Linux Mesa software drivers; Windows/RTX 4090 FPS remains unmeasured. Arbitrary third-party modpacks are not universally certified.

## Source and installation

The release includes complete merged source (986 files), the 23-step immutable reconstruction recipe, all 44 builder-package source files, exact JAR and full QA evidence. Fresh reconstruction matches every source byte. Filtering excludes generated build roots by full path and preserves legitimate Java packages named build. CI binary duplicates are represented by the canonical root JAR and hash references. Historical failed attempts are retained separately and never counted as acceptance.

Replace the previous Hari JAR in mods with the included 2.4.1 JAR. The Vulkan renderer is merged; do not install a separate VulkanMod alongside it. C2ME Forge 0.2.0-forge.9.6 is optional and not bundled. Keep current graphics/gameplay settings; Fabulous and conflicting renderers retain the OpenGL compatibility route. Original Hari, renderer and dependency licenses are included in source/JAR.

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

