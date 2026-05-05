package com.ticketing.infrastructure;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Repository;

import com.ticketing.domain.queue.IQueueRepository;
import com.ticketing.domain.queue.VirtualQueue;

@Repository
public class InMemoryQueueRepository implements IQueueRepository {

    private final ConcurrentHashMap<UUID, VirtualQueue> store = new ConcurrentHashMap<>();

    @Override
    public Optional<VirtualQueue> findById(UUID id) {
        if (id == null) return Optional.empty();
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public Optional<VirtualQueue> findBySessionId(UUID sessionId) {
        if (sessionId == null) return Optional.empty();
        for (VirtualQueue q : store.values()) {
            if (sessionId.equals(q.getSessionId())) return Optional.of(q);
        }
        return Optional.empty();
    }

    @Override
    public List<QueueSession> findAll() {
        return new ArrayList<>(store.values());
    }

    @Override
    public List<VirtualQueue> findByEventId(UUID eventId) {
        if (eventId == null) return List.of();
        List<VirtualQueue> hits = new ArrayList<>();
        for (VirtualQueue q : store.values()) {
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

    @Override
    public void save(VirtualQueue session) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'save'");
    }

    @Override
    public List<VirtualQueue> findAllActive() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'findAllActive'");
    }
}
