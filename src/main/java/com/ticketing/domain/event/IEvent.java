package com.ticketing.domain.event;

/**
 * Base interface for domain events.
 * All domain events should implement this interface.
 */
public interface IEvent {
    /**
     * @return the type of event (e.g., "CompanyOpened")
     */
    String getEventType();
}
