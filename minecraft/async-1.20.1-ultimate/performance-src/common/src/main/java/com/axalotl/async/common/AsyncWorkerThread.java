package com.axalotl.async.common;

/** An ownership marker; does not depend on a mutable thread name or registry scan. */
public final class AsyncWorkerThread extends Thread {
    public AsyncWorkerThread(Runnable action, String name) {
        super(action, name);
    }
}
