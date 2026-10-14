package me.srrapero720.dimthread.mixin.impl;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import me.srrapero720.dimthread.DimThread;
import me.srrapero720.dimthread.thread.IMutableMainThread;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.ChunkSource;
import net.minecraftforge.fml.loading.FMLLoader;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = ServerChunkCache.class, priority = 1001)
public abstract class ServerChunkCacheMixin extends ChunkSource implements IMutableMainThread {
	@Shadow public Thread mainThread;
	@Shadow @Final public ChunkMap chunkMap;
	@Shadow @Final public ServerLevel level;

	@Override
	@Unique
	public Thread dimThreads$getMainThread() {
		return this.mainThread;
	}

	@Override
	@Unique
	public void dimThreads$setMainThread(Thread thread) {
		this.mainThread = thread;
	}

}
