# Hari 2.4.1 Vulkan performance and C2ME compatibility

Recommendations 1, 2 and 5 are implemented for Minecraft 1.20.1 / Forge 47.4.23 / Java 17. Product commit: 6594c6cc652db554213c52ae02f7acd8f439644c. JAR: HariMultiThread-Ultimate-1.20.1-2.4.1-vulkan-hybrid.jar, 30,730,495 bytes, SHA-256 58ceda73c9328c78fc3d82eb652475cb4c7ddd91f3078d6d31717eb0ea9d432f.

## Implementation

- Entity work uses a snapshot-indexed queue without per-entity linked nodes. Affinity, stealing and complete worker barriers are retained. Actual worker thread identity takes the fast path; public registration remains available.
- Automatic entity and mesh worker counts leave CPU capacity for the main/render threads and external chunk engines. Explicit user caps remain authoritative. C2ME owns world generation, lighting and chunk I/O; aliases c2meforge/c2mef/c2me_base/harichunk are detected.
- Chunk workers offer, poll and wait under the same monitor to prevent missed wakeups. Closing a worker safely discards an already-polled build task.
- Uploads reuse primitive staging-region storage and batch compatible disjoint writes into one Vulkan copy call. Overlap and buffer-copy dependencies retain barriers; staging rollover, alignment, bounds, fences, deferred frees and submission ownership are preserved. No new Vulkan feature requirement is introduced.
- Backport the upstream cancelled transparency-sort fix under the renderer's existing LGPL license. Graphics features, gameplay simulation and visibility remain enabled.
- Replace inherited direction-change omission with reusable deferred graph scheduling. All frustum/occlusion-valid sections remain eligible; the existing advanced-culling setting now prioritizes simple paths instead of dropping visible terrain. The actual complete Java graph/queue regression reproduces 11/32 reachable sections in the old code and verifies 44,744 section checks across all four settings, queue growth/reset, exact rebuilds/block entities and retained frustum/visibility exclusions.

The complete graph regression proves a concrete old reachability defect. The short-distance native screenshots do not independently establish that their terrain frontier was caused by this graph defect. A separate view/simulation-distance-eight challenge is checking surrounding terrain before final packaging. Official ExecuteCommand bytecode confirms that if loaded requires ENTITY_TICKING and loaded entity data, so simulation distance must cover the entire radius-six predicate.

## Rendering and audio API boundaries

Minecraft's shader, buffer, texture, render-target, draw and window paths use the merged Vulkan renderer and SPIR-V pipelines. Hari's Embeddium/OpenGL terrain route remains behind renderer ownership and capability checks; it does not probe or call a missing OpenGL context in the Vulkan lane. The seven native clients verify both routes with their original graphics settings.

The upstream import declared 2,359 GL overloads as compatibility overwrites, including empty copy/compute/shader placeholders. An overwrite is no longer taken as evidence of translation. The generated external-call contract advertises 111 reviewed state/resource overloads and includes their exact JVM descriptors. The selector rejects unknown overloads, generic shader/compute paths and empty placeholders before applying Vulkan transforms; incompatible providers retain the complete OpenGL renderer. It does not claim arbitrary OpenGL 4.x programs have been converted into working Vulkan pipelines. The cache schema invalidates the old permissive decisions.

GL20 and ARB source boundaries preserve all source strings, explicit UTF-8 byte lengths, null-terminated strings and caller buffer positions; native LWJGL memory tests cover these paths. Buffer-based program/shader queries use the same underlying query as scalar overloads rather than return unconditional success.

Official Minecraft 1.20.1 bytecode exposed a reload race: stopAll restarts the sound executor, queues source stops, directly clears handles and destroys the context without waiting behind those source operations. ChannelAccess now runs its original clear on the sound executor and waits before context cleanup. Same-thread cleanup runs directly; original failures and submission failures propagate unchanged. The negative control reproduces the deleted-source race, and 1,100 ordered cleanup cycles pass. A separate installed client acknowledges a live music sound and completes resource reload three times, with no invalid-source errors or muted diagnostic. OpenAL remains the audio API.

## C2ME correctness repairs

Real Minecraft runs exposed several defects; each was repaired and its evidence retained. Despawn, entity tracker mutations, spawn/removal/movement and whole passenger spawn trees execute owner-only effects before entity/dimension locks. World RNG retains C2ME's checked original delegate, sequence and forks on the real owner queue. Entity simulation remains parallel.

C2ME entity barriers use MinecraftServer.managedBlock so server and other-world owner queues can finish chunk dependencies. Every worker is joined; worker and owner errors, cancellation and interruption remain visible. Tick exceptions are not swallowed and emergency crash saves remain active.

A whole-method chunk wrapper intercepts only Hari's own async workers before C2ME's off-thread HEAD interception. Already completed FULL chunks use getChunkNow without creating or handing off. Other requests execute the original getChunk through the actual owner queue, retaining C2ME's re-entrant currently-loading path, tickets and generation. This repairs the native spider CFUtil.join stall captured for superseded product 1707842.

Three inherited executable CFR failure stubs in living/item/player sensor comparators were restored. Identity-cached squared distances preserve stable-world vanilla order and ties while preventing concurrently moving entities from violating the comparator contract. Disabled sensing retains the original extractor. Source and packaged-class gates reject executable decompiler failures.

Strict disposable C2ME fixtures use config version 3 and enforceSafeWorldRandomAccess=true, with actual enforcement logs required. The product does not alter C2ME configuration or disable checks. Optional compatibility fixture: c2meforge-0.2.0-forge.9.6-all.jar, CurseForge file 8929972, 1,343,566 bytes, SHA-256 97401e625906dc7dbe7719c4915d5aa88830e64e5aa6f90831ee45a61373f8d3.

## Verification of this exact binary

| Gate | Run | Result |
|---|---|---|
| Clean build, identical rebuild, packaged integrity, 17 focused Java suites | 36899664277 | Pass |
| Packaged server, strict C2ME, 256-entity preservation, GPU/CPU, block NBT travel/restart, deliberate failure/recovery | 36899664082 | Pass |
| Seven installed Forge production-client configurations | 36899664091 | Pass |
| Khronos synchronization validation, three active-sound reloads, 49-chunk far/return predicates, sustained terrain and two-worker C2ME restart | 36900474237 | Pass |
| Additional view-distance-eight, 169-chunk readiness and sustained 90% terrain coverage | 36906027597 | Running |

Both independent builds, the clean rebuild and the installed client use the identical JAR hash above. The extra native challenge downloads that immutable compile artifact and checks its exact installed hash. The seven production-client lanes cover Vulkan, deliberate tick crash, same-world recovery, strict C2ME first launch and reopen, Fabulous OpenGL with settings retained, and actual Embeddium OpenGL coexistence. Each lane captures 300 frames after 120 warmup frames and verifies resource reload and 960/1280 resize. Unexpected runtime errors fail; only the exact offline HTTP 401 authentication diagnostic and explicit Embeddium support notice are classified. The deliberate fault creates a real crash report.

The passed extra challenge requires real sound, game-mode and teleport acknowledgements, all 49 nearby entity-ticking chunks with loaded entity data at each destination, and central terrain crops above chat with the HUD hidden. At least 60% of the 800x350 crop must contain scene pixels in three consecutive captures; every progression screenshot remains included. Far/return commands are paced from measured frame times, and every typed command screenshot is retained. The follow-up view-distance-eight challenge raises this to all 169 nearby entity-ticking chunks with loaded entities and sustained 90% terrain coverage. It changes only disposable QA settings.

Focused suites use actual production Java and checked interfaces where Minecraft/GPU resources require doubles. They include 4400 chunk requests on real 1/2/8 workers (1100 cached reads, 3300 owner loads), original failure identities and a negative control reproducing the prior raw-future stall; 1100 cross-queue waits; 553 passenger trees / 2212 entities with the old monitor cycle negative control; checked RNG/lifecycle paths; all three old sensor crashes and 330 parallel sorts. These support, and do not replace, native production testing.

## Measured component improvements

Seven alternating equivalent-work queue samples, 5,120,000 items per sample: baseline median 63,338,047ns; candidate 26,159,995ns (58.7% less time). Allocated bytes: 123,360,000 versus 21,120,000 (82.9% less). Actual old/new upload-manager comparison: 128 disjoint 16-byte writes produced 128 copies and 64 broad write barriers in the baseline versus one copy and zero barriers in the candidate, with all 2048 destination bytes identical. Native batching depends on buffer pair and ordering, so this is not a promised ratio for every frame.

These measure queue and upload components, not overall Minecraft FPS. Native execution uses Linux Mesa software drivers; Windows/RTX 4090 FPS remains unmeasured. Arbitrary third-party modpacks are not universally certified.

## Source and installation

The release includes complete merged source (989 files), the 23-step immutable reconstruction recipe, all 44 builder-package source files, exact JAR and full QA evidence collection. Fresh reconstruction matches every source byte. Filtering excludes generated build roots by full path and preserves legitimate Java packages named build. CI binary duplicates are represented by the canonical root JAR and hash references. All 1,128 historical diagnostic files remain preserved in two independently readable companion ZIPs, with exact union/hash verification. Later expanded-fixture failures are preserved as well. They are not current acceptance. Each collection ZIP is below the per-file upload limit.

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
