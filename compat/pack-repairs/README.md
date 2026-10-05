# Private pack repair recipes

These recipes accept only the hash-pinned originals already owned in Noxviola and create fresh output files. Original JARs, geometry, UVs, textures, licenses and every unrelated entry remain intact. The affected mods have restrictive redistribution terms; their assets and patched JARs are not published here.

The Cataclysm compatibility transformer must return each transformed `classNode`; its four missing returns prevented the pack from loading. The Automotives Boilerbox repair fixes a missing JSON comma and wrong namespace/OBJ paths, and supplies the missing material declaration using its original OBJ material and texture. Three JSON entries change; geometry and UVs do not change.

```powershell
python compat/pack-repairs/repair.py transformer "PATH/TO/cataclysm-to-magic-fix-1.1.0.jar" "PRIVATE/NEW/cataclysm-to-magic-fixed.jar"
python compat/pack-repairs/repair.py models "PATH/TO/create-automotives-1.20.1-V2.jar" "PRIVATE/NEW/create-automotives-fixed.jar"
```

The generated manifests record changed entries and hashes. Disable the corresponding original only in a backed-up test instance before using its replacement. Both repairs were used in the baseline and candidate to keep the comparison playable and matched.

The private resource overlay adds eight byte-identical Midnight sign texture aliases, including the original `dark_wilow.png` spelling, and moves two Mowzie Grottol JSON comments out of the texture map. It preserves all original texture pixels, model properties, UVs and geometry. Only the public generator is committed; generated copyrighted assets remain private. The overlay includes Minecraft 1.20.1 `pack.mcmeta` format 15, a change manifest and attribution.

```powershell
python compat/pack-repairs/resource_pack.py "PATH/TO/midnight-custom-original.jar" "PATH/TO/mowziesmobs-1.8.2.jar" "PRIVATE/NEW/Hari-Noxviola-Resource-Repairs-1.0.0.zip"
python tools/verify_pack_metadata.py "PRIVATE/NEW/Hari-Noxviola-Resource-Repairs-1.0.0.zip"
```

Generation verifies whole-JAR hashes, exact PNG bytes/dimensions, reversal of the JSON comment relocation, every written ZIP entry and unchanged sources. Asset correctness and pack metadata pass locally. Actual resource reload and visual validation with the affected mods remain required before promotion.
