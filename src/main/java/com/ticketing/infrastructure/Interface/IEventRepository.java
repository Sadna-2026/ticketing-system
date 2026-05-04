package com.ticketing.infrastructure.Interface;

import com.ticketing.domain.event.Event;

import java.util.Optional;
import java.util.UUID;

public interface IEventRepository {

    Optional<Event> findById(UUID eventId);

    void save(Event event);

}

