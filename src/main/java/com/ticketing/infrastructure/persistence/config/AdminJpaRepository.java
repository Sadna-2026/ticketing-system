package com.ticketing.infrastructure.persistence.config;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ticketing.domain.admin.Admin;

/**
 * Spring Data JPA repository for the {@link Admin} aggregate root.
 */
public interface AdminJpaRepository extends JpaRepository<Admin, UUID> {

    Optional<Admin> findByUsername(String username);

    boolean existsByUsername(String username);
}
