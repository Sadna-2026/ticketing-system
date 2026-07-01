package com.ticketing.application.initialization;

/**
 * Halts application startup with a framed message (configuration or initialization).
 * Logged once by Spring Boot; stack trace suppressed for a clean {@code error.log}.
 */
public class StartupHaltException extends IllegalStateException {

    private static final String BORDER = "*************************************************************";

    public StartupHaltException(String framedMessage) {
        super(framedMessage);
    }

    public static void failInitialization(String detail) {
        throw new StartupHaltException(framed("INITIALIZATION ERROR", detail));
    }

    /** Used by initial-state parser/executor in this package. */
    static String framedInitializationMessage(String detail) {
        return framed("INITIALIZATION ERROR", detail);
    }

    private static String framed(String kind, String detail) {
        return """
                
                %s
                  APPLICATION STARTUP HALTED — %s
                  %s
                %s
                """.formatted(BORDER, kind, detail, BORDER);
    }

    @Override
    public synchronized Throwable fillInStackTrace() {
        return this;
    }
}
