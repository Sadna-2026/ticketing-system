package com.ticketing.infrastructure;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import com.ticketing.domain.company.Company;
import com.ticketing.domain.company.ICompanyRepository;

public class InMemoryCompanyRepository implements ICompanyRepository {
    
    private final ConcurrentHashMap<String, Company> companies;
    
    public InMemoryCompanyRepository() {
        this.companies = new ConcurrentHashMap<>();
    }
    
    @Override
    public Optional<Company> findById(String id) {
        return Optional.ofNullable(companies.get(id.toLowerCase().trim()));
    }
    
    public boolean existsById(String id) {
        return findById(id).isPresent();
    }
    
    @Override
    public boolean existsByName(String name) {
        return findByName(name).isPresent();
    }
    
    @Override
    public Optional<Company> findByName(String name) {
        return Optional.ofNullable(companies.get(name.toLowerCase().trim()));
    }
    
    @Override
    public List<Company> getAll() {
        return new ArrayList<>(companies.values());
    }
    
    @Override
    public void save(Company entity) {
        companies.put(entity.getName().toLowerCase().trim(), entity);
    }
    
    @Override
    public void delete(String id) {
        throw new UnsupportedOperationException("Delete not supported");
    }
}
