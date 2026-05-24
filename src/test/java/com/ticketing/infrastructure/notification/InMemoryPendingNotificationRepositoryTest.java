package com.ticketing.infrastructure.notification;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class InMemoryPendingNotificationRepositoryTest {

    private InMemoryPendingNotificationRepository repo;

    @BeforeEach
    void setUp() {
        repo = new InMemoryPendingNotificationRepository();
    }

    @Test
    void GivenNoNotifications_WhenGetPending_ThenReturnsEmptyList() {
        List<String> pending = repo.getPendingNotifications("user1");
        assertTrue(pending.isEmpty());
    }

    @Test
    void GivenSavedNotification_WhenGetPending_ThenReturnsIt() {
        repo.savePendingNotification("user1", "Hello");

        List<String> pending = repo.getPendingNotifications("user1");
        assertEquals(1, pending.size());
        assertEquals("Hello", pending.get(0));
    }

    @Test
    void GivenMultipleNotifications_WhenGetPending_ThenReturnsAllInOrder() {
        repo.savePendingNotification("user1", "First");
        repo.savePendingNotification("user1", "Second");
        repo.savePendingNotification("user1", "Third");

        List<String> pending = repo.getPendingNotifications("user1");
        assertEquals(3, pending.size());
        assertEquals("First", pending.get(0));
        assertEquals("Second", pending.get(1));
        assertEquals("Third", pending.get(2));
    }

    @Test
    void GivenSavedNotifications_WhenClear_ThenGetPendingReturnsEmpty() {
        repo.savePendingNotification("user1", "Hello");
        repo.savePendingNotification("user1", "World");

        repo.clearPendingNotifications("user1");

        assertTrue(repo.getPendingNotifications("user1").isEmpty());
    }

    @Test
    void GivenTwoUsers_WhenSaveForEach_ThenNotificationsAreIsolated() {
        repo.savePendingNotification("user1", "For user 1");
        repo.savePendingNotification("user2", "For user 2");

        assertEquals(1, repo.getPendingNotifications("user1").size());
        assertEquals("For user 1", repo.getPendingNotifications("user1").get(0));
        assertEquals(1, repo.getPendingNotifications("user2").size());
        assertEquals("For user 2", repo.getPendingNotifications("user2").get(0));
    }

    @Test
    void GivenClearForOneUser_WhenGetOther_ThenOtherUnaffected() {
        repo.savePendingNotification("user1", "msg1");
        repo.savePendingNotification("user2", "msg2");

        repo.clearPendingNotifications("user1");

        assertTrue(repo.getPendingNotifications("user1").isEmpty());
        assertEquals(1, repo.getPendingNotifications("user2").size());
    }

    @Test
    void GivenNullUserId_WhenSave_ThenThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> repo.savePendingNotification(null, "msg"));
    }

    @Test
    void GivenNullMessage_WhenSave_ThenThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> repo.savePendingNotification("user1", null));
    }

    @Test
    void GivenNullUserId_WhenGetPending_ThenReturnsEmpty() {
        assertTrue(repo.getPendingNotifications(null).isEmpty());
    }

    @Test
    void GivenConcurrentSaves_WhenMultipleThreads_ThenAllNotificationsPersisted() throws InterruptedException {
        int threadCount = 20;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);

        for (int i = 0; i < threadCount; i++) {
            final int idx = i;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    repo.savePendingNotification("user1", "msg-" + idx);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        doneLatch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        List<String> pending = repo.getPendingNotifications("user1");
        assertEquals(threadCount, pending.size());
    }
}
