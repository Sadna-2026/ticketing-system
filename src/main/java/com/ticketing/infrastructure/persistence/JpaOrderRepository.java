package com.ticketing.infrastructure.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.ticketing.domain.exception.OptimisticLockException;
import com.ticketing.domain.order.ActiveOrder;
import com.ticketing.domain.order.CompletedPurchase;
import com.ticketing.domain.order.IOrderRepository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * JPA-backed implementation of {@link IOrderRepository}, delegating to two Spring
 * Data repositories: {@link ActiveOrderJpaRepository} for the in-flight
 * {@link ActiveOrder} aggregate and {@link CompletedPurchaseJpaRepository} for the
 * frozen {@link CompletedPurchase} snapshot. Activated when
 * {@code ticketing.persistence=jpa}; the default ({@code memory}) keeps
 * {@code InMemoryOrderRepository}. Exactly one of the two beans is ever active, so
 * callers stay unchanged.
 *
 * <p>Semantics mirror {@code InMemoryOrderRepository}:
 * <ul>
 *   <li>The session/member/event/findAll active-order queries return only
 *       {@link ActiveOrder#isActive() ACTIVE} orders (the same {@code isActive()}
 *       filter the in-memory repo applied); {@code findById} returns the order
 *       regardless of status.</li>
 *   <li>{@code save(ActiveOrder)} uses the {@code @Version} field for optimistic
 *       locking, re-throwing the domain {@link OptimisticLockException} on conflict
 *       and incrementing the version to 1 on first store (mirroring the in-memory
 *       CAS). The order owns its items (cascade ALL), so a single write persists the
 *       whole aggregate.</li>
 *   <li>{@code save(CompletedPurchase)} is an unconditional upsert (last-write-wins),
 *       matching the in-memory {@code Map.put}: snapshots are immutable and carry no
 *       version.</li>
 * </ul>
 */
@Repository
@ConditionalOnProperty(name = "ticketing.persistence", havingValue = "jpa")
public class JpaOrderRepository implements IOrderRepository {

    private final ActiveOrderJpaRepository activeOrders;
    private final CompletedPurchaseJpaRepository completedPurchases;

    @PersistenceContext
    private EntityManager entityManager;

    public JpaOrderRepository(ActiveOrderJpaRepository activeOrders,
                              CompletedPurchaseJpaRepository completedPurchases) {
        this.activeOrders = activeOrders;
        this.completedPurchases = completedPurchases;
    }

    // ── ActiveOrder ──────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public Optional<ActiveOrder> findActiveBySessionId(UUID sessionId) {
        if (sessionId == null) {
            return Optional.empty();
        }
        return activeOrders.findBySessionId(sessionId).stream()
                .filter(ActiveOrder::isActive)
                .findFirst()
                .map(this::detach);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ActiveOrder> findActiveByMemberId(UUID memberId) {
        if (memberId == null) {
            return Optional.empty();
        }
        return activeOrders.findByMemberId(memberId).stream()
                .filter(ActiveOrder::isActive)
                .findFirst()
                .map(this::detach);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ActiveOrder> findById(UUID orderId) {
        if (orderId == null) {
            return Optional.empty();
        }
        return activeOrders.findById(orderId).map(this::detach);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ActiveOrder> findAllActive() {
        return activeOrders.findAll().stream()
                .filter(ActiveOrder::isActive)
                .map(this::detach)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<ActiveOrder> findActiveByEventId(UUID eventId) {
        if (eventId == null) {
            return List.of();
        }
        return activeOrders.findByEventId(eventId).stream()
                .filter(ActiveOrder::isActive)
                .map(this::detach)
                .collect(Collectors.toList());
    }

    /**
     * Initialises the LAZY item collection and detaches the order from the persistence
     * context, so callers receive an independent snapshot (matching the in-memory repo,
     * which returned the stored aggregate). Detaching also lets two reads in the same
     * transaction yield independent snapshots, preserving optimistic-lock semantics.
     */
    private ActiveOrder detach(ActiveOrder order) {
        order.getItems().size();
        entityManager.detach(order);
        return order;
    }

    @Override
    @Transactional
    public void save(ActiveOrder order) {
        if (order == null) {
            throw new IllegalArgumentException("order cannot be null");
        }
        boolean isNew = !activeOrders.existsById(order.getId());
        try {
            if (isNew) {
                order.incrementVersion();
                entityManager.persist(order);
            } else {
                entityManager.merge(order);
            }
            entityManager.flush();
        } catch (ObjectOptimisticLockingFailureException
                 | jakarta.persistence.OptimisticLockException ex) {
            throw new OptimisticLockException("ActiveOrder", order.getId());
        }
    }

    // ── CompletedPurchase ────────────────────────────────────────────────────

    @Override
    @Transactional
    public void save(CompletedPurchase purchase) {
        if (purchase == null) {
            throw new IllegalArgumentException("purchase cannot be null");
        }
        // Unconditional upsert (last-write-wins), mirroring the in-memory Map.put.
        // Snapshots are immutable and unversioned, so there is no conflict to detect.
        entityManager.merge(purchase);
        entityManager.flush();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<CompletedPurchase> findCompletedById(UUID purchaseId) {
        if (purchaseId == null) {
            return Optional.empty();
        }
        return completedPurchases.findById(purchaseId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CompletedPurchase> findCompletedByCompanyName(String companyName) {
        if (companyName == null) {
            return List.of();
        }
        return completedPurchases.findByCompanyName(companyName);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CompletedPurchase> findCompletedByEventId(UUID eventId) {
        if (eventId == null) {
            return List.of();
        }
        return completedPurchases.findByEventId(eventId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CompletedPurchase> findCompletedByMemberId(UUID memberId) {
        if (memberId == null) {
            return List.of();
        }
        return completedPurchases.findByMemberId(memberId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CompletedPurchase> findAllCompleted() {
        return completedPurchases.findAll();
    }
}
