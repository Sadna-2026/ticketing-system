package com.ticketing.infrastructure;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import com.ticketing.domain.event.IEvent;

/**
 * A wrapper for InMemoryEventPublisher that tracks published events for verification in tests.
 */
public class TestEventPublisher extends InMemoryEventPublisher {
    private final List<IEvent> publishedEvents = new CopyOnWriteArrayList<>();

    @Override
    public void publish(IEvent event) {
        publishedEvents.add(event);
        super.publish(event);
    }

    public List<IEvent> getPublishedEvents() {
        return new ArrayList<>(publishedEvents);
    }
}
