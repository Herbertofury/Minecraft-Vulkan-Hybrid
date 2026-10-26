package net.vulkanmod.gl;
import com.mojang.blaze3d.systems.RenderSystem;
import net.vulkanmod.compat.UniversalRendererGate;
import net.vulkanmod.vulkan.device.DeviceManager;
/** Explicit implemented storage/mapping routes; never a fabricated GL context/version. */
public final class NativeEssentialBufferRuntime {
 private NativeEssentialBufferRuntime(){}
 private static boolean ready(){RenderSystem.assertOnRenderThread();if(!UniversalRendererGate.vulkanRendererEnabled()||DeviceManager.vkDevice==null)throw new IllegalStateException("Native Essential buffer device unavailable");return true;}
 public static boolean supportsVertexArrayRouting(){return ready();}
 public static boolean supportsUniformAndCopyStorage(){return ready();}
 public static boolean supportsRangeMapping(){return ready();}
}
