package com.ticketing.domain.order;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface IOrderRepository {
    Optional<ActiveOrder> findActiveBySessionId(UUID sessionId);

    void save(ActiveOrder order);

    Optional<ActiveOrder> findById(UUID orderId);

    List<ActiveOrder> findAllActive();

    List<ActiveOrder> findActiveByEventId(UUID eventId);

    void save(CompletedPurchase purchase);

    Optional<CompletedPurchase> findCompletedById(UUID purchaseId);

    List<CompletedPurchase> findCompletedByCompanyName(String companyName);

    List<CompletedPurchase> findCompletedByEventId(UUID eventId);
}
