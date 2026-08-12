package com.unique.examine.core.api;

public interface OutboxFacade {
    /**
     * Enqueues an event in the caller's transaction and returns its generated event id.
     */
    long enqueue(OutboxEvent event);
}
