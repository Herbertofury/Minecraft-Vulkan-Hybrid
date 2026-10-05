# Native TRender scissor compatibility

Source-built Forge 1.20.1 / Java 17 helper and recoverable recipe for official EntityCulling 1.11.2. This is a library embedded by the recipe, not a standalone installed performance mod. Own source is GPL-3.0-only; upstream TRender/CottonMC/LibGui MIT licensing and original content remain unchanged in the private input.

The original official JAR, SHA256 `5d5dc228dc18422fd8a81cff63e654abb07ca4507b317db114b220aa3fc67d3b`, selects Hari's OpenGL fallback because its bundled TRender settings screen calls `GL11.glEnable`, `glDisable` and `glScissor` directly. That isolated original trial measured 574.32 FPS. The ordinary renderer gate remains unchanged.

The recipe pins the outer JAR, nested TRender 1.0.17 JAR and both exact class hashes. Only four invocation owners change: one enable, one disable and the two scissor branches. Descriptors, literal scissor capability, coordinate/intersection calculation, flush ordering, stack, widgets/configuration and every original asset stay intact. Unexpected call sites, capabilities/counts, signed/previously patched or changed input are rejected. Source and output must differ and outputs must be fresh. Original contents are independently compared after rebuilding; the original JAR is rehashed unchanged.

The helper uses Minecraft's owner-checked GlStateManager operations. Hari's existing Vulkan state and command buffer handle them in native mode; the normal state manager handles the OpenGL fallback. No GL method is hidden or silently discarded by the safety gate. The actual helper passes 1,000 state/coordinate cycles, unknown-capability rejection and original failure/owner checks; dropped enable and removed owner protection fail the negative controls.

The actual native game passed nine bounded checks with original grass retained, including resource reload, animation/trails, FOV change, dimension roundtrip and opening/closing EntityCulling's real settings screen. It recorded **6,144 enables, 6,144 disables and 18,432 native scissor-box calls**, then resumed the native grass world without a scissor-state leak. The reviewed UI screenshot retains settings, text, checkboxes, tooltip and scroll bounds. These checks do not establish every UI interaction, dense occlusion correctness or performance benefit. Six matched absent/present performance captures determine retention separately; compatibility alone is not a performance promotion.

Build the helper from the repository root with Java 17:

```powershell
.\source\gradlew.bat -p compat/trender-scissor build --no-daemon --max-workers=4
python compat/trender-scissor/recipe.py path/to/original-entityculling.jar path/to/fresh-private-output.jar --helper compat/trender-scissor/build/libs/mvh-trender-scissor-library-1.0.0.jar --asm-classpath 'path/to/asm-9.7.1.jar;path/to/asm-tree-9.7.1.jar'
```

The helper and private output carry pack format 15. The generated mod stays private, preserving upstream attribution and assets. Source checks, game UI compatibility and measured performance remain separate from complete Noxviola/Oculus/Iris acceptance. Personal Noxviola is unchanged.

The completed six-run sole-mod native study measured absent/present medians 2,561.15/2,614.34 FPS, with overlapping ranges. Compatibility is demonstrated within the recorded UI checks, but a reliable performance benefit is not accepted. Dense occlusion scenes remain untested; the repaired mod is disabled only in the owned continuation until its purpose benefit is established. Original and repaired files are preserved. [Actual runtime and retention evidence](../../benchmarks/results/cumulative-foundation-20261005/REPORT.md).
