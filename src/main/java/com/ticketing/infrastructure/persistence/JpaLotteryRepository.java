package com.ticketing.infrastructure.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Lazy;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.ticketing.domain.exception.OptimisticLockException;
import com.ticketing.domain.lottery.ILotteryRepository;
import com.ticketing.domain.lottery.LotteryEntry;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * JPA-backed implementation of {@link ILotteryRepository}, delegating to
 * {@link LotteryJpaRepository}. Activated when {@code ticketing.persistence=jpa};
 * the default ({@code memory}) keeps {@code InMemoryLotteryRepository}. Exactly one
 * of the two beans is ever active, so callers stay unchanged.
 *
 * <p>Semantics mirror {@code InMemoryLotteryRepository}:
 * <ul>
 *   <li>{@code save} relies on the JPA {@code @Version} field for optimistic locking
 *       and re-throws the domain {@link OptimisticLockException} on a version conflict
 *       (the in-memory repo did a manual CAS on the version counter).</li>
 *   <li>On first store the entry is persisted with the version incremented to 1 via
 *       {@link LotteryEntry#incrementedVersionCopy()} (mirroring the in-memory repo),
 *       so a re-read entry reports the same version number as before. {@code LotteryEntry}
 *       is immutable, so updates re-{@code merge} a fresh copy under the version guard.</li>
 *   <li>{@code findByEventAndMember} returns the first matching entry, as in-memory.</li>
 * </ul>
 */
@Repository
@Lazy
@ConditionalOnProperty(name = "ticketing.persistence", havingValue = "jpa")
public class JpaLotteryRepository implements ILotteryRepository {

    private final LotteryJpaRepository delegate;

    @PersistenceContext
    private EntityManager entityManager;

    public JpaLotteryRepository(LotteryJpaRepository delegate) {
        this.delegate = delegate;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<LotteryEntry> findById(UUID id) {
        if (id == null) {
            return Optional.empty();
        }
        return delegate.findById(id);
    }

    @Override
    @Transactional(readOnly = true)
    public List<LotteryEntry> findAll() {
        return delegate.findAll();
    }

    @Override
    @Transactional(readOnly = true)
    public List<LotteryEntry> findByEventId(UUID eventId) {
        if (eventId == null) {
            return List.of();
        }
        return delegate.findByEventId(eventId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<LotteryEntry> findByMemberId(UUID memberId) {
        if (memberId == null) {
            return List.of();
        }
        return delegate.findByMemberId(memberId);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<LotteryEntry> findByEventAndMember(UUID eventId, UUID memberId) {
        if (eventId == null || memberId == null) {
            return Optional.empty();
        }
        return delegate.findByEventIdAndMemberId(eventId, memberId);
    }

    @Override
    @Transactional
    public void save(LotteryEntry entry) {
        if (entry == null) {
            throw new IllegalArgumentException("entry is required");
        }
        boolean isNew = !delegate.existsById(entry.id());
        try {
            if (isNew) {
                // New aggregate: store at version 1 (mirroring the in-memory repo, which
                // bumped 0 → 1 on first save) so a re-read entry never reports version 0.
                entityManager.persist(entry.incrementedVersionCopy());
            } else {
                // LotteryEntry is immutable; merge a fresh copy under the @Version guard.
                entityManager.merge(entry);
            }
            entityManager.flush();
        } catch (ObjectOptimisticLockingFailureException
                 | jakarta.persistence.OptimisticLockException ex) {
            throw new OptimisticLockException("LotteryEntry", entry.id());
        }
    }

    @Override
    @Transactional
    public void delete(UUID id) {
        if (id == null) {
            return;
        }
        delegate.deleteById(id);
    }

    @Override
    @Transactional
    public void deleteAll() {
        delegate.deleteAll();
    }
}
