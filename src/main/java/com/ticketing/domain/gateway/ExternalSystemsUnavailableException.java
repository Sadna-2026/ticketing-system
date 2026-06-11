package com.ticketing.domain.gateway;

/**
 * Raised when a call to the external systems endpoint cannot be completed (endpoint unreachable,
 * transport error, or non-success HTTP status), or when the endpoint is not configured.
 *
 * <p>Lives at the gateway abstraction level so the application/domain layer can react to an
 * integration failure without depending on the concrete HTTP client.
 */
public class ExternalSystemsUnavailableException extends RuntimeException {

    public ExternalSystemsUnavailableException(String message) {
        super(message);
    }

    public ExternalSystemsUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
