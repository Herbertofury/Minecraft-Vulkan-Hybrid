package net.vulkanmod.mixin.compatibility.xaero;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
@Pseudo
@Mixin(targets={"xaero.map.graphics.PixelBuffers","xaero.common.graphics.PixelBuffers"},remap=false)
public abstract class XaeroPixelCallerMixin {}
