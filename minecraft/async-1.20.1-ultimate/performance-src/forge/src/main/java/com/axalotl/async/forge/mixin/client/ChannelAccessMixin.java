package com.axalotl.async.forge.mixin.client;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.client.sounds.ChannelAccess;
import net.minecraft.client.sounds.SoundEngineExecutor;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;

/** Keep channel destruction behind previously queued source operations during reload. */
@Mixin(ChannelAccess.class)
public abstract class ChannelAccessMixin {
    @Shadow @Final private Executor executor;

    @WrapMethod(method = "clear")
    private void harimt$clearOnSoundThread(Operation<Void> original) {
        // Avoid waiting on ourselves when another mod initiates cleanup on the sound thread.
        if (executor instanceof SoundEngineExecutor sound && sound.isSameThread()) {
            original.call();
            return;
        }
        CompletableFuture<Void> cleared = new CompletableFuture<>();
        executor.execute(() -> {
            try {
                original.call();
                cleared.complete(null);
            } catch (Throwable failure) {
                cleared.completeExceptionally(failure);
            }
        });
        try {
            cleared.join();
        } catch (CompletionException failure) {
            // Retain the original failure, rather than turn failed audio cleanup into success.
            ChannelAccessMixin.<RuntimeException>harimt$rethrow(failure.getCause());
        }
    }

    private static <T extends Throwable> void harimt$rethrow(Throwable failure) throws T {
        throw (T) failure;
    }
}
