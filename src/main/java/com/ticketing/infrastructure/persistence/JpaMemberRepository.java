package com.ticketing.infrastructure.persistence;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.ticketing.domain.exception.OptimisticLockException;
import com.ticketing.domain.member.IMemberRepository;
import com.ticketing.domain.member.Member;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * JPA-backed implementation of {@link IMemberRepository}, delegating persistence
 * to {@link MemberJpaRepository}. Activated when {@code ticketing.persistence=jpa};
 * the default ({@code memory}) keeps {@code InMemoryMemberRepository}. Exactly one
 * of the two beans is ever active, so callers stay unchanged.
 *
 * <p>Semantics mirror {@code InMemoryMemberRepository}:
 * <ul>
 *   <li>Reads return {@link Member#detachedCopy() detached copies} so callers may
 *       mutate results without touching persisted state (matching the in-memory
 *       contract).</li>
 *   <li>Username/email lookups normalize the key (username trimmed, email
 *       trimmed + lower-cased) before querying.</li>
 *   <li>{@code save} relies on the JPA {@code @Version} field for optimistic
 *       locking and re-throws the domain {@link OptimisticLockException} on a
 *       version conflict so the interface contract is preserved.</li>
 *   <li>Uniqueness methods check existence and persist within a single
 *       transactional method.</li>
 *   <li>On first store the version is incremented to 1 (as in the in-memory
 *       repo) so a re-read member reports the same version number as before.</li>
 * </ul>
 */
@Repository
@ConditionalOnProperty(name = "ticketing.persistence", havingValue = "jpa")
public class JpaMemberRepository implements IMemberRepository {

    private final MemberJpaRepository delegate;

    @PersistenceContext
    private EntityManager entityManager;

    public JpaMemberRepository(MemberJpaRepository delegate) {
        this.delegate = delegate;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Member> findById(UUID memberId) {
        if (memberId == null) {
            return Optional.empty();
        }
        return delegate.findById(memberId).map(Member::detachedCopy);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Member> findByUsername(String username) {
        if (username == null) {
            return Optional.empty();
        }
        return delegate.findByUsername(normalizeUsername(username)).map(Member::detachedCopy);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Member> findByEmail(String email) {
        if (email == null) {
            return Optional.empty();
        }
        return delegate.findByEmail(normalizeEmail(email)).map(Member::detachedCopy);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsByUsername(String username) {
        return username != null && delegate.existsByUsername(normalizeUsername(username));
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsByEmail(String email) {
        return email != null && delegate.existsByEmail(normalizeEmail(email));
    }

    @Override
    @Transactional(readOnly = true)
    public long count() {
        return delegate.count();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Member> findByCompanyAppointment(String companyName) {
        if (companyName == null) {
            return List.of();
        }
        List<Member> hits = new ArrayList<>();
        for (Member m : delegate.findByCompanyAppointmentKey(companyName)) {
            hits.add(m.detachedCopy());
        }
        return hits;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Member> findAll() {
        List<Member> result = new ArrayList<>();
        for (Member m : delegate.findAll()) {
            result.add(m.detachedCopy());
        }
        return result;
    }

    @Override
    @Transactional
    public boolean saveIfUsernameAndEmailAvailable(Member member) {
        if (member == null) {
            throw new IllegalArgumentException("member cannot be null");
        }
        String username = normalizeUsername(member.getUsername());
        String email = normalizeEmail(member.getEmail());

        if (delegate.existsByUsername(username) || delegate.existsByEmail(email)) {
            return false;
        }
        persist(member);
        return true;
    }

    @Override
    @Transactional
    public boolean updateIfUsernameAndEmailAvailable(Member member, String username, String email) {
        if (member == null) {
            throw new IllegalArgumentException("member cannot be null");
        }
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("username cannot be null or blank");
        }
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("email cannot be null or blank");
        }

        String newUsername = normalizeUsername(username);
        String newEmail = normalizeEmail(email);

        Optional<Member> usernameOwner = delegate.findByUsername(newUsername);
        Optional<Member> emailOwner = delegate.findByEmail(newEmail);

        if (usernameOwner.isPresent() && !usernameOwner.get().getId().equals(member.getId())) {
            return false;
        }
        if (emailOwner.isPresent() && !emailOwner.get().getId().equals(member.getId())) {
            return false;
        }

        member.updateUsername(newUsername);
        member.updateEmail(newEmail);
        persist(member);
        return true;
    }

    @Override
    @Transactional
    public void save(Member member) {
        if (member == null) {
            throw new IllegalArgumentException("member cannot be null");
        }
        persist(member);
    }

    @Override
    @Transactional
    public void delete(Member member) {
        if (member == null) {
            return;
        }
        delegate.findById(member.getId()).ifPresent(delegate::delete);
    }

    /**
     * Persists the entity, surfacing version conflicts synchronously via an
     * explicit flush and translating JPA/Spring optimistic-locking failures into
     * the domain {@link OptimisticLockException}. New members are inserted with
     * the version incremented to 1 (mirroring the in-memory repo); existing ones
     * are merged under the {@code @Version} guard.
     */
    private void persist(Member member) {
        boolean isNew = !delegate.existsById(member.getId());
        try {
            if (isNew) {
                member.incrementVersion();
                entityManager.persist(member);
            } else {
                entityManager.merge(member);
            }
            entityManager.flush();
        } catch (ObjectOptimisticLockingFailureException
                 | jakarta.persistence.OptimisticLockException ex) {
            throw new OptimisticLockException("Member", member.getId());
        }
    }

    private String normalizeUsername(String username) {
        return username.trim();
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase();
    }
}
