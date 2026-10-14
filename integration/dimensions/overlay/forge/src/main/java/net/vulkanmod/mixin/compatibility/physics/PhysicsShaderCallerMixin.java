package net.vulkanmod.mixin.compatibility.physics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;

/** The plugin verifies and transports only the pinned original helper body. */
@Pseudo
@Mixin(targets="net.diebuddies.opengl.Shader",remap=false)
public abstract class PhysicsShaderCallerMixin {}
