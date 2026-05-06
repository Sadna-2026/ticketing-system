package com.ticketing.infrastructure;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

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

    // @Override
    // public Optional<VirtualQueue> findBySessionId(UUID sessionId) {
    //     if (sessionId == null) return Optional.empty();
    //     for (VirtualQueue q : store.values()) {
    //         if (sessionId.equals(q.getSessionId())) return Optional.of(q);
    //     }
    //     return Optional.empty();
    // }

    @Override
    public List<VirtualQueue> findAll() {
        return new ArrayList<>(store.values());
    }

    @Override
    public Optional<VirtualQueue> findByEventId(UUID eventId) {
        if (eventId == null) return Optional.empty();
        for (VirtualQueue q : store.values()) {
            if (eventId.equals(q.getEventId())) return Optional.of(q);
        }
        return Optional.empty();
    }

    @Override
    public void save(VirtualQueue queue) {
        if (queue == null) throw new IllegalArgumentException("queue is required");
        store.put(queue.getId(), queue);
    }

    @Override
    public void delete(UUID id) {
        if (id == null) return;
        store.remove(id);
    }

    @Override
    public List<VirtualQueue> findAllActive() {
        return store.values().stream()
                .filter(VirtualQueue::isActive)
                .collect(Collectors.toList());
    }
}
