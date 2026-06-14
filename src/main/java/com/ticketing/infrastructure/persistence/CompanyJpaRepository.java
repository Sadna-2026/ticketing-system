package com.ticketing.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ticketing.domain.company.Company;

/**
 * Spring Data JPA repository for the {@link Company} aggregate root.
 * The company's {@code @Id} is its (String) name. Only instantiated when the
 * JPA persistence profile is active (see {@link JpaCompanyRepository}).
 */
public interface CompanyJpaRepository extends JpaRepository<Company, String> {
}
