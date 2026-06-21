package com.ticketing.infrastructure;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import com.ticketing.domain.company.Company;
import com.ticketing.domain.company.CompanyStatus;
import com.ticketing.domain.company.ICompanyRepository;
import com.ticketing.domain.exception.OptimisticLockException;

/**
 * In-memory implementation of ICompanyRepository with CAS-style optimistic locking.
 * Uses the aggregate version counter to detect concurrent modifications.
 * Stored aggregates are detached from callers: reads return copies and saves persist copies.
 */
@org.springframework.stereotype.Component
@ConditionalOnProperty(name = "ticketing.persistence", havingValue = "memory", matchIfMissing = true)
public class InMemoryCompanyRepository implements ICompanyRepository {

    private final ConcurrentHashMap<String, Company> companies;

    public InMemoryCompanyRepository() {
        this.companies = new ConcurrentHashMap<>();
    }

    @Override
    public Optional<Company> findById(String id) {
        Company company = companies.get(normalizeKey(id));
        return company != null ? Optional.of(company.detachedCopy()) : Optional.empty();
    }

    public boolean existsById(String id) {
        return companies.containsKey(normalizeKey(id));
    }

    @Override
    public boolean existsByName(String name) {
        return companies.containsKey(normalizeKey(name));
    }

    @Override
    public Optional<Company> findByName(String name) {
        Company company = companies.get(normalizeKey(name));
        return company != null ? Optional.of(company.detachedCopy()) : Optional.empty();
    }

    @Override
    public List<Company> getAll() {
        List<Company> all = new ArrayList<>(companies.size());
        for (Company company : companies.values()) {
            all.add(company.detachedCopy());
        }
        return all;
    }

    @Override
    public List<Company> findActiveCompanies(String query) {
        String needle = queryNeedle(query);
        return companies.values().stream()
                .filter(Company::isActive)
                .filter(c -> matchesName(c, needle))
                .sorted(Comparator.comparing(Company::getName, String.CASE_INSENSITIVE_ORDER))
                .map(Company::detachedCopy)
                .toList();
    }

    @Override
    public List<Company> findLookupVisibleCompanies(UUID memberId, boolean systemAdmin, String query) {
        String needle = queryNeedle(query);
        return companies.values().stream()
                .filter(c -> c.isActive() || canViewSuspended(c, memberId, systemAdmin))
                .filter(c -> matchesName(c, needle))
                .sorted(Comparator.comparing(Company::getName, String.CASE_INSENSITIVE_ORDER))
                .map(Company::detachedCopy)
                .toList();
    }

    @Override
    public List<Company> findFounderLifecycleCompanies(UUID founderId, String query) {
        String needle = queryNeedle(query);
        return companies.values().stream()
                .filter(c -> founderId.equals(c.getFounderId()))
                .filter(c -> c.getStatus() == CompanyStatus.ACTIVE || c.getStatus() == CompanyStatus.SUSPENDED)
                .filter(c -> matchesName(c, needle))
                .sorted(Comparator.comparing(Company::getName, String.CASE_INSENSITIVE_ORDER))
                .map(Company::detachedCopy)
                .toList();
    }

    @Override
    public void save(Company company) {
        if (company == null) {
            throw new IllegalArgumentException("company cannot be null");
        }
        String key = normalizeKey(company.getName());
        companies.compute(key, (k, existing) -> {
            if (existing == null) {
                company.incrementVersion();
                Company stored = company.detachedCopy();
                return stored;
            }
            if (company.getVersion() == 0) {
                throw new IllegalArgumentException(
                        "A production company with this name already exists.");
            }
            if (company.getVersion() != existing.getVersion()) {
                throw new OptimisticLockException("Company", k);
            }
            company.incrementVersion();
            Company stored = company.detachedCopy();
            return stored;
        });
    }

    @Override
    public void delete(String id) {
        throw new UnsupportedOperationException("Delete not supported");
    }

    @Override
    public void deleteAll() {
        companies.clear();
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

