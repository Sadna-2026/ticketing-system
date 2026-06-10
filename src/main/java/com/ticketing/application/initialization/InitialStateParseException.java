package com.ticketing.application.initialization;

/**
 * Thrown when an initial-state file cannot be parsed: an unterminated call, a missing
 * parenthesis, an unbalanced quote, or any other malformed construct (V3-14).
 *
 * <p>The message includes the 1-based line number and a snippet of the offending text
 * so the operator can locate the problem in the source file.
 */
public class InitialStateParseException extends RuntimeException {

    public InitialStateParseException(String message) {
        super(message);
    }
}
