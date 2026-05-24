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
 * Uses a version counter alongside the entity to detect concurrent modifications.
 * On save, if the entity's version does not match the stored version, an
 * OptimisticLockException is thrown, signaling the caller to retry.
 */
@Repository
public class InMemoryQueueRepository implements IQueueRepository {

    private final ConcurrentHashMap<UUID, VersionedEntry<VirtualQueue>> store = new ConcurrentHashMap<>();

    @Override
    public void save(VirtualQueue queue) {
        if (queue == null) throw new IllegalArgumentException("queue is required");
        store.compute(queue.getId(), (id, existing) -> {
            if (existing == null) {
                queue.incrementVersion();
                return new VersionedEntry<>(queue, queue.getVersion());
            }
            if (queue.getVersion() != existing.version) {
                throw new OptimisticLockException("VirtualQueue", id);
            }
            queue.incrementVersion();
            return new VersionedEntry<>(queue, queue.getVersion());
        });
    }

    @Override
    public Optional<VirtualQueue> findById(UUID id) {
        if (id == null) return Optional.empty();
        VersionedEntry<VirtualQueue> entry = store.get(id);
        return entry != null ? Optional.of(entry.entity) : Optional.empty();
    }

    @Override
    public List<VirtualQueue> findAll() {
        List<VirtualQueue> all = new ArrayList<>(store.size());
        for (VersionedEntry<VirtualQueue> entry : store.values()) {
            all.add(entry.entity);
        }
        return all;
    }

    @Override
    public Optional<VirtualQueue> findByEventId(UUID eventId) {
        if (eventId == null) return Optional.empty();
        for (VersionedEntry<VirtualQueue> entry : store.values()) {
            if (eventId.equals(entry.entity.getEventId())) return Optional.of(entry.entity);
        }
        return Optional.empty();
    }

    @Override
    public void delete(UUID id) {
        if (id == null) return;
        store.remove(id);
    }

    @Override
    public List<VirtualQueue> findAllActive() {
        return store.values().stream()
                .map(entry -> entry.entity)
                .filter(VirtualQueue::isActive)
                .collect(Collectors.toList());
    }

    private static class VersionedEntry<T> {
        final T entity;
        final int version;

        VersionedEntry(T entity, int version) {
            this.entity = entity;
            this.version = version;
        }
    }
}
