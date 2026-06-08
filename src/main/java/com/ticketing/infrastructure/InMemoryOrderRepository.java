package com.ticketing.infrastructure;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import com.ticketing.domain.exception.OptimisticLockException;
import com.ticketing.domain.order.ActiveOrder;
import com.ticketing.domain.order.CompletedPurchase;
import com.ticketing.domain.order.IOrderRepository;

@org.springframework.stereotype.Component
@ConditionalOnProperty(name = "ticketing.persistence", havingValue = "memory", matchIfMissing = true)
public class InMemoryOrderRepository implements IOrderRepository {

    private final ConcurrentHashMap<UUID, ActiveOrder> activeOrders = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, CompletedPurchase> completedPurchases = new ConcurrentHashMap<>();

    @Override
    public Optional<ActiveOrder> findActiveBySessionId(UUID sessionId) {
        if (sessionId == null) return Optional.empty();
        return activeOrders.values().stream()
                .filter(o -> sessionId.equals(o.getSessionId()) && o.isActive())
                .findFirst();
    }

    @Override
    public Optional<ActiveOrder> findActiveByMemberId(UUID memberId) {
        if (memberId == null) return Optional.empty();
        return activeOrders.values().stream()
                .filter(o -> memberId.equals(o.getMemberId()) && o.isActive())
                .findFirst();
    }

    @Override
    public void save(ActiveOrder order) {
        if (order == null) throw new IllegalArgumentException("order cannot be null");
        activeOrders.compute(order.getId(), (id, existing) -> {
            if (existing == null) {
                order.incrementVersion();
                return order;
            }
            if (order.getVersion() != existing.getVersion()) {
                throw new OptimisticLockException("ActiveOrder", id);
            }
            order.incrementVersion();
            return order;
        });
    }

    @Override
    public Optional<ActiveOrder> findById(UUID orderId) {
        return Optional.ofNullable(activeOrders.get(orderId));
    }

    @Override
    public List<ActiveOrder> findAllActive() {
        return activeOrders.values().stream()
                .filter(ActiveOrder::isActive)
                .collect(Collectors.toList());
    }

    @Override
    public List<ActiveOrder> findActiveByEventId(UUID eventId) {
        if (eventId == null) return List.of();
        return activeOrders.values().stream()
                .filter(o -> o.isActive() && eventId.equals(o.getEventId()))
                .collect(Collectors.toList());
    }

    @Override
    public void save(CompletedPurchase purchase) {
        if (purchase == null) throw new IllegalArgumentException("purchase cannot be null");
        completedPurchases.put(purchase.purchaseId(), purchase);
    }

    @Override
    public Optional<CompletedPurchase> findCompletedById(UUID purchaseId) {
        return Optional.ofNullable(completedPurchases.get(purchaseId));
    }

    @Override
    public List<CompletedPurchase> findCompletedByCompanyName(String companyName) {
        if (companyName == null) return List.of();
        List<CompletedPurchase> hits = new ArrayList<>();
        for (CompletedPurchase p : completedPurchases.values()) {
            if (companyName.equals(p.companyName())) hits.add(p);
        }
        return hits;
    }

    @Override
    public List<CompletedPurchase> findCompletedByEventId(UUID eventId) {
        if (eventId == null) return List.of();
        List<CompletedPurchase> hits = new ArrayList<>();
        for (CompletedPurchase p : completedPurchases.values()) {
            if (eventId.equals(p.eventId())) hits.add(p);
        }
        return hits;
    }

    @Override
    public List<CompletedPurchase> findCompletedByMemberId(UUID memberId) {
        if (memberId == null) return List.of();
        List<CompletedPurchase> hits = new ArrayList<>();
        for (CompletedPurchase p : completedPurchases.values()) {
            if (memberId.equals(p.memberId())) hits.add(p);
        }
        return hits;
    }

    @Override
    public List<CompletedPurchase> findAllCompleted() {
        return new ArrayList<>(completedPurchases.values());
    }

}

