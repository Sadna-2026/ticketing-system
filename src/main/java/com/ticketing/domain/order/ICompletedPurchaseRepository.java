package com.ticketing.domain.order;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for completed (paid) purchases.
 * Separated from IOrderRepository (active orders) per the domain model UML.
 */
public interface ICompletedPurchaseRepository {

    void save(CompletedPurchase purchase);

    Optional<CompletedPurchase> findById(UUID purchaseId);

    List<CompletedPurchase> findByCompanyName(String companyName);

    List<CompletedPurchase> findByEventId(UUID eventId);

    List<CompletedPurchase> findByMemberId(UUID memberId);

    List<CompletedPurchase> findAll();
}
