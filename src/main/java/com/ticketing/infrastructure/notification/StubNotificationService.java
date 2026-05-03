package com.ticketing.infrastructure.notification;

import com.ticketing.application.INotificationService;
import java.util.logging.Logger;

/**
 * No-op stub implementation for V1.
 * Safely sinks notification calls without attempting external delivery.
 */
public class StubNotificationService implements INotificationService {
    
    private static final Logger logger = Logger.getLogger(StubNotificationService.class.getName());

    @Override
    public void notify(String memberId, String message) {
        // V1 Requirement: Do NOT implement full delivery.
        // We only log the action so developers can verify it triggered correctly during testing.
        logger.info("STUB EVENT [Deferred to V2]: Would send notification to user [" + memberId + "] -> Message: " + message);
    }
}
