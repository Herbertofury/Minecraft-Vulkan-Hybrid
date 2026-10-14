package net.vulkanmod.mixin.compatibility.essential;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;

/** The plugin redirects only exact reviewed shader call sites in these GAME classes. */
@Pseudo
@Mixin(targets={"gg.essential.elementa.shaders.Shader","gg.essential.elementa.shaders.FloatUniform","gg.essential.elementa.shaders.IntUniform","gg.essential.elementa.shaders.Vec2Uniform","gg.essential.elementa.shaders.Vec4Uniform"},remap=false)
public abstract class EssentialShaderCallerMixin {}
