package com.ticketing.infrastructure;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.springframework.stereotype.Repository;

import com.ticketing.domain.exception.OptimisticLockException;
import com.ticketing.domain.queue.IQueueRepository;
import com.ticketing.domain.queue.VirtualQueue;

/**
 * In-memory implementation of IQueueRepository with CAS-style optimistic locking.
 * Uses the aggregate version counter to detect concurrent modifications.
 * On save, if the entity's version does not match the stored version, an
 * OptimisticLockException is thrown, signaling the caller to retry.
 */
@Repository
public class InMemoryQueueRepository implements IQueueRepository {

    private final ConcurrentHashMap<UUID, VirtualQueue> store = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, UUID> idsByEventId = new ConcurrentHashMap<>();

    @Override
    public Optional<VirtualQueue> findById(UUID id) {
        if (id == null) return Optional.empty();
        VirtualQueue queue = store.get(id);
        return queue != null ? Optional.of(queue.detachedCopy()) : Optional.empty();
    }

    @Override
    public List<VirtualQueue> findAll() {
        List<VirtualQueue> all = new ArrayList<>(store.size());
        for (VirtualQueue queue : store.values()) {
            all.add(queue.detachedCopy());
        }
        return all;
    }

    @Override
    public Optional<VirtualQueue> findByEventId(UUID eventId) {
        if (eventId == null) return Optional.empty();
        UUID queueId = idsByEventId.get(eventId);
        return queueId == null ? Optional.empty() : findById(queueId);
    }

    @Override
    public void save(VirtualQueue queue) {
        if (queue == null) throw new IllegalArgumentException("queue is required");
        UUID reservedQueueId = idsByEventId.putIfAbsent(queue.getEventId(), queue.getId());
        if (reservedQueueId != null && !reservedQueueId.equals(queue.getId())) {
            throw new IllegalStateException("A virtual queue already exists for this event");
        }
        store.compute(queue.getId(), (id, existing) -> {
            if (existing == null) {
                queue.incrementVersion();
                VirtualQueue stored = queue.detachedCopy();
                return stored;
            }
            if (queue.getVersion() != existing.getVersion()) {
                throw new OptimisticLockException("VirtualQueue", id);
            }
            queue.incrementVersion();
            VirtualQueue stored = queue.detachedCopy();
            return stored;
        });
    }

    @Override
    public void delete(UUID id) {
        if (id == null) return;
        VirtualQueue removed = store.remove(id);
        if (removed != null) {
            idsByEventId.remove(removed.getEventId(), id);
        }
    }

    @Override
    public List<VirtualQueue> findAllActive() {
        return store.values().stream()
                .map(VirtualQueue::detachedCopy)
                .filter(VirtualQueue::isActive)
                .collect(Collectors.toList());
    }

    @Override
    public void deleteAll() {
        store.clear();
        idsByEventId.clear();
    }
}
