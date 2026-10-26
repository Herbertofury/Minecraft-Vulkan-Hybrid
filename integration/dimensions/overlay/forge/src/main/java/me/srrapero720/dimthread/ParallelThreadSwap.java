package me.srrapero720.dimthread;

import java.util.*;
import java.util.concurrent.locks.ReentrantLock;
import me.srrapero720.dimthread.thread.IMutableMainThread;

/** Per-object ownership, ordered locking, identity deduplication and exception-safe restoration. */
public final class ParallelThreadSwap {
    private static final IdentityHashMap<Object, Entry> locks = new IdentityHashMap<>();
    private static long order;
    private record Entry(long order, ReentrantLock lock) {}
    private ParallelThreadSwap() {}

    public static void clear() { synchronized (locks) { locks.clear(); } }

    public static void swapThreadsAndRun(Runnable action, Object... objects) {
        Objects.requireNonNull(action);
        Objects.requireNonNull(objects);
        List<IMutableMainThread> owners = new ArrayList<>();
        List<Entry> entries = new ArrayList<>();
        IdentityHashMap<Object, Boolean> seen = new IdentityHashMap<>();
        synchronized (locks) {
            for (Object object : objects) {
                Objects.requireNonNull(object);
                if (!(object instanceof IMutableMainThread owner)) throw new IllegalArgumentException("Object has no mutable owner: " + object.getClass());
                if (seen.put(object, true) != null) continue;
                owners.add(owner);
                entries.add(locks.computeIfAbsent(object, k -> new Entry(order++, new ReentrantLock())));
            }
        }
        entries.sort(Comparator.comparingLong(Entry::order));
        for (Entry entry : entries) entry.lock().lock();
        Thread[] previous = new Thread[owners.size()];
        int changed = 0;
        Throwable failure = null;
        try {
            for (int i = 0; i < owners.size(); i++) {
                IMutableMainThread owner = owners.get(i);
                previous[i] = owner.dimThreads$getMainThread();
                // Include a setter that mutates and then throws in restoration.
                changed = i + 1;
                owner.dimThreads$setMainThread(Thread.currentThread());
            }
            action.run();
        } catch (Throwable thrown) {
            failure = thrown;
        } finally {
            for (int i = changed - 1; i >= 0; i--) {
                try { owners.get(i).dimThreads$setMainThread(previous[i]); }
                catch (Throwable restore) {
                    if (failure == null) failure = restore;
                    else if (failure != restore) failure.addSuppressed(restore);
                }
            }
            for (int i = entries.size() - 1; i >= 0; i--) entries.get(i).lock().unlock();
        }
        if (failure instanceof RuntimeException runtime) throw runtime;
        if (failure instanceof Error error) throw error;
        if (failure != null) throw new IllegalStateException("Dimension ownership transfer failed", failure);
    }
}
