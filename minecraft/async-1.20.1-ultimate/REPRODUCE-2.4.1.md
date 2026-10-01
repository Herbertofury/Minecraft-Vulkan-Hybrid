# Reproduce Hari 2.4.1 Vulkan hybrid

Target: Minecraft 1.20.1, Forge 47.4.23, Java 17. The archive's `source/` directory is the complete merged Gradle project; `reconstruction/` holds the versioned overlay recipe. The tested product commit is ba05e9589960f3ab255926c50b007f9d7ce1b4ea.

## Build the complete source

Use a Java 17 JDK and allow Gradle access to the Minecraft, Forge and dependency repositories. From `source/` on Linux/macOS:

```sh
chmod +x gradlew
./gradlew --no-daemon clean :forge:build --stacktrace
```

On Windows:

```bat
gradlew.bat --no-daemon clean :forge:build --stacktrace
```

The distributable Forge JAR is under `forge/build/libs/`. Do not distribute a sources, development or unremapped intermediate JAR. Compare the final bytes with the root release JAR and the SHA-256 in `CHECKPOINT.json`. The accepted CI gate independently rebuilds from clean source and compares both JARs byte for byte.

## Reconstruct immutable upstream inputs

Pinned Hari source: JustHari01/HariMultiThread at f381611c2d71a85192e2028f9e30c03823a6482b. Pinned Forge renderer source: kzktor/Forgified-VulkanMod at 0ceac5d47f84c910d2f5f6d007b0ffe266c6f736.

The exact 23-step ordering, upstream checkout commands, shader compilation, actual-source regressions and packaging gates are in `evidence/workflows/async-1.20.1-ultimate-2.4.0-vulkan-hybrid.yml`. Apply those reconstruction steps from a repository checkout of the canonical branch; they reference `minecraft/async-1.20.1-ultimate/`, corresponding to the archive's `reconstruction/` directory. A fresh reconstruction already matches all 986 complete-source files; see `evidence/SOURCE-RECIPE-REPRODUCTION.json` and `evidence/SOURCE-COMPLETENESS.json` for file hashes.

## Reproduce runtime evidence

The workflows under `evidence/workflows/` retain the exact Forge production profile, PortableMC version/hash, software driver setup, real worlds and optional C2ME fixture. Scripts are under `evidence/qa/`. Production-client gates use `forgeclient`, require actual rendered worlds, resource reload and resize, and reject `forgeclientuserdev`. The native server gates verify entity counts, collision results, saved block-entity inventory and lock data after unload/reload/restart, and deliberate crash recovery. Intentional fault evidence remains separate from normal-run error ledgers.

The extra C2ME challenge uses the exact root JAR with Khronos synchronization validation and safe-world-RNG enforcement. It requires acknowledged real client commands, generated far chunks, returned chunks, central terrain screenshots and an explicit two-worker server fixture. It never disables C2ME safety checks or replaces simulation with fake markers.

Every final archive entry except the manifest itself is hashed in `SHA256SUMS.txt`. Identical current CI JAR copies are represented by the root binary and `BINARY-REFERENCES.json`. Full older diagnostics are retained under `evidence/diagnostics-repaired/` with provenance; they are not current acceptance.
