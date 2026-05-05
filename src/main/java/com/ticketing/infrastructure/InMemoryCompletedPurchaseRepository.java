package com.ticketing.infrastructure;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.ticketing.domain.order.CompletedPurchase;
import com.ticketing.domain.order.ICompletedPurchaseRepository;

public class InMemoryCompletedPurchaseRepository implements ICompletedPurchaseRepository {

    private final ConcurrentHashMap<UUID, CompletedPurchase> store = new ConcurrentHashMap<>();

    @Override
    public void save(CompletedPurchase purchase) {
        if (purchase == null) throw new IllegalArgumentException("purchase cannot be null");
        store.put(purchase.purchaseId(), purchase);
    }

    @Override
    public List<CompletedPurchase> findByCompanyName(String companyName) {
        if (companyName == null) return List.of();
        List<CompletedPurchase> hits = new ArrayList<>();
        for (CompletedPurchase p : store.values()) {
            if (companyName.equals(p.companyName())) hits.add(p);
        }
        return hits;
    }

    @Override
    public List<CompletedPurchase> findByEventId(UUID eventId) {
        if (eventId == null) return List.of();
        List<CompletedPurchase> hits = new ArrayList<>();
        for (CompletedPurchase p : store.values()) {
            if (eventId.equals(p.eventId())) hits.add(p);
        }
        return hits;
    }
}
