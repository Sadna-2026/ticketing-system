package com.ticketing.infrastructure.notification;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class WebSocketNotificationServiceTest {

    private WebSocketNotificationService service;
    private InMemoryPendingNotificationRepository pendingRepo;
    private ExecutorService executor;

    @BeforeEach
    void setUp() {
        pendingRepo = new InMemoryPendingNotificationRepository();
        // Use a single-thread executor so we can predictably await completion
        executor = Executors.newSingleThreadExecutor();
        service = new WebSocketNotificationService(pendingRepo, executor);
    }

    @Test
    void GivenConnectedUser_WhenNotify_ThenListenerReceivesMessage() throws InterruptedException {
        List<String> received = Collections.synchronizedList(new ArrayList<>());
        service.registerListener("user1", received::add);

        service.notify("user1", "Hello!");

        awaitExecutor();
        assertEquals(1, received.size());
        assertEquals("Hello!", received.get(0));
    }

    @Test
    void GivenConnectedUser_WhenMultipleNotifications_ThenAllDelivered() throws InterruptedException {
        List<String> received = Collections.synchronizedList(new ArrayList<>());
        service.registerListener("user1", received::add);

        service.notify("user1", "First");
        service.notify("user1", "Second");
        service.notify("user1", "Third");

        awaitExecutor();
        assertEquals(3, received.size());
        assertEquals("First", received.get(0));
        assertEquals("Second", received.get(1));
        assertEquals("Third", received.get(2));
    }

    @Test
    void GivenDisconnectedUser_WhenNotify_ThenMessageSavedAsPending() {
        service.notify("offline-user", "You missed this");

        List<String> pending = pendingRepo.getPendingNotifications("offline-user");
        assertEquals(1, pending.size());
        assertEquals("You missed this", pending.get(0));
    }

    @Test
    void GivenPendingNotifications_WhenUserConnects_ThenPendingFlushedToListener() {
        // Notifications arrive while user is offline
        service.notify("user1", "Missed msg 1");
        service.notify("user1", "Missed msg 2");

        assertEquals(2, pendingRepo.getPendingNotifications("user1").size());

        // User connects
        List<String> received = Collections.synchronizedList(new ArrayList<>());
        service.registerListener("user1", received::add);

        // Pending messages should be flushed immediately on registration
        assertEquals(2, received.size());
        assertEquals("Missed msg 1", received.get(0));
        assertEquals("Missed msg 2", received.get(1));

        // Pending store should be cleared
        assertTrue(pendingRepo.getPendingNotifications("user1").isEmpty());
    }

    @Test
    void GivenConnectedUser_WhenRemoveListener_ThenSubsequentNotificationsGoPending() throws InterruptedException {
        List<String> received = Collections.synchronizedList(new ArrayList<>());
        service.registerListener("user1", received::add);

        service.notify("user1", "Before disconnect");
        awaitExecutor();
        assertEquals(1, received.size());

        // User disconnects
        service.removeListener("user1");
        assertFalse(service.hasListener("user1"));

        // Subsequent notification goes to pending
        service.notify("user1", "After disconnect");
        assertEquals(1, pendingRepo.getPendingNotifications("user1").size());
        assertEquals("After disconnect", pendingRepo.getPendingNotifications("user1").get(0));
        // Listener did NOT receive it
        assertEquals(1, received.size());
    }

    @Test
    void GivenListenerThrows_WhenNotify_ThenMessageSavedAsPending() throws InterruptedException {
        service.registerListener("user1", msg -> {
            throw new RuntimeException("WebSocket broken");
        });

        service.notify("user1", "Important");

        awaitExecutor();

        // Message should be saved as pending since delivery failed
        List<String> pending = pendingRepo.getPendingNotifications("user1");
        assertEquals(1, pending.size());
        assertEquals("Important", pending.get(0));
    }

    @Test
    void GivenTwoUsers_WhenNotify_ThenMessagesAreIsolated() throws InterruptedException {
        List<String> user1Msgs = Collections.synchronizedList(new ArrayList<>());
        List<String> user2Msgs = Collections.synchronizedList(new ArrayList<>());
        service.registerListener("user1", user1Msgs::add);
        service.registerListener("user2", user2Msgs::add);

        service.notify("user1", "For user 1");
        service.notify("user2", "For user 2");

        awaitExecutor();
        assertEquals(1, user1Msgs.size());
        assertEquals("For user 1", user1Msgs.get(0));
        assertEquals(1, user2Msgs.size());
        assertEquals("For user 2", user2Msgs.get(0));
    }

    @Test
    void GivenHasListener_WhenChecked_ThenReturnsCorrectState() {
        assertFalse(service.hasListener("user1"));

        service.registerListener("user1", msg -> {});
        assertTrue(service.hasListener("user1"));

        service.removeListener("user1");
        assertFalse(service.hasListener("user1"));
    }

    @Test
    void GivenNullMemberId_WhenNotify_ThenNoExceptionAndNoPending() {
        service.notify(null, "msg");
        // Should not throw and should not save anything
        assertTrue(pendingRepo.getPendingNotifications(null).isEmpty());
    }

    @Test
    void GivenNullMessage_WhenNotify_ThenNoException() {
        service.notify("user1", null);
        // Should not throw
    }

    @Test
    void GivenConcurrentNotifications_WhenMultipleThreads_ThenAllDeliveredOrPending() throws InterruptedException {
        List<String> received = Collections.synchronizedList(new ArrayList<>());
        service.registerListener("user1", received::add);

        int threadCount = 20;
        ExecutorService pool = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);

        for (int i = 0; i < threadCount; i++) {
            final int idx = i;
            pool.submit(() -> {
                try {
                    startLatch.await();
                    service.notify("user1", "msg-" + idx);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        doneLatch.await(10, TimeUnit.SECONDS);
        pool.shutdown();
        pool.awaitTermination(5, TimeUnit.SECONDS);
        awaitExecutor();

        assertEquals(threadCount, received.size());
    }

    /**
     * Submits a sentinel task and waits for it to complete,
     * ensuring all prior tasks on the single-thread executor have finished.
     */
    private void awaitExecutor() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        executor.submit(latch::countDown);
        assertTrue(latch.await(5, TimeUnit.SECONDS), "Executor did not drain in time");
    }
}
