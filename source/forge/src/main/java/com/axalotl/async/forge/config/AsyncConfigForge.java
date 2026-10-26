package com.axalotl.async.forge.config;

import net.minecraftforge.common.ForgeConfigSpec;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.axalotl.async.common.config.AsyncConfig.*;

public class AsyncConfigForge {
        public static final ForgeConfigSpec SPEC;
        private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

        private static final ForgeConfigSpec.ConfigValue<Boolean> disabledLocal;
        private static final ForgeConfigSpec.ConfigValue<Integer> maxThreadsLocal;
        private static final ForgeConfigSpec.ConfigValue<List<? extends String>> synchronizedEntitiesLocal;
        private static final ForgeConfigSpec.ConfigValue<Boolean> enableAsyncSpawnLocal;
        private static final ForgeConfigSpec.ConfigValue<Boolean> enableAsyncRandomTicksLocal;
        private static final ForgeConfigSpec.ConfigValue<Boolean> enableAffinityRoutingLocal;
        private static final ForgeConfigSpec.ConfigValue<Boolean> enableCircuitBreakerLocal;
        private static final ForgeConfigSpec.ConfigValue<Boolean> enableGpuCollisionLocal;
        private static final ForgeConfigSpec.ConfigValue<Boolean> enableMc263PersistentMobIdleLocal;
        private static final ForgeConfigSpec.ConfigValue<Boolean> enableMc263StructureLocateCacheLocal;
        private static final ForgeConfigSpec.ConfigValue<Boolean> enableMc263NonCreatingRegionReadsLocal;
        private static final ForgeConfigSpec.ConfigValue<Boolean> enableMc263DensityCacheFillLocal;
        private static final ForgeConfigSpec.ConfigValue<Boolean> enableGpuDrivenTerrainLocal;
        private static final ForgeConfigSpec.ConfigValue<Boolean> enableGpuTerrainRegionCacheLocal;
        private static final ForgeConfigSpec.ConfigValue<Boolean> enableGpuTerrainPersistentSceneLocal;
        private static final ForgeConfigSpec.ConfigValue<Integer> gpuTerrainCpuThresholdLocal;
        private static final ForgeConfigSpec.ConfigValue<Integer> entitiesPerWorkerLocal;
        private static final ForgeConfigSpec.ConfigValue<Integer> staleTaskTimeoutMsLocal;

        static {
                BUILDER.push("Async Config");

                disabledLocal = BUILDER.comment("Disables parallel processing of entities.")
                                .define("disabled", disabled.getValue());

                maxThreadsLocal = BUILDER.comment("Maximum worker threads. -1 = auto.")
                                .defineInRange("paraMax", maxThreads.getValue(), -1, Integer.MAX_VALUE);

                synchronizedEntitiesLocal = BUILDER.comment("""
                                List of entity IDs or namespaces (*):
                                  - 'minecraft:zombie' = specific entity
                                  - 'minecraft:*'      = all entities in namespace""")
                                .defineListAllowEmpty(
                                                "synchronizedEntities",
                                                () -> new ArrayList<>(synchronizedEntities.getValue()),
                                                obj -> obj instanceof String);

                enableAsyncSpawnLocal = BUILDER.comment(
                                "Enables async entity spawning. WARNING: incompatible with Carpet's lagFreeSpawning.")
                                .define("enableAsyncSpawn", enableAsyncSpawn.getValue());

                enableAsyncRandomTicksLocal = BUILDER.comment("Experimental! Enables async random ticks.")
                                .define("enableAsyncRandomTicks", enableAsyncRandomTicks.getValue());

                enableAffinityRoutingLocal = BUILDER.comment("""
                                Enable affinity-based entity routing.
                                Routes entities in the same chunk to the same worker thread for better CPU cache locality.
                                Workers steal from other lanes when idle. Recommended: true.""")
                                .define("enableAffinityRouting", enableAffinityRouting.getValue());

                enableCircuitBreakerLocal = BUILDER.comment("""
                                Enable circuit breaker for entity tick crash isolation.
                                When an entity type crashes repeatedly during async tick, it is automatically
                                moved to synchronous ticking until it stabilizes. Prevents cascade failures.""")
                                .define("enableCircuitBreaker", enableCircuitBreaker.getValue());

                enableGpuCollisionLocal = BUILDER.comment(
                                "Enable Vulkan broad-phase acceleration for deferred entity push queries. " +
                                "When disabled or unavailable, vanilla collision lookup remains authoritative.")
                                .define("enableGpuCollision", enableGpuCollision.getValue());

                enableMc263PersistentMobIdleLocal = BUILDER.comment(
                                "Backport Minecraft 26.3 persistent-mob idle behavior. Persistent mobs that are " +
                                "outside their vanilla no-despawn player radius are allowed to accumulate noActionTime, " +
                                "so existing random stroll/swim goals naturally deactivate while no player is nearby. " +
                                "Does not change despawn eligibility or replace mob goals.")
                                .define("enableMc263PersistentMobIdle", enableMc263PersistentMobIdle.getValue());

                enableMc263StructureLocateCacheLocal = BUILDER.comment(
                                "Backport the safe portion of Minecraft 26.3 structure-locate caching. Remembers chunks " +
                                "whose structure metadata scan returned no stored structure data, avoiding repeated NBT/region " +
                                "reads while preserving vanilla generation checks. Cache entries are invalidated when structure " +
                                "data is loaded or references change.")
                                .define("enableMc263StructureLocateCache", enableMc263StructureLocateCache.getValue());

                enableMc263NonCreatingRegionReadsLocal = BUILDER.comment(
                                "Backport Minecraft 26.3 non-creating region reads used by structure locating. Missing .mca regions " +
                                "are remembered in a bounded cache and read/scan calls return empty without creating files. " +
                                "The cache is invalidated before any chunk write, preserving normal world generation and saves.")
                                .define("enableMc263NonCreatingRegionReads", enableMc263NonCreatingRegionReads.getValue());

                enableMc263DensityCacheFillLocal = BUILDER.comment(
                                "EXPERIMENTAL: use a 26.3-style cache-aware bulk-fill path for Minecraft 1.20.1 Cache2D density functions. " +
                                "The 2.2.0 real-Forge ABBA lab did not meet the default-on performance threshold, so this remains OFF by default. " +
                                "It keeps double-precision density math and automatically yields to HariChunk/C2ME.")
                                .define("enableMc263DensityCacheFill", enableMc263DensityCacheFill.getValue());

                enableGpuDrivenTerrainLocal = BUILDER.comment(
                                "Enable Hari's Minecraft-26.3-style GPU-driven terrain lane when Embeddium is present. " +
                                "Hari keeps Embeddium's exact static terrain meshes and only replaces CPU draw-command submission. " +
                                "Automatically yields to Nvidium, Alloyium, Oculus/Iris, unsupported GL hardware, or a runtime failure.")
                                .define("enableGpuDrivenTerrain", enableGpuDrivenTerrain.getValue());

                enableGpuTerrainRegionCacheLocal = BUILDER.comment(
                                "Cache unchanged per-region indirect draw commands using pass-local Embeddium storage generations and exact visible-face signatures.")
                                .define("enableGpuTerrainRegionCache", enableGpuTerrainRegionCache.getValue());

                enableGpuTerrainPersistentSceneLocal = BUILDER.comment(
                                "Keep immutable terrain draw-boundary metadata resident on the GPU and refresh it only when Embeddium changes that region/pass storage. " +
                                "Visible-section and block-face masks remain sourced from Embeddium every frame, preserving visual behavior while removing per-frame mesh-metadata rebuilds.")
                                .define("enableGpuTerrainPersistentScene", enableGpuTerrainPersistentScene.getValue());

                gpuTerrainCpuThresholdLocal = BUILDER.comment(
                                "Per-region meshlet count at or below which Hari builds indirect commands on the CPU instead of dispatching a compute shader. " +
                                "Small batches avoid GPU dispatch overhead; larger batches move visibility/command generation to the GPU.")
                                .defineInRange("gpuTerrainCpuThreshold", gpuTerrainCpuThreshold.getValue(), 1, 65536);

                entitiesPerWorkerLocal = BUILDER.comment("""
                                Target number of entities per worker thread. Lower values = more parallelism.
                                The system dynamically scales workers based on entity count.
                                Recommended: 15-40. Default: 25.""")
                                .defineInRange("entitiesPerWorker", entitiesPerWorker.getValue(), 5, 200);

                staleTaskTimeoutMsLocal = BUILDER.comment("""
                                Timeout in milliseconds before warning about slow entity tick batches.
                                Does NOT cancel ticks (unsafe) - only logs warnings for diagnostics.
                                Default: 200ms.""")
                                .defineInRange("staleTaskTimeoutMs", staleTaskTimeoutMs.getValue(), 50, 5000);

                BUILDER.pop();
                SPEC = BUILDER.build();
                LOGGER.info("Configuration initialized.");
        }

        public static void loadConfig() {
                disabled.setValue(disabledLocal.get());
                maxThreads.setValue(maxThreadsLocal.get());
                enableAsyncSpawn.setValue(enableAsyncSpawnLocal.get());
                enableAsyncRandomTicks.setValue(enableAsyncRandomTicksLocal.get());
                enableAffinityRouting.setValue(enableAffinityRoutingLocal.get());
                enableCircuitBreaker.setValue(enableCircuitBreakerLocal.get());
                enableGpuCollision.setValue(enableGpuCollisionLocal.get());
                enableMc263PersistentMobIdle.setValue(enableMc263PersistentMobIdleLocal.get());
                enableMc263StructureLocateCache.setValue(enableMc263StructureLocateCacheLocal.get());
                enableMc263NonCreatingRegionReads.setValue(enableMc263NonCreatingRegionReadsLocal.get());
                enableMc263DensityCacheFill.setValue(enableMc263DensityCacheFillLocal.get());
                enableGpuDrivenTerrain.setValue(enableGpuDrivenTerrainLocal.get());
                enableGpuTerrainRegionCache.setValue(enableGpuTerrainRegionCacheLocal.get());
                enableGpuTerrainPersistentScene.setValue(enableGpuTerrainPersistentSceneLocal.get());
                gpuTerrainCpuThreshold.setValue(gpuTerrainCpuThresholdLocal.get());
                entitiesPerWorker.setValue(entitiesPerWorkerLocal.get());
                staleTaskTimeoutMs.setValue(staleTaskTimeoutMsLocal.get());

                List<? extends String> entries = synchronizedEntitiesLocal.get();
                Set<String> entities = new HashSet<>();
                if (!entries.isEmpty()) {
                        entities.addAll(entries);
                }

                synchronizedEntities.setValue(entities.isEmpty()
                                ? getDefaultSynchronizedEntities()
                                : entities);
        }

        public static void saveConfig() {
                java.util.Map<String, Object> snapshot = new java.util.LinkedHashMap<>();
                snapshot.put(disabled.getKey(), disabled.getValue());
                snapshot.put(maxThreads.getKey(), maxThreads.getValue());
                snapshot.put(enableAsyncSpawn.getKey(), enableAsyncSpawn.getValue());
                snapshot.put(enableAsyncRandomTicks.getKey(), enableAsyncRandomTicks.getValue());
                snapshot.put(enableAffinityRouting.getKey(), enableAffinityRouting.getValue());
                snapshot.put(enableCircuitBreaker.getKey(), enableCircuitBreaker.getValue());
                snapshot.put(enableGpuCollision.getKey(), enableGpuCollision.getValue());
                snapshot.put(enableMc263PersistentMobIdle.getKey(), enableMc263PersistentMobIdle.getValue());
                snapshot.put(enableMc263StructureLocateCache.getKey(), enableMc263StructureLocateCache.getValue());
                snapshot.put(enableMc263NonCreatingRegionReads.getKey(), enableMc263NonCreatingRegionReads.getValue());
                snapshot.put(enableMc263DensityCacheFill.getKey(), enableMc263DensityCacheFill.getValue());
                snapshot.put(enableGpuDrivenTerrain.getKey(), enableGpuDrivenTerrain.getValue());
                snapshot.put(enableGpuTerrainRegionCache.getKey(), enableGpuTerrainRegionCache.getValue());
                snapshot.put(enableGpuTerrainPersistentScene.getKey(), enableGpuTerrainPersistentScene.getValue());
                snapshot.put(gpuTerrainCpuThreshold.getKey(), gpuTerrainCpuThreshold.getValue());
                snapshot.put(entitiesPerWorker.getKey(), entitiesPerWorker.getValue());
                snapshot.put(staleTaskTimeoutMs.getKey(), staleTaskTimeoutMs.getValue());
                snapshot.put(synchronizedEntities.getKey(), new ArrayList<>(synchronizedEntities.getValue()));
                HariConfigPersistence.save(snapshot);
                onConfigLoaded();
        }
}
