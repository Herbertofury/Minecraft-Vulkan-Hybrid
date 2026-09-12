# HariMultiThread Ultimate 1.20.1 — Current Checkpoint

Updated: 2026-09-12
Canonical branch: `async-1.20.1-ultimate`
Final-fix staging branch: `async-1.20.1-ultimate-final-fix-20260912`
Pinned upstream: `JustHari01/HariMultiThread@f381611c2d71a85192e2028f9e30c03823a6482b`
Target: Minecraft 1.20.1 / Forge 47.4.23 / Java 17

## Accepted runtime boundary

Authoritative diagnostic release run #88 (`34081440571`, job `101617338848`, commit `6203952553f89ec69892fab699c0746bf5ac4476`) passed every release gate through the packaged runtime:

- deterministic release reconstruction and hardening invariants;
- real SPIR-V compilation/validation;
- Forge 47.4.23 production build and final JAR/Mixin/refmap/manifest checks;
- isolated Vulkan backend packaging;
- real Forge 47.4.23 dedicated-server installation;
- packaged Vulkan activation, complete fallback, save and restart QA.

The only failed step was the real Forge client/integrated-server evidence gate.

## Client failure root cause proven from run #88 artifact

Failure artifact `async-1.20.1-ultimate-client-failure` (`10003967788`, digest `sha256:ab5ec95af91146f258f29485b58cf7b2171162bb9674e320ba36f17fdb955bcc`) proves the client runtime itself was healthy:

- real `[Render thread/INFO]` was reached;
- local player `Dev` logged in and `Dev joined the game`;
- isolated Vulkan backend activated on llvmpipe;
- first verified broad-phase batch produced 32,641 candidate pairs;
- 10 consecutive verified Vulkan broad-phase batches completed;
- no HariMT/Mixin/Vulkan/JVM fatal marker occurred.

The QA harness then captured the Minecraft window while it still displayed `Loading terrain...`. The exact failed capture was 1278x695, 9 colors, grayscale standard deviation 0.033306, and 4,517 bytes. The old harness incorrectly used PNG byte size `< 10,000` as its rejection rule, so it failed before waiting for an actually rendered world.

## Final-fix implementation

The staging branch contains only the following intended delta from canonical commit `6203952553f89ec69892fab699c0746bf5ac4476`:

1. `runtime_client_qa.py`
   - requires Render-thread proof, integrated player join, Vulkan active, and sustained 10-batch proof;
   - repeatedly captures the actual visible Minecraft X11 window until it satisfies rendered-world metrics (minimum 800x450, 64 colors, normalized grayscale standard deviation 0.050);
   - explicitly rejects the exact old 9-color `Loading terrain...` frame in the local negative control;
   - preserves console/latest-log/error/last-unready-frame evidence on failures;
   - keeps the rendered world alive briefly before a normal Alt+F4 close and integrated-server save path.

2. `performance_stack_compatibility_audit.py`
   - verifies third-party entity namespaces remain synchronous unless explicitly opted into HariMT `AsyncCompatible`;
   - verifies `ServerPlayer` and Ender Dragon synchronous safety;
   - verifies C2ME Forge detection and dimension-local DimThread barrier pumping;
   - fails if HariMT common/Forge code overlaps Minecraft client renderer/GUI/Blaze3D/OpenGL, Entity Culling, ImmediatelyFast, GPUTape, or BadOptimizations ownership;
   - verifies Vulkan remains a private isolated backend instead of a Forge JarJar LWJGL module;
   - writes a compatibility report covering the active Noxviola performance stack, including Entity Culling, ImmediatelyFast, GPUTape, BadOptimizations, C2ME/DimThread, Physics Mod/Uranus, Curios/ApothicCurios, and Potatoptimize.

3. `apply_mixin_packaging.py`
   - invokes the compatibility audit after the final deterministic source transformation, making the compatibility contract release-blocking and including its report in release evidence.

No gameplay/content/quality reduction, entity cap, render reduction, silent collision loss, unsafe fire-and-forget behavior, or mandatory Vulkan dependency is introduced.

## Preserved implementation

All accepted hardening remains unchanged: work stealing/affinity/circuit breaker, one logical CPU reserved, Dragon sync, conservative modded-entity sync policy, EntitySection snapshots, tracked-entity serialization, same-tick spawn/random-tick barriers, scheduled-LevelChunk spawn interop, PalettedContainer locking, C2ME/c2meforge + SAFE-INTEROP DimThread compatibility, lifecycle cleanup, isolated compile-checked LWJGL Vulkan backend, repaired 9-SSBO/16-byte shader ABI, convergent final workgroup barriers, serialized AABB snapshot/dispatch/pair-map transaction, mixed sync/async world-batch push deferral, complete vanilla fallback, adaptive no-cap pair capacity with learned high-water reuse, and the dense collision stress fixture.

## Exact next action

Fast-forward `async-1.20.1-ultimate` to this staged successor with `force=false` exactly once. Track only the resulting `Async 1.20.1 Ultimate` run. Require: compatibility gate -> SPIR-V -> Forge build/JAR/refmap/backend -> packaged Forge server Vulkan/fallback/save/restart -> real Forge client with rendered-world evidence and sustained Vulkan -> package/checksums -> Actions release bundle -> GitHub release tag `harimt-ultimate-1.20.1-2.1.0-noxviola.1`. Independently verify the resulting JAR/source/evidence/checksums and mirror the verified deliverables to Drive folder `12TS5TdawtH6x4UU4OCiCQ933lz7nHmDD`.
