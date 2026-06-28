package com.ticketing.application.initialization;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class InitialStateFileLoaderTest {

    @Test
    void givenNullLocation_whenLoad_thenHalts() {
        assertThrows(StartupHaltException.class, () -> InitialStateFileLoader.load(null));
    }

    @Test
    void givenBlankLocation_whenLoad_thenHalts() {
        StartupHaltException ex = assertThrows(
                StartupHaltException.class,
                () -> InitialStateFileLoader.load("   "));
        assertTrue(ex.getMessage().contains("ticketing.initial-state.file is blank"));
    }

    @Test
    void givenMissingClasspathResource_whenLoad_thenHalts() {
        StartupHaltException ex = assertThrows(
                StartupHaltException.class,
                () -> InitialStateFileLoader.load("classpath:initial-state/does-not-exist.txt"));
        assertTrue(ex.getMessage().contains("classpath initial-state resource not found"));
    }

    @Test
    void givenClasspathResourceWithLeadingSlash_whenLoad_thenReturnsContent() {
        String content = InitialStateFileLoader.load("classpath:/initial-state/staff-demo-v3.txt");
        assertFalse(content.isBlank());
        assertTrue(content.contains("login"));
    }
}
