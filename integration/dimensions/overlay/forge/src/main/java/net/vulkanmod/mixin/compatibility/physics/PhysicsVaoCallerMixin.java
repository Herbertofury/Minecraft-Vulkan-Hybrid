package net.vulkanmod.mixin.compatibility.physics;
import org.spongepowered.asm.mixin.Mixin;import org.spongepowered.asm.mixin.Pseudo;
@Pseudo @Mixin(targets={"net.diebuddies.opengl.VAO","net.diebuddies.opengl.VertexFormat","net.diebuddies.opengl.StateTracker"},remap=false)
public abstract class PhysicsVaoCallerMixin {}
