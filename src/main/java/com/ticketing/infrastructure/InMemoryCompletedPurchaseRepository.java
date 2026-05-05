package com.ticketing.infrastructure;

import java.util.List;
import java.util.UUID;

import com.ticketing.domain.order.CompletedPurchase;
import com.ticketing.domain.order.ICompletedPurchaseRepository;

public class InMemoryCompletedPurchaseRepository extends InMemoryOrderRepository implements ICompletedPurchaseRepository {

    @Override
    public void save(CompletedPurchase purchase) {
        super.save(purchase);
    }

    @Override
    public List<CompletedPurchase> findByCompanyName(String companyName) {
        return findCompletedByCompanyName(companyName);
    }

    @Override
    public List<CompletedPurchase> findByEventId(UUID eventId) {
        return findCompletedByEventId(eventId);
    }
}
