package com.ticketing.infrastructure.gateway;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import com.ticketing.application.initialization.StartupHaltException;
import com.ticketing.domain.gateway.IExternalSystemsClient;

/**
 * Runs the external systems startup handshake (V3-16, integrity rule: a live payment + ticket
 * issuance connection must exist after initialization).
 *
 * <p>Gated on {@code ticketing.external.base-url}: when it is blank (the default), the handshake is
 * skipped so local dev, stub gateways and the test suite are unaffected. When the URL is configured,
 * a failed handshake halts startup with a clear message. Runs early ({@link Order}) before platform
 * initialization / seeding.
 */
@Component
@Order(0)
public class ExternalSystemsHandshakeRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ExternalSystemsHandshakeRunner.class);

    private final String baseUrl;
    private final IExternalSystemsClient externalSystemsClient;

    public ExternalSystemsHandshakeRunner(
            @Value("${ticketing.external.base-url:}") String baseUrl,
            IExternalSystemsClient externalSystemsClient) {
        this.baseUrl = baseUrl == null ? "" : baseUrl.trim();
        this.externalSystemsClient = externalSystemsClient;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (baseUrl.isBlank()) {
            log.info("External systems base URL not configured (ticketing.external.base-url); "
                    + "skipping startup handshake.");
            return;
        }
        log.info("Performing external systems startup handshake against {} ...", baseUrl);
        if (!externalSystemsClient.handshake()) {
            StartupHaltException.failInitialization(
                    "Cannot reach the external payment and ticket-issuance service at " + baseUrl + ". "
                            + "Because ticketing.external.base-url is set, startup sends a handshake "
                            + "(POST with action_type=handshake) to verify checkout and ticket delivery will work. "
                            + "Ensure that service is running and the URL is correct, or leave "
                            + "ticketing.external.base-url empty to use stub gateways in local dev.");
        }
        log.info("External systems handshake OK.");
    }
}
