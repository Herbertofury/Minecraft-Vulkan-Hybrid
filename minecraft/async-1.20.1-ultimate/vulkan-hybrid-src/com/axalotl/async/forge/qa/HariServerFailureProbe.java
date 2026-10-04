package com.axalotl.async.forge.qa;

import com.axalotl.async.common.ParallelProcessor;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import java.util.concurrent.atomic.AtomicBoolean;

/** Registered only by the explicit failure-path QA property; absent from normal event dispatch. */
public final class HariServerFailureProbe {
    private final AtomicBoolean fired = new AtomicBoolean();

    @SubscribeEvent
    public void tick(LivingEvent.LivingTickEvent event) {
        if (event.getEntity().level().isClientSide()
                || !event.getEntity().getTags().contains("harimt_qa")
                || !ParallelProcessor.isServerExecutionThread()
                || !fired.compareAndSet(false, true)) return;
        throw new IllegalStateException("HARI_QA_INTENTIONAL_ASYNC_ENTITY_TICK_FAULT");
    }
}
