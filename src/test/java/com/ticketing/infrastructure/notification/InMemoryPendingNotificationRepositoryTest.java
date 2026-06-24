package com.ticketing.infrastructure.notification;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("InMemoryPendingNotificationRepository")
class InMemoryPendingNotificationRepositoryTest {

    private InMemoryPendingNotificationRepository repo;

    @BeforeEach
    void setUp() {
        repo = new InMemoryPendingNotificationRepository();
    }

    // ══════════════════════════════════════════════════════════════════
    //  Storage and retrieval
    // ══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Storage and retrieval")
    class StorageAndRetrieval {

        @Test
        void GivenEmpty_WhenGetPending_ThenReturnsEmptyList() {
            List<String> result = repo.getPendingNotifications("user-1");
            assertTrue(result.isEmpty());
        }

        @Test
        void GivenOneNotification_WhenGetPending_ThenReturnsIt() {
            repo.savePendingNotification("user-1", "Hello");

            List<String> result = repo.getPendingNotifications("user-1");
            assertEquals(1, result.size());
            assertEquals("Hello", result.get(0));
        }

        @Test
        void GivenMultipleNotifications_WhenGetPending_ThenPreservesInsertionOrder() {
            repo.savePendingNotification("user-1", "First");
            repo.savePendingNotification("user-1", "Second");
            repo.savePendingNotification("user-1", "Third");

            List<String> result = repo.getPendingNotifications("user-1");
            assertEquals(List.of("First", "Second", "Third"), result);
        }

        @Test
        void GivenDifferentUsers_WhenGetPending_ThenIsolated() {
            repo.savePendingNotification("alice", "For Alice");
            repo.savePendingNotification("bob", "For Bob");

            assertEquals(List.of("For Alice"), repo.getPendingNotifications("alice"));
            assertEquals(List.of("For Bob"), repo.getPendingNotifications("bob"));
        }

        @Test
        void GivenDuplicateMessages_WhenSaved_ThenBothStored() {
            repo.savePendingNotification("user-1", "Same message");
            repo.savePendingNotification("user-1", "Same message");

            List<String> result = repo.getPendingNotifications("user-1");
            assertEquals(2, result.size());
        }
    }

    // ══════════════════════════════════════════════════════════════════
    //  Seen flag and history (#490)
    // ══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Seen flag and history")
    class SeenAndHistory {

        @Test
        void GivenUnseenAndSeen_WhenGetPending_ThenOnlyUnseenReturned_ButHistoryHasBoth() {
            repo.savePendingNotification("user-1", "undelivered");
            repo.savePendingNotification("user-1", "already delivered", true);

            assertEquals(List.of("undelivered"), repo.getPendingNotifications("user-1"));
            assertEquals(List.of("undelivered", "already delivered"), repo.getNotificationHistory("user-1"));
        }

        @Test
        void GivenUnseenNotifications_WhenMarkAllSeen_ThenPendingEmptyButHistoryRetained() {
            repo.savePendingNotification("user-1", "Msg 1");
            repo.savePendingNotification("user-1", "Msg 2");

            repo.markAllSeen("user-1");

            assertTrue(repo.getPendingNotifications("user-1").isEmpty(), "delivered messages are no longer pending");
            assertEquals(List.of("Msg 1", "Msg 2"), repo.getNotificationHistory("user-1"),
                    "delivered messages remain in history");
        }

        @Test
        void GivenSeenNotification_WhenSavedAgainUnseen_ThenOnlyTheNewOneIsPending() {
            repo.savePendingNotification("user-1", "old", true);
            repo.savePendingNotification("user-1", "new");

            assertEquals(List.of("new"), repo.getPendingNotifications("user-1"));
            assertEquals(2, repo.getNotificationHistory("user-1").size());
        }

        @Test
        void GivenClear_WhenGetHistory_ThenEmpty() {
            repo.savePendingNotification("user-1", "Msg 1", true);
            repo.savePendingNotification("user-1", "Msg 2");

            repo.clearPendingNotifications("user-1");

            assertTrue(repo.getNotificationHistory("user-1").isEmpty());
        }

        @Test
        void GivenNullUserId_WhenGetHistoryOrMarkSeen_ThenSafe() {
            assertTrue(repo.getNotificationHistory(null).isEmpty());
            repo.markAllSeen(null); // no exception
        }
    }

    // ══════════════════════════════════════════════════════════════════
    //  Clear
    // ══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Clear pending")
    class ClearPending {

        @Test
        void GivenPendingNotifications_WhenClear_ThenEmpty() {
            repo.savePendingNotification("user-1", "Msg 1");
            repo.savePendingNotification("user-1", "Msg 2");

            repo.clearPendingNotifications("user-1");

            assertTrue(repo.getPendingNotifications("user-1").isEmpty());
        }

        @Test
        void GivenClearOneUser_WhenGetOtherUser_ThenUnaffected() {
            repo.savePendingNotification("alice", "For Alice");
            repo.savePendingNotification("bob", "For Bob");

            repo.clearPendingNotifications("alice");

            assertTrue(repo.getPendingNotifications("alice").isEmpty());
            assertEquals(1, repo.getPendingNotifications("bob").size());
        }

        @Test
        void GivenNoNotifications_WhenClear_ThenNoError() {
            repo.clearPendingNotifications("nobody");
            // no exception
        }

        @Test
        void GivenNullUserId_WhenClear_ThenNoError() {
            repo.clearPendingNotifications(null);
            // no exception
        }
    }

    // ══════════════════════════════════════════════════════════════════
    //  Validation
    // ══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Validation")
    class Validation {

        @Test
        void GivenNullUserId_WhenSave_ThenThrows() {
            assertThrows(IllegalArgumentException.class,
                    () -> repo.savePendingNotification(null, "msg"));
        }

        @Test
        void GivenNullMessage_WhenSave_ThenThrows() {
            assertThrows(IllegalArgumentException.class,
                    () -> repo.savePendingNotification("user", null));
        }

        @Test
        void GivenNullUserId_WhenGet_ThenReturnsEmpty() {
            assertTrue(repo.getPendingNotifications(null).isEmpty());
        }
    }

    // ══════════════════════════════════════════════════════════════════
    //  Concurrency
    // ══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Concurrency")
    class Concurrency {

        @Test
        void GivenConcurrentWrites_WhenSave_ThenAllPreserved() throws Exception {
            int threadCount = 10;
            int messagesPerThread = 50;
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            CountDownLatch latch = new CountDownLatch(threadCount);

            for (int t = 0; t < threadCount; t++) {
                final int threadNum = t;
                executor.submit(() -> {
                    for (int i = 0; i < messagesPerThread; i++) {
                        repo.savePendingNotification("shared-user",
                                "Thread-" + threadNum + "-Msg-" + i);
                    }
                    latch.countDown();
                });
            }

            assertTrue(latch.await(5, TimeUnit.SECONDS));
            executor.shutdown();

            List<String> all = repo.getPendingNotifications("shared-user");
            assertEquals(threadCount * messagesPerThread, all.size());
        }
    }
}
