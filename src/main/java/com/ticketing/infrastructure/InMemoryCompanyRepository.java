package com.ticketing.infrastructure;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import com.ticketing.domain.company.Company;
import com.ticketing.domain.company.ICompanyRepository;
import com.ticketing.domain.exception.OptimisticLockException;

/**
 * In-memory implementation of ICompanyRepository with CAS-style optimistic locking.
 * Uses the aggregate version counter to detect concurrent modifications.
 * Stored aggregates are detached from callers: reads return copies and saves persist copies.
 */
@org.springframework.stereotype.Component
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

    private static String normalizeKey(String name) {
        return name.toLowerCase().trim();
    }

}

