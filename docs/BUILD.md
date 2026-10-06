# Build and verification

Requirements: Java 17 (tested locally with Temurin 17.0.20.1), Python 3, and normal network access to the pinned project's Maven/Gradle repositories. Gradle wrapper is 8.11.1; Minecraft 1.20.1 and Forge 47.4.23 remain pinned. The source/build recipe retains that acceptance pin. At the user's request, the actual Windows A/B uses the latest official Forge 47.4.26 in both isolated Modrinth instances; its runtime compatibility is checked separately.

From the repository root run `python tools/verify_source.py`, then from `source/` run `gradlew.bat --no-daemon --max-workers=4 :forge:build --stacktrace`. Linux/macOS use `./gradlew`. For isolation set `GRADLE_USER_HOME` to a project-specific cache. Four workers limit interference with concurrent projects.

The initial Windows build passed. Its all-JAR SHA256 is `1410671973ada76e20a35f9c469dd2433785f7ee3feede3ac8c9fb5d311bab9d` (30,763,985 bytes), versus the accepted Linux JAR `8b593bac1ac77670849ed992c808d327dd6d9f188ddfdea341ac88f16edf8c9f` (30,763,983 bytes). All top-level class entries match. Six nested-JAR/refmap/metadata entries differ; see `provenance/windows-build-comparison.json`. Do not describe this Windows packaging as byte-identical reproduction. The hardware A/B uses original verified binaries, with identical capture instrumentation.

The original 24-step reconstruction and negative-control regressions remain under `minecraft/async-1.20.1-ultimate/`. `legacy-workflows/async-1.20.1-ultimate-2.4.0-vulkan-hybrid.yml` preserves their exact order, native shader tools, artifact checks and original upstream controls. Historical workflow run IDs refer to ProjectDump and cannot be reused as destination CI IDs. The current destination workflow builds the complete source directly and executes portable focused gates. Linux native compatibility acceptance remains historical unless a new destination run explicitly reruns it.

On Windows run Python regression scripts with `python -X utf8`; Unicode shader tests also require UTF-8 Java process encoding. Linux-native tests retain their Linux scope and are not silently reported as Windows passes.


## Windows Vulkan startup

On this RTX 4090 system with Minecraft's LWJGL core 3.3.1, Vulkan device-extension enumeration exhausts the default 64 KB native memory stack. Set `-Dorg.lwjgl.system.stackSize=1024` in the instance Java arguments before launch. The renderer's later `Configuration.STACK_SIZE.set` cannot change LWJGL's already initialized static default. This is a startup compatibility setting, not a measured FPS optimization; both benchmark versions use it. See [LWJGL configuration](https://javadoc.lwjgl.org/org/lwjgl/system/Configuration.html#STACK_SIZE).

Modrinth App 0.21.6 is the latest installed app. Its upstream Forge catalog (last modified May 23, 2026) still tops out at 47.4.20 after a forced refresh, although [Forge publishes 47.4.26](https://files.minecraftforge.net/net/minecraftforge/forge/index_1.20.1.html). The isolated instances use a checksum-verified official 47.4.26 client installer and Modrinth's supported installed-runtime metadata route, pinned explicitly to 47.4.26. No personal instance or app executable is patched. Existing cache files with differing hashes are never overwritten.
