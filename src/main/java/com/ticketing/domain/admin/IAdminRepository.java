package com.ticketing.domain.admin;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface IAdminRepository {
    Optional<Admin> findById(UUID id);
    Optional<Admin> findByUsername(String username);
    List<Admin> findAll();
    void save(Admin admin);
    void delete(UUID id);
    boolean existsByUsername(String username);
}
