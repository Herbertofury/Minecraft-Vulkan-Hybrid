#!/usr/bin/env python3
from pathlib import Path
import json
import sys

if len(sys.argv) != 2:
    raise SystemExit("usage: apply_mc263_region_read.py <merged-upstream-root>")

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


common_config = root / "common/src/main/java/com/axalotl/async/common/config/AsyncConfig.java"
replace_once(
    common_config,
    '    public static Map.Entry<String, Boolean> enableMc263StructureLocateCache = new AbstractMap.SimpleEntry<String, Boolean>("enableMc263StructureLocateCache", true);\n',
    '    public static Map.Entry<String, Boolean> enableMc263StructureLocateCache = new AbstractMap.SimpleEntry<String, Boolean>("enableMc263StructureLocateCache", true);\n'
    '    public static Map.Entry<String, Boolean> enableMc263NonCreatingRegionReads = new AbstractMap.SimpleEntry<String, Boolean>("enableMc263NonCreatingRegionReads", true);\n',
    "AsyncConfig non-creating region read toggle",
)

forge_config = root / "forge/src/main/java/com/axalotl/async/forge/config/AsyncConfigForge.java"
replace_once(
    forge_config,
    '        private static final ForgeConfigSpec.ConfigValue<Boolean> enableMc263StructureLocateCacheLocal;\n',
    '        private static final ForgeConfigSpec.ConfigValue<Boolean> enableMc263StructureLocateCacheLocal;\n'
    '        private static final ForgeConfigSpec.ConfigValue<Boolean> enableMc263NonCreatingRegionReadsLocal;\n',
    "AsyncConfigForge non-creating region declaration",
)

structure_definition = '''                enableMc263StructureLocateCacheLocal = BUILDER.comment(\n                                "Backport the safe portion of Minecraft 26.3 structure-locate caching. Remembers chunks " +\n                                "whose structure metadata scan returned no stored structure data, avoiding repeated NBT/region " +\n                                "reads while preserving vanilla generation checks. Cache entries are invalidated when structure " +\n                                "data is loaded or references change.")\n                                .define("enableMc263StructureLocateCache", enableMc263StructureLocateCache.getValue());\n'''
region_definition = structure_definition + '''\n                enableMc263NonCreatingRegionReadsLocal = BUILDER.comment(\n                                "Backport Minecraft 26.3 non-creating region reads used by structure locating. Missing .mca regions " +\n                                "are remembered in a bounded cache and read/scan calls return empty without creating files. " +\n                                "The cache is invalidated before any chunk write, preserving normal world generation and saves.")\n                                .define("enableMc263NonCreatingRegionReads", enableMc263NonCreatingRegionReads.getValue());\n'''
replace_once(forge_config, structure_definition, region_definition, "AsyncConfigForge non-creating region definition")
replace_once(
    forge_config,
    '                enableMc263StructureLocateCache.setValue(enableMc263StructureLocateCacheLocal.get());\n',
    '                enableMc263StructureLocateCache.setValue(enableMc263StructureLocateCacheLocal.get());\n'
    '                enableMc263NonCreatingRegionReads.setValue(enableMc263NonCreatingRegionReadsLocal.get());\n',
    "AsyncConfigForge non-creating region load",
)
replace_once(
    forge_config,
    '                enableMc263StructureLocateCacheLocal.set(enableMc263StructureLocateCache.getValue());\n',
    '                enableMc263StructureLocateCacheLocal.set(enableMc263StructureLocateCache.getValue());\n'
    '                enableMc263NonCreatingRegionReadsLocal.set(enableMc263NonCreatingRegionReads.getValue());\n',
    "AsyncConfigForge non-creating region save",
)

mixin = root / "common/src/main/java/com/axalotl/async/common/mixin/world/RegionFileStorageMixin.java"
mixin.parent.mkdir(parents=True, exist_ok=True)
mixin.write_text('''package com.axalotl.async.common.mixin.world;\n\nimport com.axalotl.async.common.config.AsyncConfig;\nimport net.minecraft.nbt.CompoundTag;\nimport net.minecraft.nbt.StreamTagVisitor;\nimport net.minecraft.world.level.ChunkPos;\nimport net.minecraft.world.level.chunk.storage.RegionFileStorage;\nimport org.spongepowered.asm.mixin.Final;\nimport org.spongepowered.asm.mixin.Mixin;\nimport org.spongepowered.asm.mixin.Shadow;\nimport org.spongepowered.asm.mixin.Unique;\nimport org.spongepowered.asm.mixin.injection.At;\nimport org.spongepowered.asm.mixin.injection.Inject;\nimport org.spongepowered.asm.mixin.injection.callback.CallbackInfo;\nimport org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;\n\nimport java.nio.file.Files;\nimport java.nio.file.Path;\nimport java.util.Set;\nimport java.util.concurrent.ConcurrentHashMap;\n\n/**\n * Minecraft 1.20.1 RegionFileStorage#getRegionFile creates an .mca file even\n * for read/scan misses. Structure locating calls scanChunk across candidate\n * chunks, so a locate in ungenerated terrain can litter the region directory\n * with empty files. 26.3 switched missing-region reads to an existence fast\n * path and cached negative regions. This mixin backports that behavior without\n * changing the on-disk format or any successful read/write path.\n */\n@Mixin(RegionFileStorage.class)\npublic abstract class RegionFileStorageMixin {\n    @Unique\n    private static final int HARIMT$MC263_MAX_MISSING_REGIONS = 4_096;\n\n    @Shadow @Final\n    private Path folder;\n\n    @Unique\n    private final Set<Long> harimt$mc263MissingRegions = ConcurrentHashMap.newKeySet();\n\n    @Unique\n    private long harimt$mc263RegionKey(ChunkPos pos) {\n        return ChunkPos.asLong(pos.getRegionX(), pos.getRegionZ());\n    }\n\n    @Unique\n    private Path harimt$mc263RegionPath(ChunkPos pos) {\n        return this.folder.resolve("r." + pos.getRegionX() + "." + pos.getRegionZ() + ".mca");\n    }\n\n    @Unique\n    private boolean harimt$mc263RegionMissing(ChunkPos pos) {\n        long key = this.harimt$mc263RegionKey(pos);\n        if (this.harimt$mc263MissingRegions.contains(key)) {\n            return true;\n        }\n\n        // Files.notExists is conservative: false means either the file exists or\n        // existence could not be determined, in which case vanilla handles it.\n        if (!Files.notExists(this.harimt$mc263RegionPath(pos))) {\n            return false;\n        }\n\n        if (this.harimt$mc263MissingRegions.size() >= HARIMT$MC263_MAX_MISSING_REGIONS) {\n            this.harimt$mc263MissingRegions.clear();\n        }\n        this.harimt$mc263MissingRegions.add(key);\n        return true;\n    }\n\n    @Inject(method = "read", at = @At("HEAD"), cancellable = true)\n    private void harimt$mc263SkipMissingRegionRead(ChunkPos pos, CallbackInfoReturnable<CompoundTag> cir) {\n        if (AsyncConfig.enableMc263NonCreatingRegionReads.getValue() && this.harimt$mc263RegionMissing(pos)) {\n            cir.setReturnValue(null);\n        }\n    }\n\n    @Inject(method = "scanChunk", at = @At("HEAD"), cancellable = true)\n    private void harimt$mc263SkipMissingRegionScan(ChunkPos pos, StreamTagVisitor visitor, CallbackInfo ci) {\n        if (AsyncConfig.enableMc263NonCreatingRegionReads.getValue() && this.harimt$mc263RegionMissing(pos)) {\n            ci.cancel();\n        }\n    }\n\n    @Inject(method = "write", at = @At("HEAD"))\n    private void harimt$mc263InvalidateMissingRegionBeforeWrite(ChunkPos pos, CompoundTag tag, CallbackInfo ci) {\n        if (AsyncConfig.enableMc263NonCreatingRegionReads.getValue()) {\n            this.harimt$mc263MissingRegions.remove(this.harimt$mc263RegionKey(pos));\n        }\n    }\n}\n''', encoding="utf-8")

mixin_json = root / "common/src/main/resources/harimt.common.mixins.json"
data = json.loads(mixin_json.read_text(encoding="utf-8"))
entry = "world.RegionFileStorageMixin"
if entry not in data["mixins"]:
    anchor = "world.StructureCheckMixin"
    try:
        idx = data["mixins"].index(anchor) + 1
    except ValueError as exc:
        raise SystemExit(f"source drift: {anchor} missing; apply_mc263_backports.py must run first") from exc
    data["mixins"].insert(idx, entry)
mixin_json.write_text(json.dumps(data, indent=2) + "\n", encoding="utf-8")

required = {
    common_config: ["enableMc263NonCreatingRegionReads"],
    forge_config: ["enableMc263NonCreatingRegionReadsLocal", "Missing .mca regions"],
    mixin: ["Files.notExists", "harimt$mc263MissingRegions", "harimt$mc263InvalidateMissingRegionBeforeWrite"],
    mixin_json: ["world.RegionFileStorageMixin"],
}
for path, tokens in required.items():
    text = path.read_text(encoding="utf-8")
    missing = [token for token in tokens if token not in text]
    if missing:
        raise SystemExit(f"26.3 non-creating region-read invariant failed in {path}: missing {missing}")

print("HariMultiThread Minecraft 26.3 non-creating region-read backport applied successfully")
