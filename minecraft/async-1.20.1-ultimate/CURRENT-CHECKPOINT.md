# HariMultiThread Ultimate 1.20.1 — Current Checkpoint

Updated: 2026-09-06
Branch: `async-1.20.1-ultimate`
Pinned upstream: `JustHari01/HariMultiThread@f381611c2d71a85192e2028f9e30c03823a6482b`
Target: Minecraft 1.20.1 / Forge 47.4.23 / Java 17

## Current root cause

Production Mixin discovery and refmap generation were two separate packaging defects.

The first failed production artifact contained HMT's Mixin classes/config JSON but had an empty `MixinConfigs:` manifest, so packaged Forge never prepared HMT core mixins. `apply_mixin_packaging.py` fixed that. Dev run `34060372089` then built successfully and its exact final JAR proved the manifest fix survived reobfuscation:

`MixinConfigs: harimt.common.mixins.json,harimt.forge.mixins.json`

That run failed at the next fail-closed artifact check because the final JAR did not contain `harimt.refmap.json`, even though both Mixin config files explicitly declare it. Exact candidate JAR SHA256 from that run: `73e44a020d1a4bcf81d06e3af9ef51418b32988cf72c19626c977ab7b9059306`.

The merged Forge build had no MixinGradle plugin/refmap source-set configuration and no Sponge Mixin annotation processor, so nothing generated the declared refmap. Fabric had its own refmap setup, but Forge did not.

## Current fix

`apply_mixin_packaging.py` now owns both production requirements:

1. add the standard ForgeGradle-compatible `org.spongepowered:mixingradle:0.7-SNAPSHOT` buildscript plugin and apply `org.spongepowered.mixin`;
2. add `annotationProcessor "org.spongepowered:mixin:${mixin_version}:processor"`;
3. configure `mixin { add sourceSets.main, "${mod_id}.refmap.json" }` so the Forge main compile emits `harimt.refmap.json` while compiling the merged common + Forge Java sources;
4. keep the explicit exact production `MixinConfigs` manifest value on the real Forge `jar` task;
5. keep the final reobfuscated-JAR checks for both config JSON files, `harimt.refmap.json`, `LivingEntityPushMixin.class`, and exact non-empty manifest value.

The temporary runtime diagnostic workflow is removed in the same atomic branch commit so only one development build runs.

## Preserved implementation

All accepted hardening remains unchanged: work stealing/affinity/circuit breaker, one logical CPU reserved, Dragon sync, EntitySection snapshots, tracked-entity serialization, same-tick spawn/random-tick barriers, scheduled-LevelChunk spawn interop, PalettedContainer locking, c2me/c2meforge + SAFE-INTEROP DimThread compatibility, lifecycle cleanup, isolated compile-checked LWJGL Vulkan backend, repaired 9-SSBO/16-byte shader ABI, convergent final workgroup barriers, serialized AABB snapshot/dispatch/pair-map transaction, mixed sync/async world-batch push deferral, complete vanilla fallback, adaptive no-cap pair capacity with learned high-water reuse, and the 192-cow/18,336-pair dense stress fixture.

## Exact next action

Follow only the development workflow for the branch commit containing the refmap-generation fix. The first required milestones are: Forge/backend compile -> final JAR contains exact production Mixin manifest + `harimt.refmap.json` -> real Forge 47.4.23 packaged-server dense Vulkan/fallback/save/restart QA. If that passes, rebuild the inert final promotion commit against the new tested head and run exactly one real client/integrated-server release workflow. If it fails, fetch its completed log/artifact once and patch only the earliest causal owner.
