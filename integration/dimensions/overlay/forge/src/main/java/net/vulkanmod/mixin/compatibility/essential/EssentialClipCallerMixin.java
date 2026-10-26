package net.vulkanmod.mixin.compatibility.essential;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
/** The production plugin redirects these five exact reviewed GAME clipping callers. */
@Pseudo
@Mixin(targets={"gg.essential.universal.render.ScissorState","gg.essential.universal.render.ScissorState$Companion","gg.essential.util.ScissorState","gg.essential.util.ScissorState$Companion","gg.essential.elementa.effects.StencilEffect"},remap=false)
public abstract class EssentialClipCallerMixin {}
