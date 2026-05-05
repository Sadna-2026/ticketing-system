package com.ticketing.domain.queue;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface IQueueRepository {
    Optional<QueueSession> findById(UUID id);
    List<QueueSession> findAll();
    List<QueueSession> findByEventId(UUID eventId);
    void save(QueueSession session);
    void delete(UUID id);
}
