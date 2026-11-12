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


## Potential foundation choices and reuse boundaries

The first decision is whether a hybrid renderer is justified at all. The repository's minimal inspected README is insufficient evidence for a working baseline or a rewrite recommendation.

| Potential foundation | Specific reuse to investigate | Improvement hypothesis | Mandatory disqualifiers / evidence |
|---|---|---|---|
| VulkanMod | Inspect supported backend, resource lifecycle and Minecraft integration boundaries | A bounded upstream patch or adapter may achieve the goal with less divergence than a new renderer | Pin game/loader/driver versions; reject if required rendering hooks or mod content are lost. Require real scene comparisons. |
| Sodium | Study chunk scheduling, rendering contracts and performance methodology | Preserve a strong existing renderer as control and identify the actual bottleneck first | Do not combine incompatible renderer replacements blindly. Compare equivalent geometry, camera routes and visual settings. |
| Iris | Inspect shader-pack compatibility contracts as a separate reference | Preserve the user's shader experience while evaluating any backend change | Compatibility is not established by API similarity. Test actual permitted packs, effects, failures and resource reloads. |
| Existing Minecraft test/evidence tools | Investigate a versioned receipt interface for captured runs | Avoid another disconnected benchmark harness | A possible Vault TestGrid integration must preserve rendered-client identity; server-only success is not renderer proof. No shared implementation is recommended before comparison. |

**Licensing gate:** no code copy, port or distribution until exact pinned source and dependency licenses, notices and compatibility are inspected. Link to upstream rather than assuming public source is unrestricted. Shader packs and game assets retain their own distribution restrictions.

**Decision sequence:** recover baseline → locate bottleneck → assess reuse and license → isolate prototype → compare visual correctness and resource behavior → retain failures → accept a narrowly proven improvement or reject it. Vulkan, Rust or a new framework alone does not establish faster rendering.

**Current evidence:** source-linked candidate roles only. No hybrid build, renderer integration, performance win or supported-mod matrix was produced in this pass.

## Review and evidence rules

Review date: **2026-11-12**. This is a comparison agenda, not a benchmark result or proof of superiority. Official-source capability descriptions and our proposed experiments are separate. Pin actual versions and licenses before reuse; record publication date separately from the date checked. Retain failed comparisons and rejected candidates.

During recurring reviews, inspect upstream releases, breaking changes, security notices and relevant issue reports; add a dated change note only for a meaningful change. Reuse the existing catalogs instead of creating another disconnected list. A missing observation is **unknown**, not unsupported. No new feature is silently authorized merely by appearing here.

For each proposed adoption, record baseline revision, candidate revision, identical workload, required outputs, correctness/security/compatibility checks, latency distribution, memory and reproducible raw evidence. Never make a target-project superiority claim from a synthetic microbenchmark. Keep private assets and traces out of public evidence.

## Publication status

This repository document is the canonical comparison entry point for this addition. A repository-backed wiki page can link to it without duplicating its tables. Native GitHub Wiki publication must be verified separately; a wiki-enabled setting or a source mirror is not publication proof.
