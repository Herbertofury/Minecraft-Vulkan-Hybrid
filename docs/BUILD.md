# Build and verification

Requirements: Java 17 (tested locally with Temurin 17.0.20.1), Python 3, and normal network access to the pinned project's Maven/Gradle repositories. Gradle wrapper is 8.11.1; Minecraft 1.20.1 and Forge 47.4.23 remain pinned. The source/build recipe retains that acceptance pin. At the user's request, the actual Windows A/B uses the latest official Forge 47.4.26 in both isolated Modrinth instances; its runtime compatibility is checked separately.

From the repository root run `python tools/verify_source.py`, then from `source/` run `gradlew.bat --no-daemon --max-workers=4 :forge:build --stacktrace`. Linux/macOS use `./gradlew`. For isolation set `GRADLE_USER_HOME` to a project-specific cache. Four workers limit interference with concurrent projects.

The initial Windows build passed. Its all-JAR SHA256 is `1410671973ada76e20a35f9c469dd2433785f7ee3feede3ac8c9fb5d311bab9d` (30,763,985 bytes), versus the accepted Linux JAR `8b593bac1ac77670849ed992c808d327dd6d9f188ddfdea341ac88f16edf8c9f` (30,763,983 bytes). All top-level class entries match. Six nested-JAR/refmap/metadata entries differ; see `provenance/windows-build-comparison.json`. Do not describe this Windows packaging as byte-identical reproduction. The hardware A/B uses original verified binaries, with identical capture instrumentation.

The original 24-step reconstruction and negative-control regressions remain under `minecraft/async-1.20.1-ultimate/`. `legacy-workflows/async-1.20.1-ultimate-2.4.0-vulkan-hybrid.yml` preserves their exact order, native shader tools, artifact checks and original upstream controls. Historical workflow run IDs refer to ProjectDump and cannot be reused as destination CI IDs. The current destination workflow builds the complete source directly and executes portable focused gates. Linux native compatibility acceptance remains historical unless a new destination run explicitly reruns it.

On Windows run Python regression scripts with `python -X utf8`; Unicode shader tests also require UTF-8 Java process encoding. Linux-native tests retain their Linux scope and are not silently reported as Windows passes.


## Windows Vulkan startup

On this RTX 4090 system with Minecraft's LWJGL core 3.3.1, Vulkan device-extension enumeration exhausts the default 64 KB native memory stack. Set `-Dorg.lwjgl.system.stackSize=1024` in the instance Java arguments before launch. The renderer's later `Configuration.STACK_SIZE.set` cannot change LWJGL's already initialized static default. This is a startup compatibility setting, not a measured FPS optimization; both benchmark versions use it. See [LWJGL configuration](https://javadoc.lwjgl.org/org/lwjgl/system/Configuration.html#STACK_SIZE).

Modrinth App 0.21.6 is the latest installed app. Its upstream Forge catalog (last modified May 23, 2026) still tops out at 47.4.20 after a forced refresh, although [Forge publishes 47.4.26](https://files.minecraftforge.net/net/minecraftforge/forge/index_1.20.1.html). The isolated instances use a checksum-verified official 47.4.26 client installer and Modrinth's supported installed-runtime metadata route, pinned explicitly to 47.4.26. No personal instance or app executable is patched. Existing cache files with differing hashes are never overwritten.

## Native UI renderer continuation

Assemble into a fresh directory with `python integration/dimensions/assemble.py ../hari-native-ui36`, then use Java17 and `source/gradlew -p ../hari-native-ui36 :forge:build --no-daemon --max-workers=2`. On Windows use `source/gradlew.bat`. The reviewed renderer-only output is `harimt-forge-1.20.1-2.4.11-noxviola.36-dimensions-vulkan-hybrid-all.jar`, SHA256 `78c463cd0cf16fa0362df8400e71f6334cf41df4265a5f52fd2d09450890daa7`. [Actual native UI proof and remaining full-pack limitations](../benchmarks/results/native-ui-atlas-20261014/REPORT.md). Keep the logged-in installed Modrinth runtime/Java17 and native stack argument; do not add a separate VulkanMod, Oculus, Indigo, Embeddium or Zink. Full327 compatibility must pass before normal pack launch or matched FPS tests. New standalone frontend regressions use the separate production3.3.3 callback SDK, without replacing Minecraft libraries.

## Native pixel-buffer renderer continuation

The same fresh assembly/build commands above now produce `harimt-forge-1.20.1-2.4.11-noxviola.38-dimensions-vulkan-hybrid-all.jar`, SHA256 `6ed89deeadc70955f43bd70c4165aac461d2d7acadf3d45eee850422522396c3`. It retains all original source hashes and excludes the separate noise/mipmap/FieldGuide performance candidates. [Actual native pixel-buffer GAME tests and remaining full-pack work](../benchmarks/results/native-pixel-buffers-20261014/REPORT.md). The earlier36 instructions/proof remain historical checkpoints. Full327 compatibility remains blocked; do not promote it to personal Noxviola or assert full-pack FPS.

## Native Physics shader renderer continuation

Fresh assembly/build commands above produce `harimt-forge-1.20.1-2.4.11-noxviola.40-dimensions-vulkan-hybrid-all.jar`, SHA256 `8ddf2547a7837545b4f37b4d6902497bb0276d46b1cce22a7f04d2c5269e8a0a`. Java17 and the isolated authenticated installed Modrinth runtime with native stack1024 remain required. [67 actual native GPU cases and remaining full-pack work](../benchmarks/results/native-physics-shaders-20261014/REPORT.md). Exact original Physics shader helper methods pass; complete Physics buffers/geometry/context and full327 remain blocked. Retain the separate `benchmarks/tools/mvh_pack_cli-digest-opt.exe` benchmark helper with [integrity-preserving measured launch gain](../benchmarks/results/launcher-content-sync-20261014/REPORT.md). Earlier36/38 checkpoints remain historical.

## Native Physics buffer renderer continuation

Fresh assembly/build now produces `harimt-forge-1.20.1-2.4.11-noxviola.42-dimensions-vulkan-hybrid-all.jar`, SHA256 `15d2049fc48650deeef36ef7fee0ab39e47b1d76306fcd730843911b33c4223d`. [75 actual native GPU cases and remaining full-pack work](../benchmarks/results/native-physics-buffers-20261014/REPORT.md). Use Java17, the authenticated installed Modrinth runtime/MC1.20.1/Forge47.4.26, native-required configuration and stack1024. Retain the separate optimized helper and complete full327 native compatibility before normal personal-pack promotion or FPS claims. Earlier checkpoints stay preserved.

## Native vertex binding/instance continuation

Fresh assembly/build produces `harimt-forge-1.20.1-2.4.11-noxviola.44-dimensions-vulkan-hybrid-all.jar`, SHA256 `ed730c6e229b64904faf7af57be508035b3ed044bf62869c719fe7913a026436`. [85 actual native GPU cases/limits](../benchmarks/results/native-vertex-bindings-20261014/REPORT.md). Java17, isolated authenticated installed Modrinth MC1.20.1/Forge47.4.26, native-required configuration and stack1024 remain required. Device divisors are queried/optional; no capability spoof. Complete full327 before personal promotion or FPS claims; prior checkpoints remain preserved.

## Original native Physics VAO continuation

Fresh integration assembly and Java17 `:forge:build` now produce `harimt-forge-1.20.1-2.4.11-noxviola.47-dimensions-vulkan-hybrid-all.jar`, SHA256 `0f254f08713ca590205de0dbe1d8e2ace3f82237e000736db96c5c09ed92497e`. [99 actual GPU cases and automatic original GAME VAO proof](../benchmarks/results/native-physics-vao-20261014/REPORT.md). Isolated installed/authenticated Modrinth MC1.20.1/Forge47.4.26, native-required configuration and `-Dorg.lwjgl.system.stackSize=1024` remain the tested launch route. Native constructors/arrays/indexed/instanced draws pass; full327 remains blocked387 references, so full-pack gameplay/FPS and personal promotion are unaccepted. Earlier build artifacts remain preserved.

## Native Essential typed buffer continuation

Fresh assembly and Java17 `:forge:build` produce `harimt-forge-1.20.1-2.4.11-noxviola.49-dimensions-vulkan-hybrid-all.jar`, SHA256 `870aad789ef897c8cd0162ef9d60d5441f624f2dba80430c5eb8f94afdcc6565`. [106 actual native GPU cases and scope](../benchmarks/results/native-essential-buffers-20261014/REPORT.md). The isolated authenticated Modrinth MC1.20.1/Forge47.4.26/native-required/stack1024 route stays tested. Full Essential device and327-mod gameplay remain incomplete; partial buffer transports grant no whole-device gate exemption.

## Native Essential image continuation

Fresh assembly and Java17 `:forge:build` produce `harimt-forge-1.20.1-2.4.11-noxviola.53-dimensions-vulkan-hybrid-all.jar`, SHA256 `42c49354f34a54ee9aa39d53c11c4348353c1c5aa0cdbe90e76ebe7b405faf64`. [116 actual native GPU cases and image scope](../benchmarks/results/native-essential-images-20261014/REPORT.md). Isolated authenticated Modrinth MC1.20.1/Forge47.4.26/native-required/stack1024 remains tested. Actual original typed images, requested mips, physical limits and depth/HDR sampling pass; full framebuffer/provider/327-mod gameplay remains incomplete. Earlier artifacts/default samplers are preserved.

## Native Essential framebuffer continuation

Fresh assembly and Java17 `:forge:build` produce `harimt-forge-1.20.1-2.4.11-noxviola.54-dimensions-vulkan-hybrid-all.jar`, SHA256 `16f1cd2050148c38a459b819a291b8954b2a7c5e16317d06d94313999211d3a6`. [129 actual native GPU cases and original clear scope](../benchmarks/results/native-essential-framebuffers-20261014/REPORT.md). Tested authenticated isolated Modrinth MC1.20.1/Forge47.4.26/native-required/stack1024. Original clipped clears and retained mip/state pass;21 root GL sites/full provider/327-mod gameplay remain incomplete. Earlier artifacts/default framebuffer behavior remain preserved.

## Native Essential integrated provider-call continuation

Fresh assembly and Java17 `:forge:build` produce `harimt-forge-1.20.1-2.4.11-noxviola.55-dimensions-vulkan-hybrid-all.jar`, SHA256 `cf8eda8fd3d22fb6501d0d6be271593020c8aa6f00b8a8fd3f6eabb4535a4eaa`. [141 actual native GPU cases and original integrated chain](../benchmarks/results/native-essential-provider-20261014/REPORT.md), [exact owned reproduction](../benchmarks/results/native-essential-provider-20261014/REPRODUCE.md). Authenticated isolated Modrinth MC1.20.1/Forge47.4.26/native-required/stack1024 is tested. Original uploads/framebuffer actions work; full typed render-pass/provider/automatic GAME/full327 gameplay remains incomplete. No personal promotion or FPS claim.

## Native Vulkan logic-operation and depth-bias repair

Fresh assembly/Java17 Forge build produces Hari56. [164 actual GPU cases and paired defect reproduction](../benchmarks/results/native-raster-state-20261014/REPORT.md), [reproduction and artifact hash](../benchmarks/results/native-raster-state-20261014/REPRODUCE.md). Full typed provider/GAME/full327 gameplay remains unfinished. No personal promotion or FPS claim.

## Native original managed-state GAME continuation

Fresh assembly and Java17 Forge build produce Hari58. [191 actual GPU cases, original GAME state and native blend repair](../benchmarks/results/native-managed-state-20261014/REPORT.md), [reproduction and artifact hash](../benchmarks/results/native-managed-state-20261014/REPRODUCE.md). Full typed provider/delayed-loader/features/full327 gameplay remain incomplete. No personal promotion or FPS claim.
