# Renderer alternatives and compatibility baselines

The inspected README is only a project title. This page therefore defines a **research scope**, not an assertion of an implemented hybrid renderer. Recover the exact intended game version, loader, baseline branch and supported mod envelope before selecting or porting a renderer.

| Comparator and primary source | Documented role | Comparison question |
|---|---|---|
| [VulkanMod](https://github.com/xCollateral/VulkanMod) | Vulkan renderer mod for Minecraft | Which rendering paths and extension boundaries are supported for the exact target? Record incompatibilities before a hybrid design. |
| [Sodium](https://github.com/CaffeineMC/sodium) | Minecraft rendering-engine replacement | Can the candidate improve frame-time tails without losing geometry, lighting, particles, resource packs or mod compatibility? |
| [Iris](https://github.com/IrisShaders/Iris) | Shader-pack rendering integration | Treat shader compatibility as a separate workload and correctness constraint, not a free consequence of choosing a graphics API. |
| Vanilla renderer | Required installed-game control | Preserve identical view distance, resolution, scene, camera path and visual settings for every comparison. |

## Milestone: establish a reproducible renderer decision
- [ ] Recover and document the real target and existing implementation before making architecture recommendations.
- [ ] Pin candidate commits, game/loader/runtime versions, GPU/driver and licenses.
- [ ] Use equivalent isolated instances: vanilla, supported Sodium/Iris combination, VulkanMod, and hybrid candidate only when buildable.
- [ ] Capture cold shader compilation, world entry, chunk traversal, frame-time p50/p95/p99, stutter events, RAM/VRAM and GPU/CPU utilization.
- [ ] Validate screenshots and known effects: translucent geometry, lighting, shadows, animated textures, particles and modded entities.
- [ ] Run repeated samples with warm/cold state labeled; retain crashes and visual failures.
- [ ] Reject a faster result that silently disables shaders, lowers visual quality or breaks protected mods.
- [ ] Prefer upstream reuse or a bounded patch over an unproven rewrite. No universal Vulkan speedup is claimed.

## Review and evidence rules

Review date: **2026-11-12**. This is a comparison agenda, not a benchmark result or proof of superiority. Official-source capability descriptions and our proposed experiments are separate. Pin actual versions and licenses before reuse; record publication date separately from the date checked. Retain failed comparisons and rejected candidates.

During recurring reviews, inspect upstream releases, breaking changes, security notices and relevant issue reports; add a dated change note only for a meaningful change. Reuse the existing catalogs instead of creating another disconnected list. A missing observation is **unknown**, not unsupported. No new feature is silently authorized merely by appearing here.

For each proposed adoption, record baseline revision, candidate revision, identical workload, required outputs, correctness/security/compatibility checks, latency distribution, memory and reproducible raw evidence. Never make a target-project superiority claim from a synthetic microbenchmark. Keep private assets and traces out of public evidence.

## Publication status

This repository document is the canonical comparison entry point for this addition. A repository-backed wiki page can link to it without duplicating its tables. Native GitHub Wiki publication must be verified separately; a wiki-enabled setting or a source mirror is not publication proof.
