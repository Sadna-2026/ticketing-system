package com.ticketing.infrastructure;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.ticketing.domain.event.Event;
import com.ticketing.domain.event.IEventRepository;
import com.ticketing.domain.exception.OptimisticLockException;

/**
 * In-memory implementation of IEventRepository with CAS-style optimistic locking.
 * Uses a version counter alongside the entity to detect concurrent modifications.
 * On save, if the entity's version does not match the stored version, an
 * OptimisticLockException is thrown, signaling the caller to retry.
 */
public class InMemoryEventRepository implements IEventRepository {

    private final ConcurrentHashMap<UUID, VersionedEntry<Event>> store = new ConcurrentHashMap<>();

    @Override
    public void save(Event event) {
        store.compute(event.getId(), (id, existing) -> {
            if (existing == null) {
                // New entity - first save
                event.incrementVersion();
                return new VersionedEntry<>(event, event.getVersion());
            }
            // Existing entity - check version for CAS
            if (event.getVersion() != existing.version) {
                throw new OptimisticLockException("Event", id);
            }
            event.incrementVersion();
            return new VersionedEntry<>(event, event.getVersion());
        });
    }

    @Override
    public Optional<Event> findById(UUID id) {
        VersionedEntry<Event> entry = store.get(id);
        return entry != null ? Optional.of(entry.entity) : Optional.empty();
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
