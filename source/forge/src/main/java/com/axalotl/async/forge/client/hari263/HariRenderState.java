package com.axalotl.async.forge.client.hari263;

import com.axalotl.async.common.config.AsyncConfig;
import com.mojang.logging.LogUtils;
import net.minecraftforge.fml.ModList;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11C;
import org.lwjgl.opengl.GLCapabilities;
import org.slf4j.Logger;

/**
 * Client-render provider arbitration for the 1.20.1 -> 26.3 performance bridge.
 *
 * <p>The invariant is deliberately conservative: Hari never double-hooks a renderer that can
 * already own the terrain pipeline. NVIDIA/Nvidium and external Alloyium win; otherwise Hari can
 * accelerate Embeddium using its existing static terrain buffers. Shader-provider presence falls
 * back to the provider until the compatible bridge is proven in native QA.</p>
 */
public final class HariRenderState {
    public enum Provider {
        NVIDIA_NVIDIUM,
        EXTERNAL_ALLOYIUM,
        HARI_GPU_INDIRECT,
        EMBEDDIUM,
        VANILLA
    }

    private static final Logger LOGGER = LogUtils.getLogger();

    private static volatile boolean glCompatible;
    private static volatile boolean indirectCount;
    private static volatile boolean nvidiaMeshShaderCapable;
    private static volatile String glVendor = "unknown";
    private static volatile String glRenderer = "unknown";
    private static volatile Provider provider = Provider.VANILLA;
    private static volatile Provider lastLoggedProvider;

    private HariRenderState() {}

    public static void detectCapabilities() {
        GLCapabilities caps = GL.getCapabilities();
        glCompatible = caps.OpenGL43 || (caps.GL_ARB_compute_shader
                && caps.GL_ARB_shader_storage_buffer_object
                && caps.GL_ARB_multi_draw_indirect
                && caps.GL_ARB_draw_indirect
                && caps.GL_ARB_explicit_attrib_location);
        indirectCount = caps.OpenGL46 || caps.GL_ARB_indirect_parameters;
        // LWJGL exposes NV_mesh_shader on supported drivers. Keep this diagnostic even though
        // the internal fallback intentionally remains portable; Nvidium gets ownership when loaded.
        nvidiaMeshShaderCapable = caps.GL_NV_mesh_shader;
        String vendor = GL11C.glGetString(GL11C.GL_VENDOR);
        String renderer = GL11C.glGetString(GL11C.GL_RENDERER);
        glVendor = vendor == null ? "unknown" : vendor;
        glRenderer = renderer == null ? "unknown" : renderer;
        refreshProvider();
    }

    public static void refreshProvider() {
        boolean nvidium = loaded("nvidium");
        boolean alloyium = loaded("alloyium");
        boolean embeddium = loaded("embeddium");
        boolean shaderProvider = loaded("oculus") || loaded("iris");

        Provider next;
        if (nvidium) {
            next = Provider.NVIDIA_NVIDIUM;
        } else if (alloyium) {
            next = Provider.EXTERNAL_ALLOYIUM;
        } else if (embeddium && !shaderProvider && glCompatible
                && AsyncConfig.enableGpuDrivenTerrain.getValue()) {
            next = Provider.HARI_GPU_INDIRECT;
        } else if (embeddium) {
            next = Provider.EMBEDDIUM;
        } else {
            next = Provider.VANILLA;
        }
        provider = next;

        if (next != lastLoggedProvider) {
            lastLoggedProvider = next;
            LOGGER.info("Hari 26.3 render bridge: provider={} GL43={} indirectCount={} NV_mesh_shader={} vendor='{}' renderer='{}'",
                    provider, glCompatible, indirectCount, nvidiaMeshShaderCapable, glVendor, glRenderer);
            if (shaderProvider && next == Provider.EMBEDDIUM) {
                LOGGER.info("Hari GPU terrain is yielding to Oculus/Iris for visual correctness; server/world 26.3 backports remain active");
            }
            if (nvidium) {
                LOGGER.info("Nvidium detected: Hari yields terrain ownership to the NVIDIA mesh-shader backend and keeps non-render optimizations active");
            } else if (alloyium) {
                LOGGER.info("Alloyium detected: Hari yields terrain ownership to the external GPU-indirect backend and keeps non-render optimizations active");
            }
        }
    }

    private static boolean loaded(String id) {
        try {
            return ModList.get().isLoaded(id);
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static boolean shouldUseInternalRenderer() {
        return provider == Provider.HARI_GPU_INDIRECT && glCompatible
                && AsyncConfig.enableGpuDrivenTerrain.getValue();
    }

    public static boolean hasIndirectCount() {
        return indirectCount;
    }

    public static boolean isGlCompatible() {
        return glCompatible;
    }

    public static boolean isNvidiaMeshShaderCapable() {
        return nvidiaMeshShaderCapable;
    }

    public static Provider provider() {
        return provider;
    }

    public static String glVendor() {
        return glVendor;
    }

    public static String glRenderer() {
        return glRenderer;
    }
}
