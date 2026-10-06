package com.axalotl.async.common.mixin.world;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.util.function.Consumer;
import java.util.function.Predicate;
import net.minecraft.core.IdMap;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.chunk.PalettedContainerRO;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Preserve the existing per-container monitor used by Hari's SynchronisePlugin.
 * The real transformed Forge 1.20.1 class synchronizes all palette entry points,
 * including ModernFix/Embeddium accessors. A second read/write lock inside these
 * monitor-protected wrappers cannot allow concurrent readers and adds redundant
 * acquisition/thread-local bookkeeping. These wrappers explicitly synchronize
 * so their protection does not depend on the plugin's method annotation order.
 * Snapshot callbacks, nested mutation, exceptions and original operations retain
 * their existing monitor behavior; no optimistic or unlocked palette read occurs.
 */
@Mixin(PalettedContainer.class)
public abstract class PalettedContainerMixin<T> {
    @WrapMethod(method = "get(III)Ljava/lang/Object;")
    private synchronized T harimt$get(int x, int y, int z, Operation<T> original) {
        return original.call(x, y, z);
    }

    @WrapMethod(method = "getAndSet(IIILjava/lang/Object;)Ljava/lang/Object;")
    private synchronized T harimt$getAndSet(int x, int y, int z, T value, Operation<T> original) {
        return original.call(x, y, z, value);
    }

    @WrapMethod(method = "getAndSetUnchecked")
    private synchronized T harimt$getAndSetUnchecked(int x, int y, int z, T value, Operation<T> original) {
        return original.call(x, y, z, value);
    }

    @WrapMethod(method = "set(IIILjava/lang/Object;)V")
    private synchronized void harimt$set(int x, int y, int z, T value, Operation<Void> original) {
        original.call(x, y, z, value);
    }

    @WrapMethod(method = "read")
    private synchronized void harimt$read(FriendlyByteBuf buffer, Operation<Void> original) {
        original.call(buffer);
    }

    @WrapMethod(method = "write")
    private synchronized void harimt$write(FriendlyByteBuf buffer, Operation<Void> original) {
        original.call(buffer);
    }

    @WrapMethod(method = "pack")
    private synchronized PalettedContainerRO.PackedData<T> harimt$pack(IdMap<T> registry,
                                                          PalettedContainer.Strategy strategy,
                                                          Operation<PalettedContainerRO.PackedData<T>> original) {
        return original.call(registry, strategy);
    }

    @WrapMethod(method = "getAll")
    private synchronized void harimt$getAll(Consumer<T> consumer, Operation<Void> original) {
        original.call(consumer);
    }

    @WrapMethod(method = "count")
    private synchronized void harimt$count(PalettedContainer.CountConsumer<T> output, Operation<Void> original) {
        original.call(output);
    }

    @WrapMethod(method = "copy")
    private synchronized PalettedContainer<T> harimt$copy(Operation<PalettedContainer<T>> original) {
        return original.call();
    }

    @WrapMethod(method = "maybeHas")
    private synchronized boolean harimt$maybeHas(Predicate<T> predicate, Operation<Boolean> original) {
        return original.call(predicate);
    }
}
