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
            Thread.sleep(500); // allow async executor to complete

            List<String> pending = pendingRepo.getPendingNotifications("flaky-user");
            assertEquals(1, pending.size());
            assertEquals("Important message", pending.get(0));
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
