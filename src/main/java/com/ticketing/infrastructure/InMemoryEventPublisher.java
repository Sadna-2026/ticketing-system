package com.ticketing.infrastructure;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import com.ticketing.domain.event.IEvent;
import com.ticketing.domain.event.IEventListener;
import com.ticketing.domain.event.IEventPublisher;

/**
 * In-memory implementation of IEventPublisher.
 * Maintains a registry of listeners by event type and publishes events to them.
 */
public class InMemoryEventPublisher implements IEventPublisher {
    private final ConcurrentHashMap<String, List<IEventListener>> listeners = new ConcurrentHashMap<>();
    private final List<IEvent> publishedEvents = new java.util.concurrent.CopyOnWriteArrayList<>();

    @Override
    public void publish(IEvent event) {
        publishedEvents.add(event);
        String eventType = event.getEventType();
        List<IEventListener> eventListeners = listeners.get(eventType);
        
        if (eventListeners != null) {
            for (IEventListener listener : eventListeners) {
                try {
                    listener.handle(event);
                } catch (Exception e) {
                    // Log but don't propagate - prevent one listener from breaking others
                    System.err.println("Error handling event of type " + eventType + ": " + e.getMessage());
                    e.printStackTrace();
                }
            }
        }
    }

    public List<IEvent> getPublishedEvents() {
        return new ArrayList<>(publishedEvents);
    }

    @Override
    public void subscribe(String eventType, IEventListener listener) {
        if (eventType == null || listener == null) {
            throw new IllegalArgumentException("Event type and listener cannot be null");
        }
        
        listeners.computeIfAbsent(eventType, k -> new ArrayList<>()).add(listener);
    }
}
