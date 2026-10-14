# Exact Ponder stencil bridge

Pinned Create 6.0.8 bundles Ponder 1.0.91. Its original `StencilElement` has four `GL11.glEnable/glDisable` instructions, all with literal `GL_STENCIL_TEST`. This recipe changes only their owner to the original bridge, preserving stencil comparison/fail/write operations, geometry, clear, flush and cleanup. The bundled Ponder `pack_format` changes from 8 to 15; its description and assets remain intact. All other original entries are checked byte for byte. Originals are never overwritten.

Hari `.13` repairs the native pipeline's disabled stencil state and missing dynamic-rendering stencil attachment format. Its actual GPU diagnostic compares all 1,024 pixels in each of three cases: clipped replacement, masked writes preserving upper bits, and unclipped drawing after disable. `.12` fails both clipping cases with 768 wrong pixels; `.13` matches every pixel in all three. The repaired **original Ponder component's default `render` method** also passes native clipping readback, with exactly two enable/two disable calls and stencil disabled afterward. Its hierarchy runs in a restricted, byte-pinned disposable diagnostic loader; this is not a complete Ponder/Create mod launch. Full Ponder requires Flywheel, whose native engine remains unfinished.

The bridge asserts render ownership, accepts only the exact stencil capability and uses Hari's selected renderer. Native sessions do not initialize OpenGL. OpenGL fallback lazily invokes the original GL11 method and preserves error identity. Behavior mutation tests cover capability, owner, selection and eager linkage; the ASM repair rejects unknown call owner/site/count/capability. The original complete Create scan goes from 137 unsupported references/118 APIs to 135/116, all remaining sites in Flywheel. No gate or general GL method contract is relaxed.

Build the helper after `source/forge/build`:

```powershell
.\source\gradlew.bat -p compat/ponder-stencil build
```

For another local Hari build, pass `-PmvhHariReference=C:/absolute/path/to/harimt-all.jar`. The helper is a library, not a standalone mod. `recipe.py ORIGINAL_CREATE.jar FRESH_OUTPUT_CREATE.jar --helper HELPER.jar --asm-classpath ASM_JARS` creates a recoverable private candidate. `--standalone FRESH_PONDER.jar` exports the same repaired component for bounded diagnostics. Do not add standalone Ponder alongside Create's bundled copy. The recipe verifies outer, nested and class SHA256 pins and rejects signed or changed inputs.

[Actual GPU/grass evidence and limits](../../benchmarks/results/cumulative-foundation-20261005/native-stencil-SUMMARY.json) and [remaining Create/Flywheel requirements](../vulkan-shaders/CREATE-FLYWHEEL-STATUS.md) are retained. This repair has no accepted full-pack, contraption, blueprint, shader-pack or zero-hitch result.

Original code: GPL-3.0-only, [license](LICENSE). Ponder: MIT, [preserved notice](LICENSE-UPSTREAM-PONDER-MIT.txt), official [source](https://github.com/Creators-of-Create/Ponder/tree/09e008a1406737d2a13beb7d36503a7e6d80258d). No upstream Create/Ponder binary is published in this repository.
