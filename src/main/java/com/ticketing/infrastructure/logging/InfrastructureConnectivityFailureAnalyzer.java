package com.ticketing.infrastructure.logging;

import org.springframework.boot.diagnostics.FailureAnalysis;
import org.springframework.boot.diagnostics.FailureAnalyzer;

/**
 * Startup failure reporting for database / external connectivity problems — clear console
 * output matching the framed summary written to {@code logs/error.log}.
 */
public class InfrastructureConnectivityFailureAnalyzer implements FailureAnalyzer {

    @Override
    public FailureAnalysis analyze(Throwable failure) {
        String description = InfrastructureErrorMessages.shortDescription(failure);
        if (description == null) {
            return null;
        }
        return new FailureAnalysis(
                description,
                InfrastructureErrorMessages.recommendedAction(failure),
                failure);
    }
}
