package com.ticketing.application.initialization;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.OrderUtils;

import com.ticketing.infrastructure.gateway.ExternalSystemsHandshakeRunner;

/**
 * Pins the startup {@code ApplicationRunner} ordering: external handshake → platform
 * initialization + dev seed → initial-state replay. {@link DevSeedDataInitializer} must carry an
 * explicit {@code @Order} below {@link InitialStateRunner}'s, otherwise (with no annotation) it
 * would default to lowest precedence and run *after* the initial-state replay — replaying a
 * configured state file before the platform is initialized.
 */
@DisplayName("Startup ApplicationRunner ordering")
class StartupRunnerOrderingTest {

    @Test
    void GivenStartupRunners_ThenOrderIsExternalThenSeedThenInitialState() {
        Integer external = OrderUtils.getOrder(ExternalSystemsHandshakeRunner.class);
        Integer seed = OrderUtils.getOrder(DevSeedDataInitializer.class);
        Integer initialState = OrderUtils.getOrder(InitialStateRunner.class);

        assertEquals(Integer.valueOf(0), external, "ExternalSystemsHandshakeRunner should run first");
        assertEquals(Integer.valueOf(50), seed,
                "DevSeedDataInitializer (platform init + seed) must run before the initial-state replay");
        assertEquals(Integer.valueOf(100), initialState,
                "InitialStateRunner replays last, on an initialized platform");

        assertTrue(external < seed && seed < initialState,
                "Order must be external handshake -> platform init/seed -> initial-state replay");
    }
}
