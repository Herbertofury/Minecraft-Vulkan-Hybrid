package com.axalotl.async.forge.mixin.client.rubidium;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;

/** The config plugin adds the monitor flag, preserving the complete original method. */
@Pseudo
@Mixin(targets = "me.jellysquid.mods.sodium.client.world.cloned.ClonedChunkSectionCache", remap = false)
public abstract class RubidiumChunkCacheMixin {}
