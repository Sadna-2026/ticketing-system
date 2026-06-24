package com.ticketing.infrastructure.persistence;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import com.ticketing.domain.notification.IPendingNotificationRepository;
import com.ticketing.domain.notification.PendingNotification;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.transaction.Transactional;

@Repository
@ConditionalOnProperty(name = "ticketing.persistence", havingValue = "jpa")
public class JpaPendingNotificationRepository implements IPendingNotificationRepository {

    @PersistenceContext(unitName = "operational")
    private EntityManager entityManager;

    @Override
    @Transactional
    public void savePendingNotification(String userId, String message) {
        savePendingNotification(userId, message, false);
    }

    @Override
    @Transactional
    public void savePendingNotification(String userId, String message, boolean seen) {
        entityManager.persist(new PendingNotification(userId, message, seen));
    }

    @Override
    public List<String> getPendingNotifications(String userId) {
        return entityManager.createQuery(
                "SELECT p FROM PendingNotification p WHERE p.userId = :userId AND p.seen = false ORDER BY p.createdAt ASC",
                PendingNotification.class)
                .setParameter("userId", userId)
                .getResultList()
                .stream()
                .map(PendingNotification::getMessage)
                .collect(Collectors.toList());
    }

    @Override
    public List<String> getNotificationHistory(String userId) {
        return entityManager.createQuery(
                "SELECT p FROM PendingNotification p WHERE p.userId = :userId ORDER BY p.createdAt ASC",
                PendingNotification.class)
                .setParameter("userId", userId)
                .getResultList()
                .stream()
                .map(PendingNotification::getMessage)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void markAllSeen(String userId) {
        entityManager.createQuery(
                "UPDATE PendingNotification p SET p.seen = true WHERE p.userId = :userId AND p.seen = false")
                .setParameter("userId", userId)
                .executeUpdate();
    }

    @Override
    @Transactional
    public void clearPendingNotifications(String userId) {
        entityManager.createQuery("DELETE FROM PendingNotification p WHERE p.userId = :userId")
                .setParameter("userId", userId)
                .executeUpdate();
    }

    @Override
    @Transactional
    public void deleteAll() {
        entityManager.createQuery("DELETE FROM PendingNotification").executeUpdate();
    }
}
