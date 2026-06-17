package com.ticketing.domain.gateway;

import java.util.Map;

/**
 * Client for the single external systems endpoint (WSEP payment + ticket systems, reqs I.3/I.4).
 *
 * <p>The external API is a single HTTP {@code POST} endpoint whose behaviour is selected by an
 * {@code action_type} parameter; the base URL is configuration-driven. {@code handshake} returns
 * the literal {@code "OK"} when the systems are available.
 *
 * <p>Defined as an interface so that tests substitute a stub instead of calling the live endpoint.
 * The real payment / ticket gateways (V3-17 / V3-18) delegate their calls to this client.
 */
public interface IExternalSystemsClient {

    /** The {@code action_type} value that probes external systems availability. */
    String HANDSHAKE_ACTION = "handshake";

    /**
     * Performs a {@code POST} to the configured endpoint with the given parameters
     * (sent {@code application/x-www-form-urlencoded}) and returns the raw response body.
     *
     * @throws ExternalSystemsUnavailableException if the endpoint is unreachable or returns an error,
     *         or if the base URL is not configured.
     */
    String send(Map<String, String> params);

    /**
     * Runs the startup handshake ({@code action_type=handshake}).
     *
     * @return {@code true} only if the endpoint responds with {@code "OK"}; {@code false} on any
     *         non-{@code OK} response or communication failure (so callers can treat it as a health check).
     */
    boolean handshake();
}
