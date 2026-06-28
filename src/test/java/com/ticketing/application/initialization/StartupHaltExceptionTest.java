package com.ticketing.application.initialization;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class StartupHaltExceptionTest {

    @Test
    void failInitializationProducesFramedMessage() {
        StartupHaltException ex = assertThrows(
                StartupHaltException.class,
                () -> StartupHaltException.failInitialization("payment service unreachable"));

        assertTrue(ex.getMessage().contains("APPLICATION STARTUP HALTED"));
        assertTrue(ex.getMessage().contains("INITIALIZATION ERROR"));
        assertTrue(ex.getMessage().contains("payment service unreachable"));
    }

    @Test
    void fillInStackTraceReturnsSelf() {
        StartupHaltException ex = new StartupHaltException("halt");
        assertSame(ex, ex.fillInStackTrace());
    }
}
