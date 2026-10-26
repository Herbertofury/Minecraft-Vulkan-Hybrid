#!/usr/bin/env python3
from pathlib import Path
import json
import sys

if len(sys.argv) != 2:
    raise SystemExit("usage: apply_mc263_backports.py <merged-upstream-root>")

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


# ---------------------------------------------------------------------------
# Common config: independent, compatibility-friendly 26.3 backport switches.
# ---------------------------------------------------------------------------
common_config = root / "common/src/main/java/com/axalotl/async/common/config/AsyncConfig.java"
config_marker = '    public static Map.Entry<String, Boolean> enableGpuCollision = new AbstractMap.SimpleEntry<String, Boolean>("enableGpuCollision", true);\n'
config_insert = config_marker + (
    '    public static Map.Entry<String, Boolean> enableMc263PersistentMobIdle = new AbstractMap.SimpleEntry<String, Boolean>("enableMc263PersistentMobIdle", true);\n'
    '    public static Map.Entry<String, Boolean> enableMc263StructureLocateCache = new AbstractMap.SimpleEntry<String, Boolean>("enableMc263StructureLocateCache", true);\n'
)
replace_once(common_config, config_marker, config_insert, "AsyncConfig 26.3 toggles")

# ---------------------------------------------------------------------------
# Forge config bridge.
# ---------------------------------------------------------------------------
forge_config = root / "forge/src/main/java/com/axalotl/async/forge/config/AsyncConfigForge.java"
replace_once(
    forge_config,
    '        private static final ForgeConfigSpec.ConfigValue<Boolean> enableGpuCollisionLocal;\n',
    '        private static final ForgeConfigSpec.ConfigValue<Boolean> enableGpuCollisionLocal;\n'
    '        private static final ForgeConfigSpec.ConfigValue<Boolean> enableMc263PersistentMobIdleLocal;\n'
    '        private static final ForgeConfigSpec.ConfigValue<Boolean> enableMc263StructureLocateCacheLocal;\n',
    "AsyncConfigForge declarations",
)

forge_gpu_definition = '''                enableGpuCollisionLocal = BUILDER.comment(\n                                "Enable Vulkan broad-phase acceleration for deferred entity push queries. " +\n                                "When disabled or unavailable, vanilla collision lookup remains authoritative.")\n                                .define("enableGpuCollision", enableGpuCollision.getValue());\n'''
forge_263_definitions = forge_gpu_definition + '''\n                enableMc263PersistentMobIdleLocal = BUILDER.comment(\n                                "Backport Minecraft 26.3 persistent-mob idle behavior. Persistent mobs that are " +\n                                "outside their vanilla no-despawn player radius are allowed to accumulate noActionTime, " +\n                                "so existing random stroll/swim goals naturally deactivate while no player is nearby. " +\n                                "Does not change despawn eligibility or replace mob goals.")\n                                .define("enableMc263PersistentMobIdle", enableMc263PersistentMobIdle.getValue());\n\n                enableMc263StructureLocateCacheLocal = BUILDER.comment(\n                                "Backport the safe portion of Minecraft 26.3 structure-locate caching. Remembers chunks " +\n                                "whose structure metadata scan returned no stored structure data, avoiding repeated NBT/region " +\n                                "reads while preserving vanilla generation checks. Cache entries are invalidated when structure " +\n                                "data is loaded or references change.")\n                                .define("enableMc263StructureLocateCache", enableMc263StructureLocateCache.getValue());\n'''
replace_once(forge_config, forge_gpu_definition, forge_263_definitions, "AsyncConfigForge definitions")
replace_once(
    forge_config,
    '                enableGpuCollision.setValue(enableGpuCollisionLocal.get());\n',
    '                enableGpuCollision.setValue(enableGpuCollisionLocal.get());\n'
    '                enableMc263PersistentMobIdle.setValue(enableMc263PersistentMobIdleLocal.get());\n'
    '                enableMc263StructureLocateCache.setValue(enableMc263StructureLocateCacheLocal.get());\n',
    "AsyncConfigForge load bridge",
)
replace_once(
    forge_config,
    '                enableGpuCollisionLocal.set(enableGpuCollision.getValue());\n',
    '                enableGpuCollisionLocal.set(enableGpuCollision.getValue());\n'
    '                enableMc263PersistentMobIdleLocal.set(enableMc263PersistentMobIdle.getValue());\n'
    '                enableMc263StructureLocateCacheLocal.set(enableMc263StructureLocateCache.getValue());\n',
    "AsyncConfigForge save bridge",
)

# ---------------------------------------------------------------------------
# 26.3 persistent-mob idling. This restores only the noActionTime value that
# 1.20.1 checkDespawn() zeroes for persistent mobs; all other vanilla logic stays.
# ---------------------------------------------------------------------------
mob = root / "common/src/main/java/com/axalotl/async/common/mixin/entity/MobMixin.java"
replace_once(
    mob,
    'import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;\n',
    'import com.axalotl.async.common.config.AsyncConfig;\n'
    'import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;\n',
    "MobMixin config import",
)
replace_once(
    mob,
    'import net.minecraft.world.entity.EntityType;\n',
    'import net.minecraft.world.entity.Entity;\n'
    'import net.minecraft.world.entity.EntityType;\n',
    "MobMixin entity import",
)
replace_once(
    mob,
    'import org.spongepowered.asm.mixin.Mixin;\n',
    'import org.spongepowered.asm.mixin.Mixin;\n'
    'import org.spongepowered.asm.mixin.injection.At;\n'
    'import org.spongepowered.asm.mixin.injection.Inject;\n'
    'import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;\n',
    "MobMixin injection imports",
)

mob_lock = '''    @Unique\n    private static final Object async$lock = new Object();\n'''
mob_263 = mob_lock + '''\n    /**\n     * Minecraft 26.3 stopped resetting persistent mobs' noActionTime solely because they are persistent.\n     * Capture the value before vanilla 1.20.1 checkDespawn() so the tail hook can undo only that legacy\n     * reset when no player is inside the category's vanilla no-despawn radius. This deliberately leaves\n     * vanilla goal logic, despawn rules, targeting, navigation, and near-player reset behavior untouched.\n     */\n    @Unique\n    private int harimt$mc263NoActionTimeBeforeDespawn;\n\n    @Inject(method = "checkDespawn", at = @At("HEAD"))\n    private void harimt$captureMc263PersistentIdleState(CallbackInfo ci) {\n        if (AsyncConfig.enableMc263PersistentMobIdle.getValue()) {\n            this.harimt$mc263NoActionTimeBeforeDespawn = ((Mob)(Object)this).getNoActionTime();\n        }\n    }\n\n    @Inject(method = "checkDespawn", at = @At("RETURN"))\n    private void harimt$restoreMc263PersistentIdleState(CallbackInfo ci) {\n        if (!AsyncConfig.enableMc263PersistentMobIdle.getValue()) {\n            return;\n        }\n\n        Mob mob = (Mob)(Object)this;\n        if (!(mob.isPersistenceRequired() || mob.requiresCustomPersistence())) {\n            return;\n        }\n\n        Entity nearestPlayer = mob.level().getNearestPlayer(mob, -1.0D);\n        int noDespawnDistance = mob.getType().getCategory().getNoDespawnDistance();\n        double noDespawnDistanceSqr = (double)noDespawnDistance * (double)noDespawnDistance;\n        if (nearestPlayer == null || nearestPlayer.distanceToSqr(mob) >= noDespawnDistanceSqr) {\n            mob.setNoActionTime(Math.max(mob.getNoActionTime(), this.harimt$mc263NoActionTimeBeforeDespawn));\n        }\n    }\n'''
replace_once(mob, mob_lock, mob_263, "MobMixin 26.3 idle behavior")

# ---------------------------------------------------------------------------
# 26.3-inspired structure locate storage-miss cache. Caches only the null return
# from 1.20.1 tryLoadFromStorage, which means checkStart still executes vanilla's
# canCreateStructure path. It cannot turn a valid locate result into a negative.
# ---------------------------------------------------------------------------
structure_mixin = root / "common/src/main/java/com/axalotl/async/common/mixin/world/StructureCheckMixin.java"
structure_mixin.parent.mkdir(parents=True, exist_ok=True)
structure_mixin.write_text('''package com.axalotl.async.common.mixin.world;\n\nimport com.axalotl.async.common.config.AsyncConfig;\nimport it.unimi.dsi.fastutil.objects.Object2IntMap;\nimport net.minecraft.world.level.ChunkPos;\nimport net.minecraft.world.level.levelgen.structure.Structure;\nimport net.minecraft.world.level.levelgen.structure.StructureCheck;\nimport net.minecraft.world.level.levelgen.structure.StructureCheckResult;\nimport org.spongepowered.asm.mixin.Mixin;\nimport org.spongepowered.asm.mixin.Unique;\nimport org.spongepowered.asm.mixin.injection.At;\nimport org.spongepowered.asm.mixin.injection.Inject;\nimport org.spongepowered.asm.mixin.injection.callback.CallbackInfo;\nimport org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;\n\nimport java.util.Set;\nimport java.util.concurrent.ConcurrentHashMap;\n\n/**\n * Backports the repeated-query portion of Minecraft 26.3's structure-locate\n * improvements without changing structure placement or generation semantics.\n */\n@Mixin(StructureCheck.class)\npublic abstract class StructureCheckMixin {\n    @Unique\n    private static final int HARIMT$MC263_MAX_STORAGE_MISSES = 65_536;\n\n    @Unique\n    private final Set<Long> harimt$mc263StorageMisses = ConcurrentHashMap.newKeySet();\n\n    @Inject(method = "tryLoadFromStorage", at = @At("HEAD"), cancellable = true)\n    private void harimt$mc263SkipRepeatedStorageMiss(ChunkPos chunkPos, Structure structure, boolean skipKnownStructures,\n                                                     long chunkKey, CallbackInfoReturnable<StructureCheckResult> cir) {\n        if (AsyncConfig.enableMc263StructureLocateCache.getValue()\n                && this.harimt$mc263StorageMisses.contains(chunkKey)) {\n            // null is vanilla's signal to continue into the cached canCreateStructure path.\n            cir.setReturnValue(null);\n        }\n    }\n\n    @Inject(method = "tryLoadFromStorage", at = @At("RETURN"))\n    private void harimt$mc263RememberStorageMiss(ChunkPos chunkPos, Structure structure, boolean skipKnownStructures,\n                                                 long chunkKey, CallbackInfoReturnable<StructureCheckResult> cir) {\n        if (!AsyncConfig.enableMc263StructureLocateCache.getValue() || cir.getReturnValue() != null) {\n            return;\n        }\n        if (this.harimt$mc263StorageMisses.size() >= HARIMT$MC263_MAX_STORAGE_MISSES) {\n            // Hard memory bound. Clearing only loses cache warmth; it cannot change results.\n            this.harimt$mc263StorageMisses.clear();\n        }\n        this.harimt$mc263StorageMisses.add(chunkKey);\n    }\n\n    @Inject(method = "storeFullResults", at = @At("HEAD"))\n    private void harimt$mc263InvalidateStorageMiss(long chunkKey, Object2IntMap<Structure> structures, CallbackInfo ci) {\n        this.harimt$mc263StorageMisses.remove(chunkKey);\n    }\n\n    @Inject(method = "incrementReference", at = @At("HEAD"))\n    private void harimt$mc263InvalidateStorageMissOnReference(ChunkPos chunkPos, Structure structure, CallbackInfo ci) {\n        this.harimt$mc263StorageMisses.remove(chunkPos.toLong());\n    }\n}\n''', encoding="utf-8")

mixin_json = root / "common/src/main/resources/harimt.common.mixins.json"
data = json.loads(mixin_json.read_text(encoding="utf-8"))
entry = "world.StructureCheckMixin"
if entry not in data["mixins"]:
    try:
        idx = data["mixins"].index("world.ServerLevelMixin") + 1
    except ValueError as exc:
        raise SystemExit("source drift: world.ServerLevelMixin missing from common mixin config") from exc
    data["mixins"].insert(idx, entry)
mixin_json.write_text(json.dumps(data, indent=2) + "\n", encoding="utf-8")

# Fail closed if any half-applied state escaped the deterministic patch.
required = {
    common_config: ["enableMc263PersistentMobIdle", "enableMc263StructureLocateCache"],
    forge_config: ["enableMc263PersistentMobIdleLocal", "enableMc263StructureLocateCacheLocal"],
    mob: ["harimt$mc263NoActionTimeBeforeDespawn", "enableMc263PersistentMobIdle"],
    structure_mixin: ["harimt$mc263StorageMisses", "enableMc263StructureLocateCache"],
    mixin_json: ["world.StructureCheckMixin"],
}
for path, tokens in required.items():
    text = path.read_text(encoding="utf-8")
    missing = [token for token in tokens if token not in text]
    if missing:
        raise SystemExit(f"26.3 backport invariant failed in {path}: missing {missing}")

print("HariMultiThread Minecraft 26.3 safe performance backports applied successfully")
