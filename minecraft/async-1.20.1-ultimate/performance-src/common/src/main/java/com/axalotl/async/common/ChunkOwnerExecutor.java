package com.axalotl.async.common;

import java.util.concurrent.Executor;

/** The same owner queue pumped by the entity batch barrier and chunk futures. */
public interface ChunkOwnerExecutor {
    Executor harimt$ownerExecutor();
}
