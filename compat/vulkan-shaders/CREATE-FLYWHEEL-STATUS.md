# Create and Flywheel native transition

Actual cumulative stage 16 rendered through Vulkan at 2,569.32 FPS. Adding only original Create 6.0.8 (bundled Flywheel 1.0.5 and Ponder 1.0.91) selected OpenGL at 498.99 FPS. The world loaded, recorded, saved and exited normally; a low-performing renderer transition remains unresolved. TaCZ and later additions are paused.

The production gate's standalone byte inspection found 137 unsupported call sites across 118 distinct API descriptors, matching the runtime gate's 12 displayed examples plus 125 remaining sites. It reads pinned original/nested JARs without executing their classes. [Full call inventory](../../benchmarks/results/cumulative-foundation-20261005/proofs/create-native-gl-gate-inventory.json), [actual captures and limits](../../benchmarks/results/cumulative-foundation-20261005/REPORT.md), and [reproducible scanner](../../tools/audit_gl_gate_inputs.py) are retained. Constant-pool presence alone does not prove every path executes.

Required native interoperation includes shader compilation/linking and uniform reflection, complete vertex/instance formats, indirect and compute submission/barriers, persistent buffer lifetime, image/framebuffer attachments and Ponder stencil clipping. Current generic shader/vertex-array adapters do not implement that complete contract. No gate forcing, method allowlisting, stencil omission or backend disable is accepted as a feature-preserving fix.

Checked 2026-10-05 against primary sources:

- [VulkanMod Create Compat, pinned MIT source](https://github.com/xuyife/VulkanMod-Create-Compat/tree/58cb7ec3755783fffd6328e1f7630c5e7b3adcdd) targets Fabric 1.20.1 and explicitly documents missing handheld blueprint preview and stencil borders. It is not installed as a full-feature repair.
- [CrankShaft native Vulkan branch](https://github.com/Warfactory-Official/CrankShaft/tree/a409ea9c66ff04c8bcc007fcffbe3ade610977d7) targets Minecraft 26.2, NeoForge/Fabric and Java 25, using the host's native Vulkan renderer. Its Iris shader path still requires OpenGL. [Master](https://github.com/Warfactory-Official/CrankShaft/tree/08aa362e32b188e80917773ba905ec6a3b1f63c2) targets Cleanroom 1.12.2. Neither supplies the current Forge 1.20.1/Java 17 runtime contract.

These sources are inspected as references; no third-party executable/build is run. Original Create and personal Noxviola remain unchanged. A functioning port and actual machine/contraption/Ponder/blueprint/shader scenes are still required before feature/FPS acceptance.

The native `.13` stencil pipeline and exact Ponder bridge now pass actual GPU readback, including the original component default-method lifecycle. [Repair/evidence](../ponder-stencil/README.md) describes the bounded scope and resource-format correction. The private repaired Create byte scan retains135 untranslated Flywheel references across116 APIs, so cumulative stage17 remains unresolved. No complete machine, contraption, blueprint or shader-pack result is accepted.
