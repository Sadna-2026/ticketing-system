package com.ticketing.domain.event;

/**
 * Publishes domain events to registered listeners.
 */
public interface IEventPublisher {
    /**
     * Publishes an event to all registered listeners.
     * @param event the event to publish
     */
    void publish(IEvent event);
    
    /**
     * Subscribes a listener to events of a specific type.
     * @param eventType the type of event to listen for
     * @param listener the listener to register
     */
    void subscribe(String eventType, IEventListener listener);
}
