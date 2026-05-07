package com.ticketing.infrastructure;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Repository;

import com.ticketing.domain.admin.Admin;
import com.ticketing.domain.admin.IAdminRepository;

@Repository
public class InMemoryAdminRepository implements IAdminRepository {

    private final ConcurrentHashMap<UUID, Admin> byId = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, UUID> idByUsername = new ConcurrentHashMap<>();

    @Override
    public Optional<Admin> findById(UUID id) {
        if (id == null) return Optional.empty();
        return Optional.ofNullable(byId.get(id));
    }

    @Override
    public Optional<Admin> findByUsername(String username) {
        if (username == null) return Optional.empty();
        UUID id = idByUsername.get(normalize(username));
        return id == null ? Optional.empty() : findById(id);
    }

    @Override
    public List<Admin> findAll() {
        return new ArrayList<>(byId.values());
    }

    @Override
    public synchronized void save(Admin admin) {
        if (admin == null) throw new IllegalArgumentException("admin is required");
        Admin existing = byId.get(admin.getId());
        if (existing != null && !existing.getUsername().equalsIgnoreCase(admin.getUsername())) {
            idByUsername.remove(normalize(existing.getUsername()));
        }
        byId.put(admin.getId(), admin);
        idByUsername.put(normalize(admin.getUsername()), admin.getId());
    }

    @Override
    public synchronized void delete(UUID id) {
        if (id == null) return;
        Admin removed = byId.remove(id);
        if (removed != null) {
            idByUsername.remove(normalize(removed.getUsername()));
        }
    }

    @Override
    public boolean existsByUsername(String username) {
        return username != null && idByUsername.containsKey(normalize(username));
    }

    private static String normalize(String username) {
        return username.trim().toLowerCase();
    }
}
