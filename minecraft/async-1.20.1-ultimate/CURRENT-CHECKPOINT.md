# HariMultiThread Ultimate 1.20.1 — Current Checkpoint

Updated: 2026-09-06
Branch: `async-1.20.1-ultimate`
Pinned upstream: `JustHari01/HariMultiThread@f381611c2d71a85192e2028f9e30c03823a6482b`
Target: Minecraft 1.20.1 / Forge 47.4.23 / Java 17

## Current root cause

The runtime-only diagnostic proved the packaged JAR boots, HMT initializes, entities tick, and the isolated Vulkan backend initializes, but HMT's core mixins were never prepared in production. The packaged JAR contains the mixin classes/config JSON/refmap and mods.toml mixin entries, but `META-INF/MANIFEST.MF` had an empty `MixinConfigs:` attribute. ForgeGradle dev runs hid this because `forge/build.gradle` passes `--mixin.config=harimt.common.mixins.json,harimt.forge.mixins.json` to runClient/runServer.

Production Forge 1.20.1 discovers these configs from the JAR manifest. The fix is therefore packaging-level, not another scheduler, collision, or fixture change.

## Diagnostic receipts

Runtime diagnostic run: `34050580939` (SUCCESS), job `101533345630`.
Artifact: `async-runtime-diagnostic-evidence`, id `9994427633`, digest `sha256:af6ecc11484d5be50605f05791d384eee9c271d952138444884d2427787362bb`.

Exact reused packaged JAR came from failed native run `34049938122`.
Diagnostic results:
- real Forge 47.4.23 server booted;
- HMT full async mode initialized with three worker threads;
- isolated Vulkan backend initialized on llvmpipe;
- `/async stats` reported about 86–87% entities async;
- test cow's Fire counter changed from 199s to 131s over about three seconds, proving entity tick execution;
- no GpuPush/Vulkan push markers;
- verbose Mixin log prepared MixinExtras but never prepared `harimt.common.mixins.json` or `harimt.forge.mixins.json`;
- final packaged manifest contained `MixinConfigs:` with no values.

## Preserved implementation state

All previously accepted hardening remains: work stealing/affinity/circuit breaker, one logical CPU reserved, Dragon sync, EntitySection snapshots, SpawnState serialization, same-tick spawn/random-tick barriers, scheduled-LevelChunk spawn interop, PalettedContainer locking, c2me/c2meforge + DimThread interop, lifecycle cleanup, isolated compile-checked Vulkan backend, repaired 9-SSBO/16-byte shader ABI, convergent final workgroup barriers, shared-dispatch serialization, mixed sync/async world-batch push deferral, complete vanilla fallback, adaptive no-cap pair capacity with learned high-water reuse, and 192-cow/18,336-pair native stress fixture.

## Next authoritative build

The next atomic branch commit must:
1. add deterministic `apply_mixin_packaging.py`;
2. apply it last after `apply_world_batch.py`;
3. restore exactly one push-triggered development workflow;
4. remove the temporary runtime diagnostic workflow;
5. fail closed unless the reobfuscated final JAR manifest contains exactly `MixinConfigs: harimt.common.mixins.json,harimt.forge.mixins.json`;
6. require both mixin JSON files, `harimt.refmap.json`, and `LivingEntityPushMixin.class` in the final JAR;
7. run the existing packaged Forge dense Vulkan/fallback/save/restart gate.

If that full dev gate passes, refresh the single final RELEASE workflow with the same production mixin packaging layer/gates, add the packaging fix to release notes, switch dev CI back to manual-only, and run exactly one final native server + real client/integrated-server release workflow.
