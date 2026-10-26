package net.vulkanmod.gl;
import com.mojang.blaze3d.systems.RenderSystem;
import net.vulkanmod.vulkan.device.DeviceManager;
import org.apache.logging.log4j.*;
import org.lwjgl.system.MemoryStack;

/** Explicit native routing, never a fabricated GLCapabilities/context object. */
public final class NativePhysicsVaoRuntime {
    public static final Logger logger=LogManager.getLogger("Physics/native-VAO");
    private NativePhysicsVaoRuntime(){}
    public static boolean separateBindings(){RenderSystem.assertOnRenderThread();if(DeviceManager.vkDevice==null)throw new IllegalStateException("Native VAO device unavailable");return true;}
    public static boolean multiBindings(){return separateBindings();}
    public static boolean instanceInputs(){return separateBindings();}
    public static boolean hasCoreMultiBinding(){return multiBindings();}
    public static boolean hasArbMultiBinding(){return multiBindings();}
    public static boolean hasCoreAttributeBinding(){return separateBindings();}
    public static boolean hasArbAttributeBinding(){return separateBindings();}
    public static boolean hasCoreInstances(){return instanceInputs();}
    public static boolean hasArbInstances(){return instanceInputs();}
    /** Original array upload helpers use the current render thread's bounded native stack. */
    public static MemoryStack memoryStack(){RenderSystem.assertOnRenderThread();return MemoryStack.stackGet();}
}
