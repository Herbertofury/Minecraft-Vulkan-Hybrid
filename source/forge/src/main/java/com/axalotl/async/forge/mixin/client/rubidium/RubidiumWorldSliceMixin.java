package com.axalotl.async.forge.mixin.client.rubidium;

import com.axalotl.async.forge.client.ModdedBiomeTint;
import com.axalotl.async.forge.client.RubidiumBiomeAccess;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BiomeColors;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ColorResolver;
import net.minecraft.world.level.biome.Biome;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.IdentityHashMap;
import java.util.HashMap;
import java.util.function.Function;

@Pseudo
@Mixin(targets = "me.jellysquid.mods.sodium.client.world.WorldSlice", remap = false)
public abstract class RubidiumWorldSliceMixin {
    @Unique private final IdentityHashMap<ColorResolver, HashMap<Long, Integer>> harimt$customColors = new IdentityHashMap<>();
    @Unique private Function<BlockPos, Biome> harimt$biomes;
    @Inject(method = "copyData", at = @At("HEAD"), remap = false)
    private void harimt$resetColors(CallbackInfo ci) {
        harimt$customColors.clear();
    }
    @Inject(method = {"m_6171_(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/ColorResolver;)I",
                      "getBlockTint(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/ColorResolver;)I"},
            at = @At("HEAD"), cancellable = true, remap = false)
    private void harimt$moddedColors(BlockPos position, ColorResolver resolver, CallbackInfoReturnable<Integer> cir) {
        if (resolver == BiomeColors.GRASS_COLOR_RESOLVER || resolver == BiomeColors.FOLIAGE_COLOR_RESOLVER
                || resolver == BiomeColors.WATER_COLOR_RESOLVER) return;
        if (harimt$biomes == null) harimt$biomes = RubidiumBiomeAccess.snapshot(this);
        HashMap<Long, Integer> colors = harimt$customColors.computeIfAbsent(resolver, ignored -> new HashMap<>());
        cir.setReturnValue(colors.computeIfAbsent(position.asLong(), ignored -> ModdedBiomeTint.blend(
                position, resolver, Minecraft.getInstance().options.biomeBlendRadius().get(), harimt$biomes)));
    }
}
