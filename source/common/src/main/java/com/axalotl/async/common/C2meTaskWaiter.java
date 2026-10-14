package com.axalotl.async.common;

import net.minecraft.server.level.ServerLevel;
import java.util.List;
import java.util.concurrent.Future;
import java.util.function.BooleanSupplier;
import java.util.function.LongConsumer;

/** Keep vanilla owner queues progressing while C2ME completes worker chunk requests. */
public final class C2meTaskWaiter {
    private C2meTaskWaiter() {}

    public static void await(ServerLevel world, List<? extends Future<?>> futures,
                             long timeoutNs, LongConsumer onSlowWait) {
        if (futures.isEmpty()) return;
        long started = System.nanoTime();
        boolean[] interrupted = {Thread.interrupted()};
        boolean[] warned = {false};
        TaskFailures failures = new TaskFailures();
        BooleanSupplier complete = () -> {
            if (Thread.interrupted()) interrupted[0] = true;
            boolean done = true;
            for (Future<?> future : futures) {
                if (!future.isDone()) { done = false; break; }
            }
            if (done) return true;
            long elapsed = System.nanoTime() - started;
            if (!warned[0] && elapsed > timeoutNs) {
                warned[0] = true;
                onSlowWait.accept(elapsed);
            }
            world.getChunkSource().pollTask();
            return false;
        };
        // managedBlock allows due owner work even after the current tick's time
        // budget expires. Polling only this world's chunk queue can strand C2ME
        // futures whose completion needs the server or another world's queue.
        for (;;) {
            try {
                world.getServer().managedBlock(complete);
                break;
            } catch (Throwable ownerFailure) {
                // Finish every worker before surfacing an owner task failure.
                failures.record(ownerFailure);
            }
        }
        if (interrupted[0]) Thread.currentThread().interrupt();
        try {
            TaskFailures.joinCompleted(futures);
        } catch (Throwable workerFailure) {
            failures.record(workerFailure);
        }
        failures.rethrow();
    }
}
