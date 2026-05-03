package com.ticketing.domain.company;
import com.ticketing.infrastructure.IRepository;

public interface ICompanyRepository extends IRepository<Company> {
    Company findByName(String name);
    boolean existsByName(String name);
}
