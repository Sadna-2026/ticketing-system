package com.ticketing.application.initialization;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
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
            throw new IllegalArgumentException("initial-state file location is required");
        }
        String trimmed = location.trim();
        if (trimmed.regionMatches(true, 0, CLASSPATH_PREFIX, 0, CLASSPATH_PREFIX.length())) {
            return loadClasspath(trimmed.substring(CLASSPATH_PREFIX.length()).trim());
        }
        Path path = Path.of(trimmed);
        if (!Files.isReadable(path)) {
            throw new IllegalStateException(
                    "Initial-state file is not readable: " + path.toAbsolutePath());
        }
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read initial-state file: " + path, e);
        }
    }

    private static String loadClasspath(String resourcePath) {
        String normalized = resourcePath.startsWith("/") ? resourcePath.substring(1) : resourcePath;
        try (InputStream in = InitialStateFileLoader.class.getClassLoader().getResourceAsStream(normalized)) {
            if (in == null) {
                throw new IllegalStateException("Classpath initial-state resource not found: " + normalized);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read classpath initial-state resource: " + normalized, e);
        }
    }
}
