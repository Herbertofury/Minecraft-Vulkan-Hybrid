/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.apache.logging.log4j.LogManager
 *  org.apache.logging.log4j.Logger
 */
package com.axalotl.async.common;

import com.axalotl.async.common.platform.PlatformUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public abstract class AsyncCommon {
    public static final String MODID = "harimt";
    private static final Logger LOGGER = LogManager.getLogger((String)"HariMultiThread");
    public static boolean LITHIUM = PlatformUtils.isModLoaded("lithium") || PlatformUtils.isModLoaded("harium");
    public static boolean HARIPLAYER = PlatformUtils.isModLoaded("hariplayer") || PlatformUtils.isModLoaded("vmp");
    public static boolean HARICHUNK = PlatformUtils.isModLoaded("harichunk") || PlatformUtils.isModLoaded("c2me") || PlatformUtils.isModLoaded("c2meforge") || PlatformUtils.isModLoaded("c2mef") || PlatformUtils.isModLoaded("c2me_base");
    // Minecraft 26.3 backport provider ownership. These flags do not make the
    // external mods dependencies; they only prevent Hari from competing for an
    // invasive subsystem another established performance provider already owns.
    public static boolean NOISIUM = PlatformUtils.isModLoaded("noisium");
    public static boolean EXTERNAL_TERRAIN_RENDERER = PlatformUtils.isModLoaded("embeddium")
            || PlatformUtils.isModLoaded("rubidium") || PlatformUtils.isModLoaded("sodium");
    public static boolean EXTERNAL_SHADER_PIPELINE = PlatformUtils.isModLoaded("oculus")
            || PlatformUtils.isModLoaded("iris");

    public final void initialize() {
        PlatformUtils.initialize();
        AsyncCommon.logCompatibilityStatus();
    }

    private static void logCompatibilityStatus() {
        LOGGER.info("=== HariMultiThread Mod Compatibility ===");
        if (LITHIUM) {
            LOGGER.info("Detected: Harium/Lithium - Adjusted entity AI optimizations");
            LOGGER.info("  -> Enabled RadiumServerLevel compat mixin");
            LOGGER.info("  -> SyncAllMixin will provide thread safety for optimized collections");
        }
        if (HARIPLAYER) {
            LOGGER.info("Detected: HariPlayer/VMP - Async chunk operations coordinated");
            LOGGER.info("  -> Enabled VMPChunkMapMixin compat mixin");
            LOGGER.info("  -> Entity tracking synchronized with VMP optimizations");
        }
        if (HARICHUNK) {
            LOGGER.info("Detected: HariChunk/C2ME - Threading synchronized");
            LOGGER.info("  -> Chunk operations deferred to C2ME async system");
            LOGGER.info("  -> DynamicGraphMinFixedPoint excluded from SyncAll (C2ME manages lighting threads)");
        }
        if (NOISIUM) {
            LOGGER.info("Detected: Noisium - additive world-generation provider");
            LOGGER.info("  -> Hari keeps its result-preserving 26.3 Cache2D fill lane; Noisium owns its generator/section fast paths");
        }
        if (EXTERNAL_TERRAIN_RENDERER) {
            LOGGER.info("Detected: Embeddium/Rubidium/Sodium terrain renderer");
            LOGGER.info("  -> Hari 2.3 can cooperate with Embeddium as the authoritative chunk-mesh owner");
            LOGGER.info("  -> Client provider arbitration prevents double-hooking Nvidium/Alloyium/shader pipelines");
        } else {
            LOGGER.info("Terrain renderer: vanilla 1.20.1 owner (Hari GPU terrain lane remains inactive)");
        }
        if (EXTERNAL_SHADER_PIPELINE) {
            LOGGER.info("Detected: Oculus/Iris shader pipeline");
            LOGGER.info("  -> Minecraft 26.3 OIT/ShaderC client lanes delegated to the shader provider");
            LOGGER.info("  -> Hari will not replace the active shader compiler/transparency pipeline");
        } else {
            LOGGER.info("Shader pipeline: vanilla 1.20.1 owner (Hari core does not force OIT/ShaderC)");
        }
        if (LITHIUM && HARIPLAYER) {
            LOGGER.info("Detected: Harium + HariPlayer together - PalettedContainer lock removal handled by Harium");
        }
        if (!(LITHIUM || HARIPLAYER || HARICHUNK)) {
            LOGGER.info("No conflicting server-threading optimization mods detected - Full async mode enabled");
        }
        LOGGER.info("=========================================");
    }
}

