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
@org.springframework.stereotype.Component
public class InMemoryEventPublisher implements IEventPublisher {
    private final ConcurrentHashMap<String, List<IEventListener>> listeners = new ConcurrentHashMap<>();

    @Override
    public void publish(IEvent event) {
        String eventType = event.getEventType();
        List<IEventListener> eventListeners = listeners.get(eventType);
        
        if (eventListeners != null) {
            for (IEventListener listener : eventListeners) {
                try {
                    listener.handle(event);
                } catch (Exception e) {
                    System.err.println("Error handling event of type " + eventType + ": " + e.getMessage());
                    if (e instanceof RuntimeException) throw (RuntimeException) e;
                    throw new RuntimeException("Event listener failed", e);
                }
            }
        }
    }

    @Override
    public void subscribe(String eventType, IEventListener listener) {
        if (eventType == null || listener == null) {
            throw new IllegalArgumentException("Event type and listener cannot be null");
        }
        
        listeners.computeIfAbsent(eventType, k -> new ArrayList<>()).add(listener);
    }
}

