package com.ticketing.domain.order;

import java.util.List;
import java.util.UUID;

public interface ICompletedPurchaseRepository {

    void save(CompletedPurchase purchase);

    List<CompletedPurchase> findByCompanyName(String companyName);

    List<CompletedPurchase> findByEventId(UUID eventId);
}
