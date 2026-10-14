package net.vulkanmod.mixin.compatibility.essential;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
/** Production plugin inserts reviewed native branches; original GL branches stay intact. */
@Pseudo
@Mixin(targets={"gg.essential.universal.render.UGpuFenceImplKt","gg.essential.universal.render.UGpuFenceImpl"},remap=false)
public abstract class EssentialSyncCallerMixin {}
