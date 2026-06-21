package com.ticketing.infrastructure.persistence;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.ticketing.domain.admin.Admin;
import com.ticketing.domain.admin.IAdminRepository;
import com.ticketing.domain.exception.OptimisticLockException;
import com.ticketing.infrastructure.persistence.config.AdminJpaRepository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

@Repository
@ConditionalOnProperty(name = "ticketing.persistence", havingValue = "jpa")
public class JpaAdminRepository implements IAdminRepository {

    private final AdminJpaRepository delegate;

    // Use specific persistence context qualifier if needed later
    @PersistenceContext(unitName = "configEntityManagerFactory")
    private EntityManager entityManager;

    public JpaAdminRepository(AdminJpaRepository delegate) {
        this.delegate = delegate;
    }

    @Override
    @Transactional(value = "configTransactionManager", readOnly = true)
    public Optional<Admin> findById(UUID id) {
        if (id == null) {
            return Optional.empty();
        }
        return delegate.findById(id).map(Admin::detachedCopy);
    }

    @Override
    @Transactional(value = "configTransactionManager", readOnly = true)
    public Optional<Admin> findByUsername(String username) {
        if (username == null) {
            return Optional.empty();
        }
        return delegate.findByUsername(username.trim()).map(Admin::detachedCopy);
    }

    @Override
    @Transactional(value = "configTransactionManager", readOnly = true)
    public List<Admin> findAll() {
        List<Admin> result = new ArrayList<>();
        for (Admin a : delegate.findAll()) {
            result.add(a.detachedCopy());
        }
        return result;
    }

    @Override
    @Transactional(value = "configTransactionManager")
    public void save(Admin admin) {
        if (admin == null) {
            throw new IllegalArgumentException("admin cannot be null");
        }
        boolean isNew = !delegate.existsById(admin.getId());
        try {
            if (isNew) {
                admin.incrementVersion();
                entityManager.persist(admin);
            } else {
                entityManager.merge(admin);
            }
            entityManager.flush();
        } catch (ObjectOptimisticLockingFailureException
                 | jakarta.persistence.OptimisticLockException ex) {
            throw new OptimisticLockException("Admin", admin.getId());
        }
    }

    @Override
    @Transactional(value = "configTransactionManager")
    public void delete(UUID id) {
        if (id == null) return;
        delegate.findById(id).ifPresent(delegate::delete);
    }

    @Override
    @Transactional(value = "configTransactionManager", readOnly = true)
    public boolean existsByUsername(String username) {
        if (username == null) return false;
        return delegate.existsByUsername(username.trim());
    }
}
