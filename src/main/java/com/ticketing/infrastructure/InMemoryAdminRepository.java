package com.ticketing.infrastructure;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import com.ticketing.domain.admin.Admin;
import com.ticketing.domain.admin.IAdminRepository;
import com.ticketing.domain.exception.OptimisticLockException;

@Repository
@ConditionalOnProperty(name = "ticketing.persistence", havingValue = "memory", matchIfMissing = true)
public class InMemoryAdminRepository implements IAdminRepository {

    private final ConcurrentHashMap<UUID, Admin> byId = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, UUID> idByUsername = new ConcurrentHashMap<>();

    @Override
    public Optional<Admin> findById(UUID id) {
        if (id == null) return Optional.empty();
        Admin admin = byId.get(id);
        return admin != null ? Optional.of(admin.detachedCopy()) : Optional.empty();
    }

    @Override
    public Optional<Admin> findByUsername(String username) {
        if (username == null) return Optional.empty();
        UUID id = idByUsername.get(normalize(username));
        return id == null ? Optional.empty() : findById(id);
    }

    @Override
    public List<Admin> findAll() {
        List<Admin> admins = new ArrayList<>(byId.size());
        for (Admin admin : byId.values()) {
            admins.add(admin.detachedCopy());
        }
        return admins;
    }

    @Override
    public void save(Admin admin) {
        if (admin == null) throw new IllegalArgumentException("admin is required");
        String newUsername = normalize(admin.getUsername());
        byId.compute(admin.getId(), (id, existing) -> {
            UUID usernameOwner = idByUsername.get(newUsername);
            if (usernameOwner != null && !usernameOwner.equals(id)) {
                throw new IllegalArgumentException("An admin with this username already exists.");
            }
            if (existing == null) {
                admin.incrementVersion();
                Admin stored = admin.detachedCopy();
                idByUsername.put(newUsername, id);
                return stored;
            }
            if (admin.getVersion() != existing.getVersion()) {
                throw new OptimisticLockException("Admin", id);
            }
            String oldUsername = normalize(existing.getUsername());
            if (!oldUsername.equals(newUsername)) {
                idByUsername.remove(oldUsername, id);
            }
            admin.incrementVersion();
            Admin stored = admin.detachedCopy();
            idByUsername.put(newUsername, id);
            return stored;
        });
    }

    @Override
    public void delete(UUID id) {
        if (id == null) return;
        Admin removed = byId.remove(id);
        if (removed != null) {
            idByUsername.remove(normalize(removed.getUsername()), id);
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
