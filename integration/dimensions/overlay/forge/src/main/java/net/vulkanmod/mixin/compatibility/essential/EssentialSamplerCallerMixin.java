package net.vulkanmod.mixin.compatibility.essential;
import org.spongepowered.asm.mixin.Mixin;import org.spongepowered.asm.mixin.Pseudo;
/** The exact original typed sampler's six calls are transported by the production GAME plugin. */
@Pseudo @Mixin(targets="gg.essential.universal.render.UGpuSamplerImpl",remap=false)
public abstract class EssentialSamplerCallerMixin {}
