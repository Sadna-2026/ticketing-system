package com.ticketing.infrastructure.persistence;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.ticketing.domain.company.Company;
import com.ticketing.domain.company.CompanyStatus;
import com.ticketing.domain.company.ICompanyRepository;
import com.ticketing.domain.exception.OptimisticLockException;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * JPA-backed implementation of {@link ICompanyRepository}, delegating to
 * {@link CompanyJpaRepository}. Activated when {@code ticketing.persistence=jpa};
 * the default ({@code memory}) keeps {@code InMemoryCompanyRepository}. Exactly
 * one of the two beans is ever active, so callers stay unchanged.
 *
 * <p>Semantics mirror {@code InMemoryCompanyRepository}:
 * <ul>
 *   <li>The company name is the {@code @Id}; keys are normalized (lower-cased +
 *       trimmed) for lookups, matching the in-memory index.</li>
 *   <li>Reads return {@link Company#detachedCopy() detached copies}.</li>
 *   <li>{@code save} acts as both create-if-name-free and optimistic-locked
 *       update: a fresh (version 0) company whose name already exists is rejected
 *       with {@code IllegalArgumentException}; a stale update is rejected with the
 *       domain {@link OptimisticLockException}.</li>
 *   <li>{@code delete} is unsupported, as in the in-memory repo.</li>
 * </ul>
 *
 * <p>Behavioral note: the in-memory repo keyed companies by the normalized name
 * but stored the raw name on the entity; here the entity's {@code @Id} is the raw
 * name, while existence/lookup queries normalize. Callers create companies with
 * already-consistent names, so this matches in practice.
 */
@Repository
@ConditionalOnProperty(name = "ticketing.persistence", havingValue = "jpa")
public class JpaCompanyRepository implements ICompanyRepository {

    private final CompanyJpaRepository delegate;

    @PersistenceContext
    private EntityManager entityManager;

    public JpaCompanyRepository(CompanyJpaRepository delegate) {
        this.delegate = delegate;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Company> findById(String id) {
        if (id == null) {
            return Optional.empty();
        }
        return findByNormalizedKey(normalizeKey(id));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Company> findByName(String name) {
        if (name == null) {
            return Optional.empty();
        }
        return findByNormalizedKey(normalizeKey(name));
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsByName(String name) {
        return name != null && findByNormalizedKey(normalizeKey(name)).isPresent();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Company> getAll() {
        List<Company> all = new ArrayList<>();
        for (Company c : delegate.findAll()) {
            all.add(c.detachedCopy());
        }
        return all;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Company> findActiveCompanies(String query) {
        String needle = queryNeedle(query);
        return delegate.findAll().stream()
                .filter(Company::isActive)
                .filter(c -> matchesName(c, needle))
                .sorted(Comparator.comparing(Company::getName, String.CASE_INSENSITIVE_ORDER))
                .map(Company::detachedCopy)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Company> findLookupVisibleCompanies(UUID memberId, boolean systemAdmin, String query) {
        String needle = queryNeedle(query);
        return delegate.findAll().stream()
                .filter(c -> c.isActive() || canViewSuspended(c, memberId, systemAdmin))
                .filter(c -> matchesName(c, needle))
                .sorted(Comparator.comparing(Company::getName, String.CASE_INSENSITIVE_ORDER))
                .map(Company::detachedCopy)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Company> findFounderLifecycleCompanies(UUID founderId, String query) {
        String needle = queryNeedle(query);
        return delegate.findAll().stream()
                .filter(c -> founderId.equals(c.getFounderId()))
                .filter(c -> c.getStatus() == CompanyStatus.ACTIVE || c.getStatus() == CompanyStatus.SUSPENDED)
                .filter(c -> matchesName(c, needle))
                .sorted(Comparator.comparing(Company::getName, String.CASE_INSENSITIVE_ORDER))
                .map(Company::detachedCopy)
                .toList();
    }

    @Override
    @Transactional
    public void save(Company company) {
        if (company == null) {
            throw new IllegalArgumentException("company cannot be null");
        }
        Optional<Company> existing = findByNormalizedKey(normalizeKey(company.getName()));
        if (existing.isEmpty()) {
            // New aggregate: increment version to 1 on first store, mirroring the
            // in-memory repo so a re-read company never reports version 0 (which the
            // contract reserves as the "fresh, never-persisted" sentinel).
            company.incrementVersion();
            entityManager.persist(company);
            entityManager.flush();
            return;
        }
        // Name already taken. A version-0 company is a fresh, never-persisted object
        // (see save-of-new above), so this is a duplicate-name create attempt.
        if (company.getVersion() == 0) {
            throw new IllegalArgumentException(
                    "A production company with this name already exists.");
        }
        // Otherwise it is an update: let the JPA @Version guard detect stale writes
        // and map the failure to the domain OptimisticLockException.
        try {
            entityManager.merge(company);
            entityManager.flush();
        } catch (ObjectOptimisticLockingFailureException
                 | jakarta.persistence.OptimisticLockException ex) {
            throw new OptimisticLockException("Company", company.getName());
        }
    }

    @Override
    public void delete(String id) {
        throw new UnsupportedOperationException("Delete not supported");
    }

    /**
     * Resolves a company by its normalized key. The {@code @Id} is the raw name,
     * so an exact-id lookup is tried first and, on miss, a scan compares
     * normalized names to preserve the in-memory case-insensitive semantics.
     */
    private Optional<Company> findByNormalizedKey(String normalizedKey) {
        Optional<Company> direct = delegate.findById(normalizedKey);
        if (direct.isPresent()) {
            return direct.map(Company::detachedCopy);
        }
        for (Company c : delegate.findAll()) {
            if (normalizeKey(c.getName()).equals(normalizedKey)) {
                return Optional.of(c.detachedCopy());
            }
        }
        return Optional.empty();
    }

    private static String normalizeKey(String name) {
        return name.toLowerCase().trim();
    }

    private static String queryNeedle(String query) {
        return query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
    }

    private static boolean matchesName(Company company, String needle) {
        return needle.isEmpty() || company.getName().toLowerCase(Locale.ROOT).contains(needle);
    }

    private static boolean canViewSuspended(Company company, UUID memberId, boolean systemAdmin) {
        return company.getStatus() == CompanyStatus.SUSPENDED
                && (systemAdmin || memberId != null && memberId.equals(company.getFounderId()));
    }
}
