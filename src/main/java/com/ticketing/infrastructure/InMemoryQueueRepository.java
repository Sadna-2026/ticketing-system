package com.ticketing.infrastructure;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Repository;

import com.ticketing.domain.queue.IQueueRepository;
import com.ticketing.domain.queue.QueueSession;

@Repository
public class InMemoryQueueRepository implements IQueueRepository {

    private final ConcurrentHashMap<UUID, QueueSession> store = new ConcurrentHashMap<>();

    @Override
    public Optional<QueueSession> findById(UUID id) {
        if (id == null) return Optional.empty();
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public List<QueueSession> findAll() {
        return new ArrayList<>(store.values());
    }

    @Override
    public List<QueueSession> findByEventId(UUID eventId) {
        if (eventId == null) return List.of();
        List<QueueSession> hits = new ArrayList<>();
        for (QueueSession q : store.values()) {
            if (eventId.equals(q.getEventId())) hits.add(q);
        }
        return hits;
    }

    @Override
    public void save(QueueSession session) {
        if (session == null) throw new IllegalArgumentException("session is required");
        store.put(session.getId(), session);
    }

    @Override
    public void delete(UUID id) {
        if (id == null) return;
        store.remove(id);
    }
}
