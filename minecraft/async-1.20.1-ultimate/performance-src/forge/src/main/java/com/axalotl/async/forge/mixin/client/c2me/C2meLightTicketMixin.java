package com.axalotl.async.forge.mixin.client.c2me;

import net.minecraft.server.level.ChunkMap;
import org.spongepowered.asm.mixin.Mixin;

/** Runs the semantic ticket pairing after C2ME's priority-1000 worldgen mixin. */
@Mixin(value = ChunkMap.class, priority = 900)
public abstract class C2meLightTicketMixin {}
