# MVH OpenGL texture compatibility

Optional Forge 1.20.1 client repair for GeckoLib **4.8.4**. GPL-3.0-only; authored here, with no GeckoLib source/assets bundled. Requires Forge 47.4.23 or newer. Keep the upstream GeckoLib and existing Molang safety addon unchanged.

The Noxviola render thread stalled in `AutoGlowingTexture.loadTexture` at `Minecraft.submit(textureLookup).get()`, reached through Physics Mod's rendering of a dying Hybrid Aquatic octopus. Ixeris puts rendering on a separate thread. Enqueuing an already render-owned texture lookup onto Minecraft's other executor and immediately waiting can stop frame progress. The captured native JVM stack is the evidence; an empty JVM deadlock report does not rule out a future/executor wait.

This narrow redirect executes that lookup on the current thread only when `RenderSystem.isOnRenderThread()` confirms render ownership. It returns a completed or exceptionally completed future, retaining the original result and exception cause. Calls from other threads still use the original submission. It does not skip glow masks, replace physics, change graphics settings, suppress errors, or pump unrelated queues.

The production SRG target is explicit. The GeckoLib dependency range is intentionally restricted to the inspected version; arbitrary new versions require inspection and testing. The mixin is client-only and uses `@Pseudo` for instances without GeckoLib.

Build from the repository root:

```powershell
.\source\gradlew.bat -p compat/opengl-texture-lookup build --no-daemon
```

Result: `build/libs/mvh-opengl-texture-compat-1.0.0.jar`. Install alongside Hari and GeckoLib in the isolated pack first. This is a compatibility development artifact, not a published release or an FPS guarantee.

The standalone regression preserves the blocked original queue as a negative control, checks single execution on the owner, ordinary off-owner scheduling, result identity and original failure propagation:

```powershell
javac -d compat/opengl-texture-lookup/test-out compat/opengl-texture-lookup/src/main/java/mvhglcompat/TextureLookup.java compat/opengl-texture-lookup/tests/TextureLookupRegression.java
java -cp compat/opengl-texture-lookup/test-out TextureLookupRegression
```

Native full-pack verification completed a Noxviola OpenGL scene capture and clean save/restart on Windows with GeckoLib 4.8.4, Ixeris 4.6.8 and Physics Mod 3.0.20. Broader resource reload and dimension checks are ongoing. No benchmark from a frozen or instrumented diagnostic run is accepted as a performance result.
