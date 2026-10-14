#!/usr/bin/env python3
from pathlib import Path
import json
import sys

if len(sys.argv) != 2:
    raise SystemExit("usage: apply_mc263_worldgen_cache.py <merged-upstream-root>")

root = Path(sys.argv[1]).resolve()
if not (root / "gradle.properties").is_file():
    raise SystemExit(f"not a HariMultiThread source root: {root}")


def replace_once(path: Path, old: str, new: str, label: str) -> None:
    text = path.read_text(encoding="utf-8")
    if new in text:
        return
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"source drift in {label}: expected exactly one insertion marker, found {count}")
    path.write_text(text.replace(old, new, 1), encoding="utf-8")


# This layer intentionally keeps Minecraft 1.20.1's double-precision density
# function representation. Minecraft 26.3 moved density functions to floats and
# redesigned interpolation/cache types, but those changes can alter terrain for
# existing seeds. We backport only the representation-neutral cache-fill win.
common_config = root / "common/src/main/java/com/axalotl/async/common/config/AsyncConfig.java"
replace_once(
    common_config,
    '    public static Map.Entry<String, Boolean> enableMc263StructureLocateCache = new AbstractMap.SimpleEntry<String, Boolean>("enableMc263StructureLocateCache", true);\n',
    '    public static Map.Entry<String, Boolean> enableMc263StructureLocateCache = new AbstractMap.SimpleEntry<String, Boolean>("enableMc263StructureLocateCache", true);\n'
    '    public static Map.Entry<String, Boolean> enableMc263DensityCacheFill = new AbstractMap.SimpleEntry<String, Boolean>("enableMc263DensityCacheFill", true);\n',
    "AsyncConfig density cache toggle",
)

forge_config = root / "forge/src/main/java/com/axalotl/async/forge/config/AsyncConfigForge.java"
replace_once(
    forge_config,
    '        private static final ForgeConfigSpec.ConfigValue<Boolean> enableMc263StructureLocateCacheLocal;\n',
    '        private static final ForgeConfigSpec.ConfigValue<Boolean> enableMc263StructureLocateCacheLocal;\n'
    '        private static final ForgeConfigSpec.ConfigValue<Boolean> enableMc263DensityCacheFillLocal;\n',
    "AsyncConfigForge density declaration",
)

structure_definition = '''                enableMc263StructureLocateCacheLocal = BUILDER.comment(\n                                "Backport the safe portion of Minecraft 26.3 structure-locate caching. Remembers chunks " +\n                                "whose structure metadata scan returned no stored structure data, avoiding repeated NBT/region " +\n                                "reads while preserving vanilla generation checks. Cache entries are invalidated when structure " +\n                                "data is loaded or references change.")\n                                .define("enableMc263StructureLocateCache", enableMc263StructureLocateCache.getValue());\n'''
density_definition = structure_definition + '''\n                enableMc263DensityCacheFillLocal = BUILDER.comment(\n                                "Use a 26.3-style cache-aware bulk-fill path for Minecraft 1.20.1 Cache2D density functions. " +\n                                "This keeps double-precision world generation and changes no density formula; it prevents " +\n                                "bulk fills from bypassing the existing X/Z cache. Automatically yielded to HariChunk/C2ME.")\n                                .define("enableMc263DensityCacheFill", enableMc263DensityCacheFill.getValue());\n'''
replace_once(forge_config, structure_definition, density_definition, "AsyncConfigForge density definition")
replace_once(
    forge_config,
    '                enableMc263StructureLocateCache.setValue(enableMc263StructureLocateCacheLocal.get());\n',
    '                enableMc263StructureLocateCache.setValue(enableMc263StructureLocateCacheLocal.get());\n'
    '                enableMc263DensityCacheFill.setValue(enableMc263DensityCacheFillLocal.get());\n',
    "AsyncConfigForge density load",
)
replace_once(
    forge_config,
    '                enableMc263StructureLocateCacheLocal.set(enableMc263StructureLocateCache.getValue());\n',
    '                enableMc263StructureLocateCacheLocal.set(enableMc263StructureLocateCache.getValue());\n'
    '                enableMc263DensityCacheFillLocal.set(enableMc263DensityCacheFill.getValue());\n',
    "AsyncConfigForge density save",
)

# Cache2D.compute already memoizes by X/Z in vanilla 1.20.1, but fillArray()
# bypasses that cache and delegates straight to the wrapped function. Route bulk
# fills through compute() instead. This preserves the existing double values and
# cache key semantics while avoiding repeated wrapped-function evaluation.
cache_mixin = root / "common/src/main/java/com/axalotl/async/common/mixin/world/NoiseChunkCache2DMixin.java"
cache_mixin.parent.mkdir(parents=True, exist_ok=True)
cache_mixin.write_text('''package com.axalotl.async.common.mixin.world;\n\nimport com.axalotl.async.common.config.AsyncConfig;\nimport net.minecraft.world.level.levelgen.DensityFunction;\nimport org.spongepowered.asm.mixin.Mixin;\nimport org.spongepowered.asm.mixin.injection.At;\nimport org.spongepowered.asm.mixin.injection.Inject;\nimport org.spongepowered.asm.mixin.injection.callback.CallbackInfo;\n\n/**\n * Cache-aware bulk fill for the 1.20.1 NoiseChunk Cache2D wrapper.\n *\n * Minecraft 26.3 replaced the legacy specialized density caches with a unified\n * cache model. This backports the safe part: bulk evaluation must pass through\n * the cache instead of bypassing it. No float-density conversion is performed.\n */\n@Mixin(targets = "net.minecraft.world.level.levelgen.NoiseChunk$Cache2D")\npublic abstract class NoiseChunkCache2DMixin implements DensityFunction {\n    @Inject(method = "fillArray", at = @At("HEAD"), cancellable = true)\n    private void harimt$mc263CacheAwareFill(double[] values, DensityFunction.ContextProvider contextProvider, CallbackInfo ci) {\n        if (!AsyncConfig.enableMc263DensityCacheFill.getValue()) {\n            return;\n        }\n        contextProvider.fillAllDirectly(values, (DensityFunction)(Object)this);\n        ci.cancel();\n    }\n}\n''', encoding="utf-8")

# C2ME/HariChunk owns the chunk-generation scheduler and frequently transforms
# the same worldgen internals. Yield this narrow cache patch when that provider is
# present instead of forcing mixin priority fights. Noisium 1.20.1 touches
# NoiseChunkGenerator/ChunkSection rather than Cache2D, so it remains additive.
plugin = root / "common/src/main/java/com/axalotl/async/common/mixin/utils/SynchronisePlugin.java"
plugin_marker = '''        if (mixinClassName.endsWith(".vmp.VMPChunkMapMixin")) {\n            return AsyncCommon.HARIPLAYER;\n        }\n'''
plugin_insert = plugin_marker + '''        if (mixinClassName.endsWith(".world.NoiseChunkCache2DMixin")) {\n            return !AsyncCommon.HARICHUNK;\n        }\n'''
replace_once(plugin, plugin_marker, plugin_insert, "SynchronisePlugin C2ME worldgen ownership")

mixin_json = root / "common/src/main/resources/harimt.common.mixins.json"
data = json.loads(mixin_json.read_text(encoding="utf-8"))
entry = "world.NoiseChunkCache2DMixin"
if entry not in data["mixins"]:
    anchor = "world.StructureCheckMixin"
    try:
        idx = data["mixins"].index(anchor) + 1
    except ValueError as exc:
        raise SystemExit(f"source drift: {anchor} missing; apply_mc263_backports.py must run first") from exc
    data["mixins"].insert(idx, entry)
mixin_json.write_text(json.dumps(data, indent=2) + "\n", encoding="utf-8")

required = {
    common_config: ["enableMc263DensityCacheFill"],
    forge_config: ["enableMc263DensityCacheFillLocal"],
    cache_mixin: ["fillAllDirectly", "enableMc263DensityCacheFill"],
    plugin: ["NoiseChunkCache2DMixin", "!AsyncCommon.HARICHUNK"],
    mixin_json: ["world.NoiseChunkCache2DMixin"],
}
for path, tokens in required.items():
    text = path.read_text(encoding="utf-8")
    missing = [token for token in tokens if token not in text]
    if missing:
        raise SystemExit(f"26.3 density-cache invariant failed in {path}: missing {missing}")

print("HariMultiThread Minecraft 26.3 cache-aware density fill applied successfully")
