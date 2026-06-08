package com.ticketing.infrastructure.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.ticketing.domain.event.Event;
import com.ticketing.domain.event.IEventRepository;
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
        entityManager.detach(event);
        return event;
    }

    @Override
    @Transactional
    public void save(Event event) {
        if (event == null) {
            throw new IllegalArgumentException("event cannot be null");
        }
        boolean isNew = !delegate.existsById(event.getId());
        try {
            if (isNew) {
                // New aggregate: increment version to 1 on first store, mirroring the
                // in-memory repo so a re-read event never reports version 0.
                event.incrementVersion();
                entityManager.persist(event);
            } else {
                entityManager.merge(event);
            }
            entityManager.flush();
        } catch (ObjectOptimisticLockingFailureException
                 | jakarta.persistence.OptimisticLockException ex) {
            throw new OptimisticLockException("Event", event.getId());
        }
    }
}
