package com.ticketing.domain.order;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for active (in-flight) orders.
 * Completed purchases are stored separately in ICompletedPurchaseRepository.
 */
public interface IOrderRepository {
    Optional<ActiveOrder> findActiveBySessionId(UUID sessionId);

    void save(ActiveOrder order);

    Optional<ActiveOrder> findById(UUID orderId);

    List<ActiveOrder> findAllActive();

    List<ActiveOrder> findActiveByEventId(UUID eventId);
}
