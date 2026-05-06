package com.ticketing.infrastructure;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import com.ticketing.domain.exception.OptimisticLockException;
import com.ticketing.domain.order.ActiveOrder;
import com.ticketing.domain.order.IOrderRepository;

public class InMemoryOrderRepository implements IOrderRepository {

    private final ConcurrentHashMap<UUID, VersionedActiveOrder> activeOrders = new ConcurrentHashMap<>();

    @Override
    public Optional<ActiveOrder> findActiveBySessionId(UUID sessionId) {
        if (sessionId == null) return Optional.empty();
        return activeOrders.values().stream()
                .map(e -> e.entity)
                .filter(o -> sessionId.equals(o.getSessionId()) && o.isActive())
                .findFirst();
    }

    @Override
    public void save(ActiveOrder order) {
        if (order == null) throw new IllegalArgumentException("order cannot be null");
        activeOrders.compute(order.getId(), (id, existing) -> {
            if (existing == null) {
                order.incrementVersion();
                return new VersionedActiveOrder(order, order.getVersion());
            }
            if (order.getVersion() != existing.version) {
                throw new OptimisticLockException("ActiveOrder", id);
            }
            order.incrementVersion();
            return new VersionedActiveOrder(order, order.getVersion());
        });
    }

    @Override
    public Optional<ActiveOrder> findById(UUID orderId) {
        VersionedActiveOrder entry = activeOrders.get(orderId);
        return entry != null ? Optional.of(entry.entity) : Optional.empty();
    }

    @Override
    public List<ActiveOrder> findAllActive() {
        return activeOrders.values().stream()
                .map(e -> e.entity)
                .filter(ActiveOrder::isActive)
                .collect(Collectors.toList());
    }

    @Override
    public List<ActiveOrder> findActiveByEventId(UUID eventId) {
        if (eventId == null) return List.of();
        return activeOrders.values().stream()
                .map(e -> e.entity)
                .filter(o -> o.isActive() && eventId.equals(o.getEventId()))
                .collect(Collectors.toList());
    }

    private static class VersionedActiveOrder {
        final ActiveOrder entity;
        final int version;

        VersionedActiveOrder(ActiveOrder entity, int version) {
            this.entity = entity;
            this.version = version;
        }
    }
}
