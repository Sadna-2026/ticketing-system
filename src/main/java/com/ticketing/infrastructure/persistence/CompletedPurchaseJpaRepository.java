package com.ticketing.infrastructure.persistence;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ticketing.domain.order.CompletedPurchase;

/**
 * Spring Data JPA repository for the {@link CompletedPurchase} snapshot.
 * Provides the derived queries the {@link JpaOrderRepository} adapter needs for
 * the completed-purchase half of the {@code IOrderRepository} contract. Only
 * instantiated when the JPA persistence profile is active (see
 * {@link JpaOrderRepository}).
 */
public interface CompletedPurchaseJpaRepository extends JpaRepository<CompletedPurchase, UUID> {

    List<CompletedPurchase> findByCompanyName(String companyName);

    List<CompletedPurchase> findByEventId(UUID eventId);

    List<CompletedPurchase> findByMemberId(UUID memberId);
}
