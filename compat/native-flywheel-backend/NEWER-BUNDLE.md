# Native newer Flywheel bundled by CasualSwing

Original CasualSwing0.1.0 bundles official Flywheel1.0.6-beta-266 and Ponder1.0.91 alongside Create's1.0.5 copy. The original newer bundle contributes137 unsupported references to the whole-profile native gate. This port replaces its internal legacy Flywheel renderer with the real measured stable14 NativeEngine, compiled against the exact newer API. The duplicate original Ponder receives the exact previously GPU-tested stencil component. The gate, supported-call contract and native-required policy remain unchanged.

Every CasualSwing gameplay class/asset, its version/selection metadata and MixinExtras library remains byte-identical. Newer Flywheel API/library/assets/model-baking changes are preserved, including its ambient-occlusion material contract and original encoder. Only backend implementation and frontend debug engine/device counters change. The Ponder repair retains all original entries except four known stencil instruction owners and format15 metadata. Original/private JARs remain recoverable; no restricted game/mod archive is published here.

The actual installed Hari23 gate now flags532 references/263 APIs across21 archives, down from669/360/22. The exact set difference is all137 CasualSwing references; every other flag stays unchanged. This is static compatibility progress, not actual newer-engine gameplay/visual/FPS acceptance. Native fullpack remains blocked; no game/OpenGL was launched and no optimizer/settings/gameplay mod was changed to manufacture a result. [Source/artifact/proof and limits](../../benchmarks/results/casualswing-native-20261006/REPORT.md).

The official Forge0.3.19 JarSelector regression reads actual original/repaired archives without loading mod classes. In the tested Create+CasualSwing order it selects the already-native Create1.0.5; the newer-only case selects1.0.6-beta-266. Both source orders and newer-only now resolve a real native factory and the accepted stencil bridge. The newer copy is not assumed to always win, and no causal FPS gain follows from removing its conservative scanner flags.

## Reproduce the private build

Use Java17, a complete clone retaining feature commit`e873da9c5b2baafd5a28ebcb67d26dd5646bbc7b`, and a fresh output directory. The helper uses that retained measured engine source rather than adding the unmeasured small15 packing optimization. Reference downloads are exact official [Flywheel binary/sources](https://maven.createmod.net/dev/engine-room/flywheel/flywheel-forge-1.20.1/1.0.6-beta-266/), both SHA256-verified before output. Original/adapted upstream namespaces retain MIT; new native code retains GPL-3.0-only and the recipes embed both notices.

```powershell
python compat/native-flywheel-backend/download_newer_reference.py reference/newer.jar reference/newer-sources.jar
python compat/native-flywheel-backend/prepare_newer.py reference/newer-sources.jar ../newer-native-build
.\source\gradlew.bat -p ../newer-native-build build `
  -PmvhHariReference=C:/absolute/path/to/harimt-native-required23-all.jar `
  -PmvhFlywheelReference=C:/absolute/path/to/reference/newer.jar `
  --no-daemon --max-workers=4
python compat/native-flywheel-backend/recipe_newer.py ORIGINAL_CASUALSWING.jar `
  ../newer-native-build/build/libs/mvh-flywheel-native-backend-0.1.0-native-engine.jar `
  FRESH_PRIVATE_CASUALSWING_NATIVE.jar --native-ponder EXACT_TESTED_PONDER.jar
```

`EXACT_TESTED_PONDER.jar` is exported from the validated private native Create14 archive; its SHA256 is`a3239fb968d8c059eecd502c05ca2daf826a33f4e66977530e4ab6fc4e92f5c7`. Unknown components require independent validation rather than weakened pins. Without that argument the Flywheel port alone retains the two original Ponder flags. Existing [Ponder source/recipe/GPU proof](../ponder-stencil/README.md) stays preserved. Use the strict owned preflight before any game launch; it still rejects the current complete pack.

The regression`tests/test_newer_selection.py` accepts actual private native Create/original CasualSwing/native CasualSwing and a classpath of the already installed official Forge JarJarSelector/Metadata0.3.19, Maven artifact3.8.5, Gson2.10, Guava31.1-jre, NoException1.7.1, CommonsLang3.12.0 and SLF4J1.7.30. Its report pins every dependency hash. It never loads the tested mod classes. Retained production instance/light controls also pass5,019 instance checks/18 light checks and reject eight unsafe mutations. Runtime parity, fullpack1500/2400FPS and hitch-free play remain separate work.
