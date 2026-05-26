package com.ticketing.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.ticketing.application.services.NotificationQueryService;
import com.ticketing.infrastructure.notification.InMemoryPendingNotificationRepository;

@DisplayName("NotificationQueryService")
class NotificationQueryServiceTest {

    private InMemoryPendingNotificationRepository pendingRepo;
    private NotificationQueryService queryService;

    @BeforeEach
    void setUp() {
        pendingRepo = new InMemoryPendingNotificationRepository();
        queryService = new NotificationQueryService(pendingRepo);
    }

    @Nested
    @DisplayName("Get pending notifications")
    class GetPending {

        @Test
        void GivenNoPending_WhenGet_ThenReturnsEmpty() {
            List<String> result = queryService.getPendingNotifications("user-1");
            assertTrue(result.isEmpty());
        }

        @Test
        void GivenPendingMessages_WhenGet_ThenReturnsAllInOrder() {
            pendingRepo.savePendingNotification("user-1", "Notification A");
            pendingRepo.savePendingNotification("user-1", "Notification B");

            List<String> result = queryService.getPendingNotifications("user-1");

            assertEquals(2, result.size());
            assertEquals("Notification A", result.get(0));
            assertEquals("Notification B", result.get(1));
        }

        @Test
        void GivenPendingForOtherUser_WhenGet_ThenReturnsEmpty() {
            pendingRepo.savePendingNotification("alice", "For Alice only");

            List<String> result = queryService.getPendingNotifications("bob");

            assertTrue(result.isEmpty());
        }
    }

    @Nested
    @DisplayName("Clear pending notifications")
    class ClearPending {

        @Test
        void GivenPendingMessages_WhenClear_ThenSubsequentGetReturnsEmpty() {
            pendingRepo.savePendingNotification("user-1", "Msg 1");
            pendingRepo.savePendingNotification("user-1", "Msg 2");

            queryService.clearPendingNotifications("user-1");

            assertTrue(queryService.getPendingNotifications("user-1").isEmpty());
        }

        @Test
        void GivenClearOneUser_WhenGetOther_ThenUnaffected() {
            pendingRepo.savePendingNotification("alice", "A");
            pendingRepo.savePendingNotification("bob", "B");

            queryService.clearPendingNotifications("alice");

            assertTrue(queryService.getPendingNotifications("alice").isEmpty());
            assertEquals(1, queryService.getPendingNotifications("bob").size());
        }

        @Test
        void GivenNoPending_WhenClear_ThenNoError() {
            queryService.clearPendingNotifications("nobody");
            // no exception
        }
    }
}
