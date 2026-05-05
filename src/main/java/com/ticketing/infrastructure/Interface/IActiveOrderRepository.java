package com.ticketing.infrastructure.Interface;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.ticketing.domain.order.ActiveOrder;

public interface IActiveOrderRepository {
    Optional<ActiveOrder> findActiveBySessionId(UUID sessionId);

    void save(ActiveOrder order);

    Optional<ActiveOrder> findById(UUID orderId);

    List<ActiveOrder> findAllActive();

    List<ActiveOrder> findActiveByEventId(UUID eventId);
}