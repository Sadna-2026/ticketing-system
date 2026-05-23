package com.ticketing.infrastructure.logging;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@DisplayName("Logging configuration")
class LoggingConfigurationTest {

    private static final Path EVENT_LOG = Path.of("logs", "event.log");
    private static final Path ERROR_LOG = Path.of("logs", "error.log");

    @Test
    void GivenWarnMessage_WhenLogged_ThenItIsPersistedToEventLogAndNotErrorLog() throws Exception {
        Logger log = LoggerFactory.getLogger("com.ticketing.logging.warn-routing-test");
        String marker = "WARN_ROUTING_" + UUID.randomUUID();

        log.warn(marker);

        assertTrue(waitForFileToContain(EVENT_LOG, marker, Duration.ofSeconds(3)),
                "WARN message should be persisted to logs/event.log");
        assertFalse(fileContains(ERROR_LOG, marker),
                "WARN message must not be persisted to logs/error.log");
    }

    @Test
    void GivenErrorMessage_WhenLogged_ThenItIsPersistedToErrorLogAndNotEventLog() throws Exception {
        Logger log = LoggerFactory.getLogger("com.ticketing.logging.error-routing-test");
        String marker = "ERROR_ROUTING_" + UUID.randomUUID();

        log.error(marker);

        assertTrue(waitForFileToContain(ERROR_LOG, marker, Duration.ofSeconds(3)),
                "ERROR message should be persisted to logs/error.log");
        assertFalse(fileContains(EVENT_LOG, marker),
                "ERROR message should not be duplicated into logs/event.log");
    }

    private static boolean waitForFileToContain(Path path, String marker, Duration timeout) throws Exception {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            if (fileContains(path, marker)) {
                return true;
            }
            Thread.sleep(50);
        }
        return false;
    }

    private static boolean fileContains(Path path, String marker) throws Exception {
        return Files.exists(path) && Files.readString(path).contains(marker);
    }
}
