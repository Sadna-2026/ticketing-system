package com.ticketing.infrastructure.notification;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class StubNotificationServiceTest {

    @Test
    void testNotifyDoesNotThrowException() {
        StubNotificationService service = new StubNotificationService();

        // Acceptance Criteria: No-op implementation does not throw when called
        assertDoesNotThrow(() -> {
            service.notify("user_123", "Your event has been cancelled.");
        }, "The stub notification service should sink the call and never throw an exception.");
    }
}
