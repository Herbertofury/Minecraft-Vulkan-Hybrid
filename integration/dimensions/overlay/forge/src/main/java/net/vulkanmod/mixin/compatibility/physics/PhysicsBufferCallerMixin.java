package net.vulkanmod.mixin.compatibility.physics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
/** The plugin preserves the exact original bodies and changes only supported call owners. */
@Pseudo
@Mixin(targets={"net.diebuddies.opengl.BufferObject","net.diebuddies.opengl.ArenaBuffer"},remap=false)
public abstract class PhysicsBufferCallerMixin {}
