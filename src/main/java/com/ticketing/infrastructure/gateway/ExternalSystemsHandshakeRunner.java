package com.ticketing.infrastructure.gateway;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import com.ticketing.domain.gateway.IExternalSystemsClient;

/**
 * Runs the external systems startup handshake (V3-16, integrity rule: a live payment + ticket
 * issuance connection must exist after initialization).
 *
 * <p>Gated on {@code ticketing.external.base-url}: when it is blank (the default), the handshake is
 * skipped so local dev, the existing stub gateways and the test suite are unaffected. When the URL
 * is configured, a failed handshake halts startup with a clear message. Runs early ({@link Order})
 * so connectivity is verified before platform initialization / seeding.
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
            throw new IllegalStateException(
                    "External systems handshake failed at startup. Verify ticketing.external.base-url ("
                    + baseUrl + ") and that the external payment/ticket endpoint is reachable.");
        }
        log.info("External systems handshake OK.");
    }
}
