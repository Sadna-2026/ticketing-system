package com.ticketing.infrastructure.persistence;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ticketing.domain.order.ActiveOrder;

/**
 * Spring Data JPA repository for the {@link ActiveOrder} aggregate root.
 * Provides the derived queries the {@link JpaOrderRepository} adapter needs for
 * the active-order half of the {@code IOrderRepository} contract. Only
 * instantiated when the JPA persistence profile is active (see
 * {@link JpaOrderRepository}).
 */
public interface ActiveOrderJpaRepository extends JpaRepository<ActiveOrder, UUID> {

    List<ActiveOrder> findByEventId(UUID eventId);

    List<ActiveOrder> findBySessionId(UUID sessionId);

    List<ActiveOrder> findByMemberId(UUID memberId);

    List<ActiveOrder> findByMemberIdAndEventId(UUID memberId, UUID eventId);
}
