package com.axalotl.async.common;

import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;

/** Preserve task failures, finish the worker barrier, then return the real failure to Minecraft. */
public final class TaskFailures {
    private final AtomicReference<Throwable> first = new AtomicReference<>();

    public boolean failed() { return first.get() != null; }

    public void record(Throwable failure) {
        if (!first.compareAndSet(null, failure)) {
            Throwable original = first.get();
            if (original != failure) original.addSuppressed(failure);
        }
    }

    public void rethrow() {
        Throwable failure = first.get();
        if (failure instanceof Error error) throw error;
        if (failure instanceof RuntimeException runtime) throw runtime;
        if (failure != null) throw new IllegalStateException("Parallel simulation task failed", failure);
    }

    public static void joinCompleted(List<? extends Future<?>> futures) {
        TaskFailures failures = new TaskFailures();
        boolean interrupted = Thread.interrupted();
        if (interrupted) failures.record(new InterruptedException("Interrupted before worker join"));
        for (Future<?> future : futures) {
            boolean done = false;
            while (!done) {
                try {
                    future.get();
                    done = true;
                } catch (ExecutionException failure) {
                    failures.record(failure.getCause());
                    done = true;
                } catch (CancellationException failure) {
                    failures.record(failure);
                    done = true;
                } catch (InterruptedException failure) {
                    if (!interrupted) failures.record(failure);
                    interrupted = true;
                    // Clear the interruption only while finishing the barrier. Restore it below.
                }
            }
        }
        if (interrupted) Thread.currentThread().interrupt();
        failures.rethrow();
    }
}
