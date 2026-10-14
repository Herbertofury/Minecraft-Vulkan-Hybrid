package net.vulkanmod.mixin.compatibility.essential;
import org.spongepowered.asm.mixin.Mixin;import org.spongepowered.asm.mixin.Pseudo;
/** Production transport of the four pinned original managed-state GAME classes. */
@Pseudo
@Mixin(targets={"gg.essential.universal.render.ManagedGlState","gg.essential.universal.render.ManagedGlState$Companion","gg.essential.universal.shader.BlendState","gg.essential.universal.shader.BlendState$Companion"},remap=false)
public abstract class EssentialManagedStateCallerMixin {}
