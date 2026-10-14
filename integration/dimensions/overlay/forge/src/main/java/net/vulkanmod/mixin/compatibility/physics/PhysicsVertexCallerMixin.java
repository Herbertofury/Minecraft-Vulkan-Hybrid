package net.vulkanmod.mixin.compatibility.physics;
import org.spongepowered.asm.mixin.Mixin;import org.spongepowered.asm.mixin.Pseudo;
@Pseudo @Mixin(targets={"net.diebuddies.opengl.VAO$GL43VertexAttribBinding","net.diebuddies.opengl.VAO$ARBVertexAttribBinding","net.diebuddies.opengl.VAO$GL44MultiBind","net.diebuddies.opengl.VAO$ARBMultiBind"},remap=false)
public abstract class PhysicsVertexCallerMixin {}
