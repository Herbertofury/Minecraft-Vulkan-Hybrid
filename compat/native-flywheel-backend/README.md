# Native Flywheel engine for Hari

Hari `.22` and this library run original Create 6.0.8 machinery through Flywheel 1.0.5's real `Engine` and `Instancer` interfaces using native Vulkan. Actual Minecraft1.20.1 / Forge47.4.26 on this RTX4090 passed powered motors/shafts/fans, resource reload and re-registration, original Ponder playback and seeking, original Create config-screen color copies and stencil clipping, UI return, and GPU culling with 16 visible instances becoming zero behind the camera and returning to16. This is bounded machinery/UI acceptance; arbitrary contraptions, blueprints, material combinations and complete Noxviola remain unaccepted. Active Oculus/Iris shaderpacks, full OIT and depth-pyramid occlusion are unfinished.

The private recipe replaces the internal legacy OpenGL renderer inside the pinned original Create archive. Every original public API, library and asset is retained. Two original debug frontend probes are adapted to the real engine; original visualization plans, registrations, content, workers and events remain. The recipe checks class-reference closure, hashes, original assets, licenses and `pack_format:15`. It refuses overwritten outputs. Original mod archives are neither overwritten nor published. The production compatibility gate and supported-call contract are unchanged.

## Rendering and ownership

Original 36-byte full vertices, instance layouts/writers, models/bounds, materials and shader resources feed actual native storage, vertex/index buffers and indexed indirect draws. Original generic instance cull and apply shaders run as compute with host/compute/vertex/indirect barriers. Current culling is conservative frustum culling; no depth-pyramid occlusion is claimed. Root/nested contexts, embedding transforms, stable handles, instance transfers/deletion/visibility, render origins, bias, light collection, structured uniforms and breaking materials are implemented. Arbitrary contraptions and breaking overlays are not accepted merely because that code compiles.

The native provider retains the original Flywheel decision only when the selected registry object is this provider. Real Hari chunk registration and resource-reload hooks restore original machinery. Epochs identify new command-buffer submissions. Persistent meshes/shaders/materials have render-thread ownership; borrowed Minecraft images stay with their owners. Frame snapshots, descriptors, feedback and retired allocations are reused/released only after their Hari frame fence.

Original light LUT/section data uses per-frame revision caching. Unchanged data reuses the exact snapshot. Changes within one submission allocate an immutable new version, retained until the fence. Capacity growth and failed allocations preserve ownership. The regression compiles actual production methods against a checked memory allocator and rejects four broken variants. The shaft/fan fixture primarily exercises original packed instance lighting; it does not establish every original light-volume shader/material combination. `ORDER_INDEPENDENT` uses the original API's permitted `TRANSLUCENT` fallback; full OIT remains work.

## Framebuffer and original UI

Hari `.22` separates logical READ/DRAW bindings, preserves them during native render-pass suspension and recognizes the actual positive Minecraft main framebuffer name (2 in the accepted run). Color copies issue `vkCmdBlitImage` on the same queue outside the pass, restore layouts and resume a compatible LOAD pass. Main/offscreen Y conventions, scaling, reversal, scissor and named binding preservation are handled. The bounded adapter rejects aliased images, unsupported usage/format/filter and fractional clipped mappings; complete OpenGL blit-contract support is not claimed.

Six GPU readback cases each checked512 pixels with zero mismatches: nearest scaling, source-X reversal, scissor preservation, named source-Y reversal with unchanged bindings, EXT callback and linear scaling. GL30 used advertised original LWJGL calls. The context advertises GL3.2; named/EXT cases without a capability pointer invoked only the bounded registry callback through official LWJGL JNI. They do not establish advertised GL4.5/extension support. Eighteen actual CPU clipping controls reject three mutations. Screenshots are captured before Vulkan presentation and show original Ponder and the menu's intended cog shadow.

All eleven checks are diagnostic: no FPS/startup/zero-hitch claim follows. A separate powered-machinery ABBAAB comparison changes only the cached/uncached backend, preserving other mod bytes, pristine world, camera/settings and warmup. See [actual evidence and limits](../../benchmarks/results/cumulative-foundation-20261005/REPORT.md).

## Build a private candidate

Use Java17, Python3 and the tested integrated Hari. Fetch the exact official dependency independently of personal launcher data:

```powershell
python compat/native-flywheel-backend/download_reference.py reference/flywheel-forge-1.20.1-1.0.5.jar
.\source\gradlew.bat -p compat/native-flywheel-backend build `
  -PmvhHariReference=C:/absolute/path/to/harimt-all.jar `
  -PmvhFlywheelReference=C:/absolute/path/to/reference/flywheel-forge-1.20.1-1.0.5.jar
python compat/native-flywheel-backend/tests/test_light_snapshot.py
python integration/dimensions/tests/test_framebuffer_blit.py
```

The helper is a backend library, not a standalone mod. Prepare a fresh input with [the exact original Ponder stencil recipe](../ponder-stencil/README.md), then:

```powershell
python compat/native-flywheel-backend/recipe.py `
  ORIGINAL_CREATE_WITH_EXACT_PONDER_REPAIR.jar `
  compat/native-flywheel-backend/build/libs/mvh-flywheel-native-backend-0.1.0-native-engine.jar `
  FRESH_PRIVATE_CREATE_NATIVE_CANDIDATE.jar
```

In a disposable Modrinth profile use one integrated Hari `.22`, privately repaired Create, original licensed dependencies, original Grassier Grass and helper1.0.3. Java17 / Forge47.4.26 and `-Dorg.lwjgl.system.stackSize=1024` are required. Avoid duplicate Hari/DimThreads/VulkanMod. The exact-marker controller belongs only in the task-owned test profile: it creates fixtures, moves the camera, saves and exits. It is excluded from desktop development packages. Exact original active hashes are restored after tests. Personal Noxviola/worlds and other projects remain untouched.

Original Flywheel is MIT, pinned to [610b1683](https://github.com/Engine-Room/Flywheel/tree/610b1683f3ed0fef5cd387a126bd2c530a9c2ead), official1.0.5 checksum `316ca250f19244956b5f0cd75329309ea65a77b4b8da854389b6a9222e7f427c`. New native code is GPL-3.0-only. Both notices accompany candidates. See [UPSTREAM.json](UPSTREAM.json), [MIT](FLYWHEEL-LICENSE-MIT.txt) and [GPL](LICENSE). Original source and accepted `.16` evidence remain preserved.
