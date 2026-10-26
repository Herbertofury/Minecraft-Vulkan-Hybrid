package net.vulkanmod.mixin.compatibility.essential;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
@Pseudo
@Mixin(targets={"gg.essential.universal.render.ViewportState","gg.essential.universal.render.ViewportState$Companion","gg.essential.util.GlViewportState","gg.essential.util.GlViewportState$Companion"},remap=false)
public abstract class EssentialViewportCallerMixin {}
