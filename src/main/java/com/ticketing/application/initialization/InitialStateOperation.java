package com.ticketing.application.initialization;

import java.util.List;

/**
 * Immutable value describing a single use-case call parsed from an initial-state file
 * (V3-14). It carries the operation name and its arguments in declaration order.
 *
 * <p>This is a plain value object — <strong>not</strong> a JPA entity and not a Spring
 * bean. It deliberately knows nothing about how the operation is executed; the executor
 * (issue #273) maps {@link #name()} to a use case and feeds {@link #args()} to it.
 *
 * @param name the operation / use-case name (e.g. {@code "login"}), never blank
 * @param args the arguments in order; quoted arguments have had their surrounding
 *             double quotes stripped, unquoted arguments are trimmed. Never {@code null};
 *             empty for a zero-arg call. The list is unmodifiable.
 * @param line the 1-based line number in the source file where this operation started
 * @param sourceFile the path/name of the source file, for diagnostics
 */
public record InitialStateOperation(String name, List<String> args, int line, String sourceFile) {

    public InitialStateOperation {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Operation name must not be blank");
        }
        if (args == null) {
            throw new IllegalArgumentException("Operation args must not be null");
        }
        args = List.copyOf(args);
    }
}
