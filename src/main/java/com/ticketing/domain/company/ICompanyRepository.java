package com.ticketing.domain.company;
import java.util.Optional;

import com.ticketing.infrastructure.IRepository;

public interface ICompanyRepository extends IRepository<Company, String> {
    Optional<Company> findByName(String name);
    boolean existsByName(String name);
}
