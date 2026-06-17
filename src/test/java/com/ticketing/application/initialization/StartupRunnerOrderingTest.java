package com.ticketing.application.initialization;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.OrderUtils;

import com.ticketing.infrastructure.gateway.ExternalSystemsHandshakeRunner;

/**
 * Pins the startup {@code ApplicationRunner} ordering: external handshake → platform
 * initialization + data bootstrap.
 */
@DisplayName("Startup ApplicationRunner ordering")
class StartupRunnerOrderingTest {

    @Test
    void GivenStartupRunners_ThenOrderIsExternalThenBootstrap() {
        Integer external = OrderUtils.getOrder(ExternalSystemsHandshakeRunner.class);
        Integer bootstrap = OrderUtils.getOrder(DataBootstrapRunner.class);

        assertEquals(Integer.valueOf(0), external, "ExternalSystemsHandshakeRunner should run first");
        assertEquals(Integer.valueOf(50), bootstrap,
                "DataBootstrapRunner runs platform init then data bootstrap");

        assertTrue(external < bootstrap,
                "Order must be external handshake -> platform init + data bootstrap");
    }
}
