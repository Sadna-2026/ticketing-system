package com.ticketing.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.ticketing.domain.admin.Admin;
import com.ticketing.domain.admin.IAdminRepository;
import com.ticketing.domain.system.StartupConfiguration;
import com.ticketing.infrastructure.InMemoryAdminRepository;
import com.ticketing.infrastructure.InMemorySessionTokenRepository;
import com.ticketing.infrastructure.gateway.StubPaymentGateway;
import com.ticketing.infrastructure.gateway.StubTicketSupplyGateway;
import com.ticketing.infrastructure.init.PlatformInitializationService;

class MarketInitializationServiceTest {

    private IAdminRepository adminRepository;
    private SessionTokenService sessionTokenService;
    private StubPaymentGateway paymentGateway;
    private StubTicketSupplyGateway supplyGateway;
    private StartupConfiguration startupConfiguration;
    private MarketInitializationService marketInitializationService;

    @BeforeEach
    void setUp() {
        adminRepository = new InMemoryAdminRepository();
        sessionTokenService = new SessionTokenService(jwtSecret(), 120, new InMemorySessionTokenRepository());
        paymentGateway = new StubPaymentGateway();
        supplyGateway = new StubTicketSupplyGateway();
        startupConfiguration = new StartupConfiguration();
        marketInitializationService = new MarketInitializationService(
                adminRepository,
                sessionTokenService,
                paymentGateway,
                supplyGateway,
                startupConfiguration
        );
    }

    @Test
    void GivenSystemAdmin_WhenOpenMarket_ThenMarketOpened() {
        String adminToken = initializePlatformAndAdminToken();

        MarketInitializationResponse response = marketInitializationService.openMarket(adminToken);

        assertTrue(response.success());
        assertTrue(marketInitializationService.isMarketOpen());
        assertEquals("Market opened successfully.", response.message());
    }

    @Test
    void GivenNonAdmin_WhenOpenMarket_ThenRejected() {
        initializePlatformAndAdminToken();
        String nonAdminToken = sessionTokenService.generateMemberToken(
                UUID.randomUUID(),
                UUID.randomUUID(),
                Set.of()
        );

        MarketInitializationResponse response = marketInitializationService.openMarket(nonAdminToken);

        assertFalse(response.success());
        assertFalse(marketInitializationService.isMarketOpen());
        assertEquals("System admin permission required.", response.message());
    }

    @Test
    void GivenUnavailablePaymentService_WhenOpenMarket_ThenMarketStaysClosed() {
        String adminToken = initializePlatformAndAdminToken();
        paymentGateway.setShouldFail(true);

        MarketInitializationResponse response = marketInitializationService.openMarket(adminToken);

        assertFalse(response.success());
        assertFalse(marketInitializationService.isMarketOpen());
        assertEquals("Payment service unavailable.", response.message());
    }

    @Test
    void GivenUnavailableSupplyService_WhenOpenMarket_ThenMarketStaysClosed() {
        String adminToken = initializePlatformAndAdminToken();
        supplyGateway.setShouldFail(true);

        MarketInitializationResponse response = marketInitializationService.openMarket(adminToken);

        assertFalse(response.success());
        assertFalse(marketInitializationService.isMarketOpen());
        assertEquals("Supply service unavailable.", response.message());
    }

    @Test
    void GivenNoAdminInDatabase_WhenOpenMarket_ThenCannotOpen() {
        startupConfiguration.activate();
        String adminToken = sessionTokenService.generateMemberToken(
                UUID.randomUUID(),
                UUID.randomUUID(),
                Set.of("SYSTEM_ADMIN")
        );

        MarketInitializationResponse response = marketInitializationService.openMarket(adminToken);

        assertFalse(response.success());
        assertFalse(marketInitializationService.isMarketOpen());
        assertEquals("System admin permission required.", response.message());
    }

    private String initializePlatformAndAdminToken() {
        PlatformInitializationService platformInitializationService = new PlatformInitializationService(
                adminRepository,
                paymentGateway,
                supplyGateway,
                startupConfiguration
        );

        assertTrue(platformInitializationService.initialize().success());

        Admin admin = adminRepository.findByUsername(startupConfiguration.adminUsername()).orElseThrow();
        return sessionTokenService.generateMemberToken(
                UUID.randomUUID(),
                admin.getId(),
                Set.of("SYSTEM_ADMIN")
        );
    }

    private String jwtSecret() {
        return Base64.getEncoder().encodeToString(
                "01234567890123456789012345678901".getBytes(StandardCharsets.UTF_8)
        );
    }
}
