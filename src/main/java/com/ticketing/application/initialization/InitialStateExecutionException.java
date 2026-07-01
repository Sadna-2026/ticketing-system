package com.ticketing.application.initialization;

/**
 * Thrown when an initial-state file cannot be executed against the application layer
 * (V3-15): an unknown operation name, the wrong number of arguments for an operation,
 * a reference to an unbound token symbol, or an underlying use case that fails.
 *
 * <p>The message names the failing operation by its index (0-based) and name so the
 * operator can locate the problem in the source file. Initialization is all-or-nothing:
 * any such failure aborts the whole run.
 */
public class InitialStateExecutionException extends RuntimeException {

    public InitialStateExecutionException(String message) {
        super(message);
    }

    public InitialStateExecutionException(String message, Throwable cause) {
        super(message, cause);
    }

    @Override
    public synchronized Throwable fillInStackTrace() {
        return this;
    }
}
