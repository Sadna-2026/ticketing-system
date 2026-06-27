package com.ticketing.infrastructure.logging;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

final class LogFileTestSupport {

    static final Path EVENT_LOG = Path.of("logs", "event.log");
    static final Path ERROR_LOG = Path.of("logs", "error.log");

    private LogFileTestSupport() {
    }

    static boolean waitForFileToContain(Path path, String marker, Duration timeout) throws Exception {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            if (fileContains(path, marker)) {
                return true;
            }
            Thread.sleep(50);
        }
        return false;
    }

    static boolean fileContains(Path path, String marker) throws Exception {
        return Files.exists(path) && Files.readString(path).contains(marker);
    }

    static boolean fileContainsLineWith(Path path, String marker, String level) throws Exception {
        return Files.exists(path)
                && Files.readAllLines(path).stream()
                        .anyMatch(line -> line.contains(marker) && line.contains(level));
    }
}
