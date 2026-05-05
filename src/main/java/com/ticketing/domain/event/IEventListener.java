package com.ticketing.domain.event;

/**
 * Listens for domain events and handles them.
 */
public interface IEventListener {
    /**
     * Handles an event.
     * @param event the event to handle
     */
    void handle(IEvent event);
}
