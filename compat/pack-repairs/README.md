# Private pack repair recipes

These recipes accept only the hash-pinned originals already owned in Noxviola and create fresh output files. Original JARs, geometry, UVs, textures, licenses and every unrelated entry remain intact. The affected mods have restrictive redistribution terms; their assets and patched JARs are not published here.

The Cataclysm compatibility transformer must return each transformed `classNode`; its four missing returns prevented the pack from loading. The Automotives Boilerbox repair fixes a missing JSON comma and wrong namespace/OBJ paths, and supplies the missing material declaration using its original OBJ material and texture. Three JSON entries change; geometry and UVs do not change.

```powershell
python compat/pack-repairs/repair.py transformer "PATH/TO/cataclysm-to-magic-fix-1.1.0.jar" "PRIVATE/NEW/cataclysm-to-magic-fixed.jar"
python compat/pack-repairs/repair.py models "PATH/TO/create-automotives-1.20.1-V2.jar" "PRIVATE/NEW/create-automotives-fixed.jar"
```

The generated manifests record changed entries and hashes. Disable the corresponding original only in a backed-up test instance before using its replacement. Both repairs were used in the baseline and candidate to keep the comparison playable and matched.
