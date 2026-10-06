package dev.engine_room.flywheel.backend;
import dev.engine_room.flywheel.api.backend.Backend;
import dev.engine_room.flywheel.lib.backend.SimpleBackend;
import mvhflywheelbackend.NativeEngine;
import net.minecraft.resources.ResourceLocation;
import net.vulkanmod.compat.UniversalRendererGate;
import net.vulkanmod.vulkan.Vulkan;
public final class Backends {
 public static final Backend NATIVE=SimpleBackend.builder().engineFactory(NativeEngine::new).priority(2000)
  .supported(()->UniversalRendererGate.vulkanRendererEnabled()&&Vulkan.getVkDevice()!=null)
  .register(new ResourceLocation("mvh","native_vulkan"));
 public static void init(){}
}
