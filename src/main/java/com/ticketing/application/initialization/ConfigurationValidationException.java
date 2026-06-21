package com.ticketing.application.initialization;

public class ConfigurationValidationException extends IllegalStateException {

    private final String key;
    private final String badValue;
    private final String expectedForm;

    public ConfigurationValidationException(String message, String key, String badValue, String expectedForm) {
        super(message);
        this.key = key;
        this.badValue = badValue;
        this.expectedForm = expectedForm;
    }

    public String getKey() {
        return key;
    }

    public String getBadValue() {
        return badValue;
    }

    public String getExpectedForm() {
        return expectedForm;
    }

    @Override
    public synchronized Throwable fillInStackTrace() {
        // Suppress stack trace to prevent leaking it to the user
        return this;
    }
}
