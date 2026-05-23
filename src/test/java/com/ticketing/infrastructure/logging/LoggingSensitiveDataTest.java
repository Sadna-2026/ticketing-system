package com.ticketing.infrastructure.logging;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Logging sensitive-data safety")
class LoggingSensitiveDataTest {

    private static final Pattern LOG_CALL = Pattern.compile(
            "\\b(?:log\\.(?:info|warn|error)|logger\\.log)\\s*\\(",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern UNSAFE_IDENTIFIER = Pattern.compile(
            "\\b(?:password|guestToken|sessionToken|adminToken|token|paymentDetails|customerInfo|card|cvv)\\b",
            Pattern.CASE_INSENSITIVE);

    @Test
    void GivenApplicationSource_WhenLoggingStatementsAreAudited_ThenNoSensitiveVariablesAreLogged() throws Exception {
        Path applicationRoot = Path.of("src", "main", "java", "com", "ticketing", "application");
        assertTrue(Files.isDirectory(applicationRoot), "application source directory must exist");

        List<String> violations = Files.walk(applicationRoot)
                .filter(path -> path.toString().endsWith(".java"))
                .flatMap(path -> unsafeLogCalls(path).stream())
                .toList();

        assertFalse(violations.isEmpty(), "sanity check: test should inspect at least one logging statement");
        assertTrue(violations.stream().noneMatch(v -> v.startsWith("VIOLATION:")),
                () -> "Sensitive values must not be passed to logging calls:\n" + String.join("\n", violations));
    }

    private static List<String> unsafeLogCalls(Path path) {
        try {
            String source = Files.readString(path);
            Matcher matcher = LOG_CALL.matcher(source);
            java.util.ArrayList<String> findings = new java.util.ArrayList<>();
            while (matcher.find()) {
                int start = matcher.start();
                int end = findCallEnd(source, matcher.end());
                if (end < 0) {
                    continue;
                }
                String call = source.substring(start, end);
                String scrubbed = stripStringLiterals(call);
                if (UNSAFE_IDENTIFIER.matcher(scrubbed).find()) {
                    int line = 1 + (int) source.substring(0, start).chars().filter(ch -> ch == '\n').count();
                    findings.add("VIOLATION:" + path + ":" + line + " -> " + call.replace('\n', ' '));
                } else {
                    findings.add("OK:" + path);
                }
            }
            return findings;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static int findCallEnd(String source, int from) {
        int depth = 1;
        boolean inString = false;
        boolean escaped = false;
        for (int i = from; i < source.length(); i++) {
            char c = source.charAt(i);
            if (inString) {
                if (escaped) {
                    escaped = false;
                } else if (c == '\\') {
                    escaped = true;
                } else if (c == '"') {
                    inString = false;
                }
                continue;
            }
            if (c == '"') {
                inString = true;
            } else if (c == '(') {
                depth++;
            } else if (c == ')') {
                depth--;
                if (depth == 0) {
                    return i + 1;
                }
            }
        }
        return -1;
    }

    private static String stripStringLiterals(String input) {
        return input.replaceAll("\"(?:\\\\.|[^\"\\\\])*\"", "\"\"");
    }
}
