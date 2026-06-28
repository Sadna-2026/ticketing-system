package com.ticketing.infrastructure.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.ticketing.domain.event.AbstractDiscountPolicy;
import com.ticketing.domain.event.AbstractPurchasePolicy;
import com.ticketing.domain.event.AndPolicy;
import com.ticketing.domain.event.ConditionalDiscount;
import com.ticketing.domain.event.Event;
import com.ticketing.domain.event.IEventRepository;
import com.ticketing.domain.event.MaxCompositeDiscount;
import com.ticketing.domain.event.OrPolicy;
import com.ticketing.domain.event.SumCompositeDiscount;
import com.ticketing.domain.event.VenueLayout;
import com.ticketing.domain.event.VenueMap;
import com.ticketing.domain.exception.OptimisticLockException;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * JPA-backed implementation of {@link IEventRepository}, delegating persistence to
 * {@link EventJpaRepository}. Activated when {@code ticketing.persistence=jpa}; the
 * default ({@code memory}) keeps {@code InMemoryEventRepository}. Exactly one of the
 * two beans is ever active, so callers stay unchanged.
 *
 * <p>Semantics mirror {@code InMemoryEventRepository}:
 * <ul>
 *   <li>{@code save} relies on the JPA {@code @Version} field for optimistic locking
 *       and re-throws the domain {@link OptimisticLockException} on a version conflict
 *       (the in-memory repo did a manual CAS on the version counter).</li>
 *   <li>On first store the version is incremented to 1 (as in the in-memory repo) so a
 *       re-read event reports the same version number as before.</li>
 *   <li>The Event owns its zones → seats (cascade ALL on the entity), so a single
 *       {@code persist}/{@code merge} writes the whole aggregate graph.</li>
 *   <li>Reads run in a read-only transaction; the {@code @DataJpaTest}/service
 *       transaction keeps the LAZY seat collection traversable, matching the in-memory
 *       repo which returned the live aggregate.</li>
 * </ul>
 */
@Repository
@ConditionalOnProperty(name = "ticketing.persistence", havingValue = "jpa")
public class JpaEventRepository implements IEventRepository {

    private final EventJpaRepository delegate;

    @PersistenceContext
    private EntityManager entityManager;

    public JpaEventRepository(EventJpaRepository delegate) {
        this.delegate = delegate;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Event> findById(UUID eventId) {
        if (eventId == null) {
            return Optional.empty();
        }
        return delegate.findById(eventId).map(this::detach);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Event> findByCompanyName(String companyName) {
        if (companyName == null) {
            return List.of();
        }
        return delegate.findByCompanyName(companyName).stream().map(this::detach).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Event> findAll() {
        return delegate.findAll().stream().map(this::detach).toList();
    }

    @Override
    @Transactional
    public void deleteAll() {
        delegate.deleteAll();
    }

    /**
     * Initialises the LAZY zone/seat graph and detaches the aggregate from the
     * persistence context, so callers receive an independent snapshot (matching the
     * in-memory repo, which returned the stored aggregate, and the {@code detachedCopy()}
     * contract used by the member/company adapters). Detaching also lets two reads in the
     * same transaction yield independent snapshots, preserving optimistic-lock semantics.
     */
    private Event detach(Event event) {
        // Touch the LAZY collections before detaching so they remain traversable.
        event.getZones().forEach(z -> z.getSeats().size());
        // The venue layout is an @Embeddable whose cells are a separate LAZY
        // @ElementCollection; initialise it too, otherwise hasSellableCell()/getCells()
        // throws a LazyInitializationException on the detached aggregate (e.g. the
        // venue-designer "Validate" action via EventService.validateEventLayout).
        VenueLayout layout = event.getVenueLayout();
        if (layout != null) {
            layout.getCells().size();
        }
        VenueMap venueMap = event.getVenueMap();
        if (venueMap != null) {
            venueMap.getSectionToZone().size();
        }
        if (event.getPurchasePolicy() instanceof AbstractPurchasePolicy purchase) {
            touchPurchasePolicy(purchase);
        }
        if (event.getDiscountPolicy() instanceof AbstractDiscountPolicy discount) {
            touchDiscountPolicy(discount);
        }
        entityManager.detach(event);
        return event;
    }

    private static void touchPurchasePolicy(AbstractPurchasePolicy policy) {
        if (policy instanceof AndPolicy and) {
            and.getPolicies().forEach(p -> {
                if (p instanceof AbstractPurchasePolicy child) {
                    touchPurchasePolicy(child);
                }
            });
        } else if (policy instanceof OrPolicy or) {
            or.getPolicies().forEach(p -> {
                if (p instanceof AbstractPurchasePolicy child) {
                    touchPurchasePolicy(child);
                }
            });
        }
    }

    private static void touchDiscountPolicy(AbstractDiscountPolicy policy) {
        if (policy instanceof SumCompositeDiscount sum) {
            sum.getPolicies().forEach(p -> {
                if (p instanceof AbstractDiscountPolicy child) {
                    touchDiscountPolicy(child);
                }
            });
        } else if (policy instanceof MaxCompositeDiscount max) {
            max.getPolicies().forEach(p -> {
                if (p instanceof AbstractDiscountPolicy child) {
                    touchDiscountPolicy(child);
                }
            });
        } else if (policy instanceof ConditionalDiscount conditional) {
            if (conditional.getCondition() != null) {
                conditional.getCondition().getClass();
            }
        }
    }

    /**
     * Purchase/discount policies are shared rows (also referenced from companies). After a
     * detached read, {@code CascadeType.ALL} on the {@code @ManyToOne} would otherwise try
     * to {@code persist} an already-persisted policy on flush. Swap in a managed reference.
     */
    private void reattachSharedPolicyReferences(Event event) {
        if (event.getPurchasePolicy() instanceof AbstractPurchasePolicy purchase
                && purchase.getId() != null) {
            event.setPurchasePolicy(
                    entityManager.getReference(AbstractPurchasePolicy.class, purchase.getId()));
        }
        if (event.getDiscountPolicy() instanceof AbstractDiscountPolicy discount
                && discount.getId() != null) {
            event.setDiscountPolicy(
                    entityManager.getReference(AbstractDiscountPolicy.class, discount.getId()));
        }
    }

    @Override
    @Transactional
    public void save(Event event) {
        if (event == null) {
            throw new IllegalArgumentException("event cannot be null");
        }
        reattachSharedPolicyReferences(event);
        boolean isNew = !delegate.existsById(event.getId());
        try {
            if (isNew) {
                // New aggregate: increment version to 1 on first store, mirroring the
                // in-memory repo so a re-read event never reports version 0.
                event.incrementVersion();
                entityManager.persist(event);
                entityManager.flush();
            } else {
                // merge() returns a MANAGED copy carrying the post-flush versions; the
                // caller's detached `event` is left stale. Copy the new versions back so
                // a second save of the SAME aggregate in the SAME transaction (V3-11 #269
                // added @Version to InventoryZone/Seat, so e.g. reserve-then-sold-out now
                // saves a versioned child twice) is not mistaken for a concurrent edit. A
                // genuinely concurrent transaction holds a DIFFERENT detached snapshot
                // that never gets this sync, so its stale child version still conflicts.
                Event managed = entityManager.merge(event);
                entityManager.flush();
                event.syncVersionsFrom(managed);
            }
        } catch (ObjectOptimisticLockingFailureException
                 | jakarta.persistence.OptimisticLockException ex) {
            throw new OptimisticLockException("Event", event.getId());
        }
    }
}
