package com.ticketing.domain.queue;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface IQueueRepository {
    Optional<VirtualQueue> findById(UUID id);
    //Optional<VirtualQueue> findBySessionId(UUID sessionId);
    List<VirtualQueue> findAll();
    Optional<VirtualQueue> findByEventId(UUID eventId);
    void save(VirtualQueue session);
    void delete(UUID id);
    List<VirtualQueue> findAllActive();
}
