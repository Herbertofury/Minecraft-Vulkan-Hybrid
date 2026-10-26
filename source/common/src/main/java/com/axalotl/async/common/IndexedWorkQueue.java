package com.axalotl.async.common;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

/** Immutable snapshot with exactly-once claims and no per-item queue nodes. */
public final class IndexedWorkQueue<T> {
    private final Object[] items;
    private final AtomicInteger next = new AtomicInteger();

    public IndexedWorkQueue(List<? extends T> input) {
        this.items = input.toArray();
        for (Object item : this.items) Objects.requireNonNull(item, "work item");
    }

    @SuppressWarnings("unchecked")
    public T poll() {
        int index = this.next.getAndIncrement();
        return index < this.items.length ? (T) this.items[index] : null;
    }
}
