package com.ticketing.domain.event;

import java.util.Optional;
import java.util.UUID;

public interface IEventRepository {

    Optional<Event> findById(UUID eventId);

    void save(Event event);

}