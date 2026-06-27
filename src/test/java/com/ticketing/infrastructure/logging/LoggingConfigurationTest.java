package com.ticketing.infrastructure.logging;

import static com.ticketing.infrastructure.logging.LogFileTestSupport.ERROR_LOG;
import static com.ticketing.infrastructure.logging.LogFileTestSupport.EVENT_LOG;
import static com.ticketing.infrastructure.logging.LogFileTestSupport.fileContains;
import static com.ticketing.infrastructure.logging.LogFileTestSupport.fileContainsLineWith;
import static com.ticketing.infrastructure.logging.LogFileTestSupport.waitForFileToContain;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@DisplayName("Logging configuration")
class LoggingConfigurationTest {

    @Test
    void GivenWarnMessage_WhenLogged_ThenItIsPersistedToEventLogAndNotErrorLog() throws Exception {
        Logger log = LoggerFactory.getLogger("com.ticketing.logging.warn-routing-test");
        String marker = "WARN_ROUTING_" + UUID.randomUUID();

        log.warn(marker);

        assertTrue(waitForFileToContain(EVENT_LOG, marker, Duration.ofSeconds(3)),
                "WARN message should be persisted to logs/event.log");
        assertTrue(fileContainsLineWith(EVENT_LOG, marker, "WARN"),
                "WARN message should be written with WARN level in logs/event.log");
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
}
