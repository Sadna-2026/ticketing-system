package com.ticketing.infrastructure;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import com.ticketing.domain.exception.OptimisticLockException;
import com.ticketing.domain.order.ActiveOrder;
import com.ticketing.infrastructure.Interface.IActiveOrderRepository;

public class InMemoryActiveOrderRepository implements IActiveOrderRepository {

    private final ConcurrentHashMap<UUID, VersionedEntry> store = new ConcurrentHashMap<>();

    @Override
    public Optional<ActiveOrder> findActiveBySessionId(UUID sessionId) {
        return store.values().stream()
                .map(e -> e.entity)
                .filter(o -> o.getSessionId().equals(sessionId) && o.isActive())
                .findFirst();
    }

    @Override
    public void save(ActiveOrder order) {
        store.compute(order.getId(), (id, existing) -> {
            if (existing == null) {
                order.incrementVersion();
                return new VersionedEntry(order, order.getVersion());
            }
            if (order.getVersion() != existing.version) {
                throw new OptimisticLockException("ActiveOrder", id);
            }
            order.incrementVersion();
            return new VersionedEntry(order, order.getVersion());
        });
    }

    @Override
    public Optional<ActiveOrder> findById(UUID id) {
        VersionedEntry entry = store.get(id);
        return entry != null ? Optional.of(entry.entity) : Optional.empty();
    }

    private static class VersionedEntry {
        final ActiveOrder entity;
        final int version;

        VersionedEntry(ActiveOrder entity, int version) {
            this.entity = entity;
            this.version = version;
        }
    }

    @Override
    public List<ActiveOrder> findAllActive() {
        return store.values().stream()
                .map(e -> e.entity)
                .filter(ActiveOrder::isActive)
                .collect(Collectors.toList());
    }

}
