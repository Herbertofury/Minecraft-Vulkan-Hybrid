# HariMultiThread Ultimate 2.4.10 Vulkan Hybrid source

Minecraft 1.20.1, Forge 47.4.23, Java 17.

`source/` contains the complete merged Gradle project. On Linux/macOS run:

```sh
cd source
chmod +x gradlew
./gradlew --no-daemon clean :forge:build --stacktrace
```

On Windows use `gradlew.bat --no-daemon clean :forge:build --stacktrace`.

The source was freshly reconstructed from the pinned upstream commits with all 24 recipe steps. SOURCE-RECIPE.json hashes every source file. CANDIDATE.json identifies the exact independently reproduced JAR and its compile run. Native compatibility acceptance is provided separately.

`reproduce/` retains every recipe script, helper, overlay and focused regression; `ci/compile.yml` records their order and actual build gates. Upstream licenses are retained inside source/. This archive contains no user worlds, runtime dumps or third-party mod binaries.
