package com.ticketing.infrastructure.notification;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("WebSocketNotificationService")
class WebSocketNotificationServiceTest {

    private InMemoryPendingNotificationRepository pendingRepo;
    private WebSocketNotificationService service;

    @BeforeEach
    void setUp() {
        pendingRepo = new InMemoryPendingNotificationRepository();
        // Use a single-thread executor for deterministic test behavior
        service = new WebSocketNotificationService(pendingRepo,
                Executors.newSingleThreadExecutor());
    }

    @AfterEach
    void tearDown() {
        service.shutdown();
    }

    // ══════════════════════════════════════════════════════════════════
    //  Real-time delivery (user online)
    // ══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Real-time delivery")
    class RealTimeDelivery {

        @Test
        void GivenOnlineUser_WhenNotify_ThenListenerReceivesMessage() throws Exception {
            String memberId = "user-1";
            List<String> received = Collections.synchronizedList(new ArrayList<>());
            CountDownLatch latch = new CountDownLatch(1);

            service.registerListener(memberId, msg -> {
                received.add(msg);
                latch.countDown();
            });

            service.notify(memberId, "Hello!");
            assertTrue(latch.await(2, TimeUnit.SECONDS));

            assertEquals(1, received.size());
            assertEquals("Hello!", received.get(0));
        }

        @Test
        void GivenOnlineUser_WhenMultipleNotifications_ThenAllDeliveredInOrder() throws Exception {
            String memberId = "user-2";
            List<String> received = Collections.synchronizedList(new ArrayList<>());
            CountDownLatch latch = new CountDownLatch(3);

            service.registerListener(memberId, msg -> {
                received.add(msg);
                latch.countDown();
            });

            service.notify(memberId, "First");
            service.notify(memberId, "Second");
            service.notify(memberId, "Third");
            assertTrue(latch.await(2, TimeUnit.SECONDS));

            assertEquals(List.of("First", "Second", "Third"), received);
        }

        @Test
        void GivenOnlineUser_WhenNotifyDifferentUser_ThenListenerNotTriggered() throws Exception {
            List<String> received = Collections.synchronizedList(new ArrayList<>());
            service.registerListener("user-A", received::add);

            service.notify("user-B", "Not for you");

            Thread.sleep(200); // give executor time to process
            assertTrue(received.isEmpty());
        }
    }

    // ══════════════════════════════════════════════════════════════════
    //  Offline / delayed delivery
    // ══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Delayed delivery (user offline)")
    class DelayedDelivery {

        @Test
        void GivenOfflineUser_WhenNotify_ThenMessageSavedAsPending() {
            service.notify("offline-user", "You have a message");

            List<String> pending = pendingRepo.getPendingNotifications("offline-user");
            assertEquals(1, pending.size());
            assertEquals("You have a message", pending.get(0));
        }

        @Test
        void GivenOfflineUser_WhenMultipleNotifications_ThenAllSavedInOrder() {
            service.notify("offline-user", "Msg 1");
            service.notify("offline-user", "Msg 2");
            service.notify("offline-user", "Msg 3");

            List<String> pending = pendingRepo.getPendingNotifications("offline-user");
            assertEquals(List.of("Msg 1", "Msg 2", "Msg 3"), pending);
        }

        @Test
        void GivenPendingNotifications_WhenUserReconnects_ThenFlushedImmediately() throws Exception {
            // User is offline — notifications accumulate
            service.notify("user-3", "Pending 1");
            service.notify("user-3", "Pending 2");

            List<String> received = Collections.synchronizedList(new ArrayList<>());
            CountDownLatch latch = new CountDownLatch(2);

            // User comes online — pending should flush
            service.registerListener("user-3", msg -> {
                received.add(msg);
                latch.countDown();
            });

            assertTrue(latch.await(2, TimeUnit.SECONDS));
            assertEquals(List.of("Pending 1", "Pending 2"), received);

            // Pending store should be cleared after flush
            assertTrue(pendingRepo.getPendingNotifications("user-3").isEmpty());
        }

        @Test
        void GivenPendingNotifications_WhenFlushedOnReconnect_ThenRetainedInHistoryNotReFlushed() throws Exception {
            // #490: a delivered notification must stay in the member's history inbox, and must not be
            // re-delivered when a listener connects again (e.g. on the next page load).
            service.notify("user-9", "Your refund is on its way");

            CountDownLatch first = new CountDownLatch(1);
            service.registerListener("user-9", msg -> first.countDown());
            assertTrue(first.await(2, TimeUnit.SECONDS));

            // No longer pending, but retained in history.
            assertTrue(pendingRepo.getPendingNotifications("user-9").isEmpty());
            assertEquals(List.of("Your refund is on its way"), pendingRepo.getNotificationHistory("user-9"));

            // A second listener (new page load) must NOT receive it again.
            service.removeListener("user-9");
            List<String> secondReceived = Collections.synchronizedList(new ArrayList<>());
            service.registerListener("user-9", secondReceived::add);
            Thread.sleep(150);
            assertTrue(secondReceived.isEmpty(), "already-delivered notification must not be re-flushed");
        }

        @Test
        void GivenOnlineUser_WhenNotify_ThenRetainedInHistoryAsSeen() throws Exception {
            CountDownLatch delivered = new CountDownLatch(1);
            service.registerListener("user-10", msg -> delivered.countDown());

            service.notify("user-10", "Live update");
            assertTrue(delivered.await(2, TimeUnit.SECONDS));
            
            // Wait for the async executor to finish saving the history
            Awaitility.await().atMost(2, TimeUnit.SECONDS)
                .until(() -> pendingRepo.getNotificationHistory("user-10").contains("Live update"));

            // Delivered live -> in history, but not pending (so it is never re-pushed).
            assertEquals(List.of("Live update"), pendingRepo.getNotificationHistory("user-10"));
            assertTrue(pendingRepo.getPendingNotifications("user-10").isEmpty());
        }

        @Test
        void GivenNoPendingNotifications_WhenUserReconnects_ThenNoFlush() {
            List<String> received = Collections.synchronizedList(new ArrayList<>());
            service.registerListener("clean-user", received::add);

            assertTrue(received.isEmpty());
        }
    }

    // ══════════════════════════════════════════════════════════════════
    //  Listener lifecycle
    // ══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Listener management")
    class ListenerManagement {

        @Test
        void GivenRegisteredListener_WhenRemoved_ThenSubsequentNotificationsGoPending() {
            List<String> received = Collections.synchronizedList(new ArrayList<>());
            service.registerListener("user-4", received::add);
            assertTrue(service.hasListener("user-4"));

            service.removeListener("user-4");
            assertFalse(service.hasListener("user-4"));

            service.notify("user-4", "After disconnect");
            List<String> pending = pendingRepo.getPendingNotifications("user-4");
            assertEquals(1, pending.size());
        }

        @Test
        void GivenTwoListenersForMember_WhenOneRegistrationIsRemoved_ThenOtherListenerRemainsActive() throws Exception {
            List<String> first = Collections.synchronizedList(new ArrayList<>());
            List<String> second = Collections.synchronizedList(new ArrayList<>());
            CountDownLatch delivered = new CountDownLatch(1);
            String firstRegistration = service.registerListener("user-5", first::add);
            service.registerListener("user-5", message -> {
                second.add(message);
                delivered.countDown();
            });

            service.removeListener("user-5", firstRegistration);
            service.notify("user-5", "Still connected");

            assertTrue(delivered.await(2, TimeUnit.SECONDS));
            assertTrue(first.isEmpty());
            assertEquals(List.of("Still connected"), second);
            assertTrue(service.hasListener("user-5"));
            assertTrue(pendingRepo.getPendingNotifications("user-5").isEmpty());
        }

        @Test
        void GivenTwoListenersForMember_WhenNotify_ThenBothListenersReceiveMessage() throws Exception {
            List<String> first = Collections.synchronizedList(new ArrayList<>());
            List<String> second = Collections.synchronizedList(new ArrayList<>());
            CountDownLatch delivered = new CountDownLatch(2);
            service.registerListener("user-6", message -> {
                first.add(message);
                delivered.countDown();
            });
            service.registerListener("user-6", message -> {
                second.add(message);
                delivered.countDown();
            });

            service.notify("user-6", "Broadcast");

            assertTrue(delivered.await(2, TimeUnit.SECONDS));
            assertEquals(List.of("Broadcast"), first);
            assertEquals(List.of("Broadcast"), second);
        }

        @Test
        void GivenNoListener_WhenHasListener_ThenReturnsFalse() {
            assertFalse(service.hasListener("nobody"));
        }

        @Test
        void GivenNullMemberId_WhenRegister_ThenThrows() {
            assertThrows(IllegalArgumentException.class,
                    () -> service.registerListener(null, msg -> {}));
        }

        @Test
        void GivenNullListener_WhenRegister_ThenThrows() {
            assertThrows(IllegalArgumentException.class,
                    () -> service.registerListener("user", null));
        }
    }

    // ══════════════════════════════════════════════════════════════════
    //  Error handling
    // ══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Error handling")
    class ErrorHandling {

        @Test
        void GivenListenerThrows_WhenNotify_ThenMessageSavedAsPending() throws Exception {
            service.registerListener("flaky-user", msg -> {
                throw new RuntimeException("WebSocket broke");
            });

            service.notify("flaky-user", "Important message");
            
            // Wait for async executor to complete and save to pending
            Awaitility.await().atMost(2, TimeUnit.SECONDS)
                .until(() -> pendingRepo.getPendingNotifications("flaky-user").size() == 1);

            List<String> pending = pendingRepo.getPendingNotifications("flaky-user");
            assertEquals(1, pending.size());
            assertEquals("Important message", pending.get(0));
            // no exception, no pending stored
        }

        @Test
        void GivenNullMemberId_WhenNotify_ThenIgnored() {
            service.notify(null, "message");
            // no exception, no pending stored
        }

        @Test
        void GivenNullMessage_WhenNotify_ThenIgnored() {
            service.notify("user", null);
            // no exception, no pending stored
        }
    }
}
