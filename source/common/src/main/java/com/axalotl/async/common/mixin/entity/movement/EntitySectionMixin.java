package com.axalotl.async.common.mixin.entity.movement;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import net.minecraft.util.AbortableIterationConsumer;
import net.minecraft.util.ClassInstanceMultiMap;
import net.minecraft.world.level.entity.EntityAccess;
import net.minecraft.world.level.entity.EntitySection;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.level.entity.Visibility;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Thread-safe EntitySection access for async entity ticking.
 *
 * The typed-query/isEmpty hardening is based on the later 1.20.1 Async fork
 * fix by MrCreeperman147 (commit 9c8d2a8). This version also snapshots the
 * untyped stream while holding the same storage lock, closing the remaining
 * concurrent-iteration window.
 */
@Mixin(EntitySection.class)
public class EntitySectionMixin<T extends EntityAccess> {
    @Shadow @Final private ClassInstanceMultiMap<T> storage;
    @Shadow private Visibility chunkStatus;

    @Unique private final AtomicReference<Visibility> async$atomicStatus =
            new AtomicReference<>(Visibility.HIDDEN);
    @Unique private final Object async$storageLock = new Object();

    @Inject(method = "<init>", at = @At("TAIL"))
    private void async$init(Class<?> clazz, Visibility status, CallbackInfo ci) {
        async$atomicStatus.set(status != null ? status : Visibility.HIDDEN);
    }

    @Overwrite
    public void add(T entity) {
        synchronized (async$storageLock) {
            storage.add(entity);
        }
    }

    @Overwrite
    public boolean remove(T entity) {
        synchronized (async$storageLock) {
            return storage.remove(entity);
        }
    }

    @Overwrite
    public boolean isEmpty() {
        synchronized (async$storageLock) {
            return storage.isEmpty();
        }
    }

    @Overwrite
    public Visibility getStatus() {
        return async$atomicStatus.get();
    }

    @Overwrite
    public Visibility updateChunkStatus(Visibility status) {
        Visibility safe = status != null ? status : Visibility.HIDDEN;
        Visibility old = async$atomicStatus.getAndSet(safe);
        chunkStatus = safe;
        return old != null ? old : Visibility.HIDDEN;
    }

    @WrapMethod(method = "getEntities()Ljava/util/stream/Stream;")
    private Stream<T> async$snapshotEntities(Operation<Stream<T>> original) {
        List<T> snapshot;
        synchronized (async$storageLock) {
            snapshot = new ArrayList<>(storage);
        }
        return snapshot.stream().filter(Objects::nonNull);
    }

    @Overwrite
    public <U extends T> AbortableIterationConsumer.Continuation getEntities(
            EntityTypeTest<T, U> filter,
            AABB bounds,
            AbortableIterationConsumer<? super U> consumer) {
        List<T> snapshot;
        synchronized (async$storageLock) {
            snapshot = new ArrayList<>(storage);
        }
        for (T entity : snapshot) {
            U casted = filter.tryCast(entity);
            if (casted != null && casted.getBoundingBox().intersects(bounds)) {
                if (consumer.accept(casted) == AbortableIterationConsumer.Continuation.ABORT) {
                    return AbortableIterationConsumer.Continuation.ABORT;
                }
            }
        }
        return AbortableIterationConsumer.Continuation.CONTINUE;
    }
}
