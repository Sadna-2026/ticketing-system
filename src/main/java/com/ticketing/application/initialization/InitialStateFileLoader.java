package com.ticketing.application.initialization;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Loads initial-state file content from a filesystem path or a {@code classpath:} resource.
 */
public final class InitialStateFileLoader {

    private static final String CLASSPATH_PREFIX = "classpath:";

    private InitialStateFileLoader() {
    }

    public static String load(String location) {
        if (location == null || location.isBlank()) {
            StartupHaltException.failInitialization("ticketing.initial-state.file is blank");
        }
        String trimmed = location.trim();
        if (trimmed.regionMatches(true, 0, CLASSPATH_PREFIX, 0, CLASSPATH_PREFIX.length())) {
            return loadClasspath(trimmed.substring(CLASSPATH_PREFIX.length()).trim());
        }
        Path path = Path.of(trimmed);
        if (!Files.isReadable(path)) {
            StartupHaltException.failInitialization("initial-state file not readable: " + path.toAbsolutePath());
        }
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException e) {
            StartupHaltException.failInitialization(
                    "failed to read initial-state file " + path.toAbsolutePath() + ": " + e.getMessage());
            return null;
        }
    }

    private static String loadClasspath(String resourcePath) {
        String normalized = resourcePath.startsWith("/") ? resourcePath.substring(1) : resourcePath;
        try (InputStream in = InitialStateFileLoader.class.getClassLoader().getResourceAsStream(normalized)) {
            if (in == null) {
                StartupHaltException.failInitialization("classpath initial-state resource not found: " + normalized);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            StartupHaltException.failInitialization(
                    "failed to read classpath initial-state resource " + normalized + ": " + e.getMessage());
            return null;
        }
    }
}
