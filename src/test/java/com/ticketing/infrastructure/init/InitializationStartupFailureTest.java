package com.ticketing.infrastructure.init;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

import com.ticketing.TicketingApplication;

/**
 * V3-24 (§2.4b): the application MUST NOT start when initialization is invalid. Each test boots the
 * real app (full Spring context, isolated V3-25 test profile) pointed at a bad initial-state
 * configuration and asserts startup fails with a clear, diagnostic error rather than coming up in a
 * half-initialized state. Component behavior is already covered by the parser/executor and
 * handshake-runner unit tests; this verifies the wiring at the {@code SpringApplication} boot boundary
 * — an invalid initial-state runner ({@code @Order(100)}) throws and aborts {@code SpringApplication.run}.
 */
@DisplayName("Invalid initialization halts startup (V3-24)")
class InitializationStartupFailureTest {

    /**
     * Boots the full app with the given initial-state file path. Returns the context so the caller can
     * close it; when startup fails (the cases under test) {@code run} throws before returning.
     * Overrides are passed as command-line args so they outrank {@code application.yml} and the
     * Surefire system-property pins; {@code server.port=0} avoids clashing with a running instance.
     */
    private static ConfigurableApplicationContext boot(String initialStateFilePath) {
        return new SpringApplicationBuilder(TicketingApplication.class).run(
                "--server.port=0",
                "--spring.profiles.active=test",
                "--ticketing.seed.enabled=false",
                "--ticketing.bootstrap.dataset=initial-state-file",
                "--ticketing.startup.initialize-platform=false",
                "--ticketing.initial-state.file=" + initialStateFilePath);
    }

    private static Path tempStateFile(String content) throws IOException {
        Path file = Files.createTempFile("v3-init-invalid", ".txt");
        Files.writeString(file, content);
        file.toFile().deleteOnExit();
        return file;
    }

    @Test
    @DisplayName("Illegal state-file step (login for an unregistered user) halts startup")
    void GivenIllegalStateStep_WhenAppBoots_ThenStartupFails() throws IOException {
        Path file = tempStateFile("login(ghost, secret1);\n");
        assertThatThrownBy(() -> boot(file.toString()).close())
                .hasMessageContaining("login for 'ghost'");
    }

    @Test
    @DisplayName("Malformed state file (parse error) halts startup, reporting the line")
    void GivenParseError_WhenAppBoots_ThenStartupFails() throws IOException {
        // Missing the terminating ';'.
        Path file = tempStateFile("guest-registration(rina, rina@example.com, secret1)\n");
        assertThatThrownBy(() -> boot(file.toString()).close())
                .hasMessageContaining("[PARSE ERROR]")
                .hasMessageContaining("missing ';'");
    }

    @Test
    @DisplayName("Non-readable configured initial-state file halts startup")
    void GivenUnreadableFile_WhenAppBoots_ThenStartupFails() {
        assertThatThrownBy(() -> boot("/nonexistent-dir-v3-24/missing-initial-state.txt").close())
                .hasMessageContaining("not readable");
    }
}
