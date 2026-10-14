package com.axalotl.async.common.mixin.world;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Consumer;
import java.util.function.Predicate;
import net.minecraft.core.IdMap;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.chunk.PalettedContainerRO;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/**
 * Forge 1.20.1 adaptation of Async's July 2026 palette hardening.
 * Saves/network serialization and readers share a read lock; palette mutation
 * takes the write lock. This prevents async entity/world work from racing a
 * chunk snapshot into corrupt palette data.
 */
@Mixin(PalettedContainer.class)
public abstract class PalettedContainerMixin<T> {
    @Unique
    private final ReentrantReadWriteLock harimt$paletteLock = new ReentrantReadWriteLock();

    @WrapMethod(method = "get(III)Ljava/lang/Object;")
    private T harimt$get(int x, int y, int z, Operation<T> original) {
        harimt$paletteLock.readLock().lock();
        try {
            return original.call(x, y, z);
        } finally {
            harimt$paletteLock.readLock().unlock();
        }
    }

    @WrapMethod(method = "getAndSet(IIILjava/lang/Object;)Ljava/lang/Object;")
    private T harimt$getAndSet(int x, int y, int z, T value, Operation<T> original) {
        harimt$paletteLock.writeLock().lock();
        try {
            return original.call(x, y, z, value);
        } finally {
            harimt$paletteLock.writeLock().unlock();
        }
    }

    @WrapMethod(method = "getAndSetUnchecked")
    private T harimt$getAndSetUnchecked(int x, int y, int z, T value, Operation<T> original) {
        harimt$paletteLock.writeLock().lock();
        try {
            return original.call(x, y, z, value);
        } finally {
            harimt$paletteLock.writeLock().unlock();
        }
    }

    @WrapMethod(method = "set(IIILjava/lang/Object;)V")
    private void harimt$set(int x, int y, int z, T value, Operation<Void> original) {
        harimt$paletteLock.writeLock().lock();
        try {
            original.call(x, y, z, value);
        } finally {
            harimt$paletteLock.writeLock().unlock();
        }
    }

    @WrapMethod(method = "read")
    private void harimt$read(FriendlyByteBuf buffer, Operation<Void> original) {
        harimt$paletteLock.writeLock().lock();
        try {
            original.call(buffer);
        } finally {
            harimt$paletteLock.writeLock().unlock();
        }
    }

    @WrapMethod(method = "write")
    private void harimt$write(FriendlyByteBuf buffer, Operation<Void> original) {
        harimt$paletteLock.readLock().lock();
        try {
            original.call(buffer);
        } finally {
            harimt$paletteLock.readLock().unlock();
        }
    }

    @WrapMethod(method = "pack")
    private PalettedContainerRO.PackedData<T> harimt$pack(IdMap<T> registry,
                                                          PalettedContainer.Strategy strategy,
                                                          Operation<PalettedContainerRO.PackedData<T>> original) {
        harimt$paletteLock.readLock().lock();
        try {
            return original.call(registry, strategy);
        } finally {
            harimt$paletteLock.readLock().unlock();
        }
    }

    @WrapMethod(method = "getAll")
    private void harimt$getAll(Consumer<T> consumer, Operation<Void> original) {
        harimt$paletteLock.readLock().lock();
        try {
            original.call(consumer);
        } finally {
            harimt$paletteLock.readLock().unlock();
        }
    }

    @WrapMethod(method = "count")
    private void harimt$count(PalettedContainer.CountConsumer<T> output, Operation<Void> original) {
        harimt$paletteLock.readLock().lock();
        try {
            original.call(output);
        } finally {
            harimt$paletteLock.readLock().unlock();
        }
    }

    @WrapMethod(method = "copy")
    private PalettedContainer<T> harimt$copy(Operation<PalettedContainer<T>> original) {
        harimt$paletteLock.readLock().lock();
        try {
            return original.call();
        } finally {
            harimt$paletteLock.readLock().unlock();
        }
    }

    @WrapMethod(method = "maybeHas")
    private boolean harimt$maybeHas(Predicate<T> predicate, Operation<Boolean> original) {
        harimt$paletteLock.readLock().lock();
        try {
            return original.call(predicate);
        } finally {
            harimt$paletteLock.readLock().unlock();
        }
    }
}
