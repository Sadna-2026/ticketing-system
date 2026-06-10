package com.ticketing.domain.company;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.ticketing.infrastructure.IRepository;

public interface ICompanyRepository extends IRepository<Company, String> {
    Optional<Company> findByName(String name);
    boolean existsByName(String name);
    List<Company> findFounderLifecycleCompanies(UUID founderId, String query);
}
