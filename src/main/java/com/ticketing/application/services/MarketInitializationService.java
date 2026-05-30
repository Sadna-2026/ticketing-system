package com.ticketing.application.services;

import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ticketing.application.MarketInitializationResponse;
import com.ticketing.application.auth.ISessionTokenService;
import com.ticketing.domain.admin.Admin;
import com.ticketing.domain.admin.IAdminRepository;
import com.ticketing.domain.gateway.IPaymentGateway;
import com.ticketing.domain.gateway.ITicketSupplyGateway;
import com.ticketing.domain.system.StartupConfiguration;

@org.springframework.stereotype.Service
public class MarketInitializationService {

    private static final Logger log = LoggerFactory.getLogger(MarketInitializationService.class);
    private static final String ADMIN_PERMISSION = "SYSTEM_ADMIN";

    private final IAdminRepository adminRepository;
    private final ISessionTokenService sessionTokenService;
    private final IPaymentGateway paymentGateway;
    private final ITicketSupplyGateway supplyGateway;
    private final StartupConfiguration startupConfiguration;

    public MarketInitializationService(
            IAdminRepository adminRepository,
            ISessionTokenService sessionTokenService,
            IPaymentGateway paymentGateway,
            ITicketSupplyGateway supplyGateway,
            StartupConfiguration startupConfiguration
    ) {
        if (adminRepository == null || sessionTokenService == null
                || paymentGateway == null || supplyGateway == null || startupConfiguration == null) {
            log.error("Market initialization failed: one or more dependencies are null");
            throw new IllegalArgumentException("All dependencies are required");
        }
        this.adminRepository = adminRepository;
        this.sessionTokenService = sessionTokenService;
        this.paymentGateway = paymentGateway;
        this.supplyGateway = supplyGateway;
        this.startupConfiguration = startupConfiguration;
    }

    /**
     * Opens trading only after platform, admin, and external-service checks pass.
     */
    public synchronized MarketInitializationResponse openMarket(String adminToken) {
        if (!startupConfiguration.isActive()) {
            log.error("Market initialization failed: platform is not initialized.");
            return fail("Platform is not initialized.");
        }

        Admin admin = authenticateSystemAdmin(adminToken);
        if (admin == null) {
            log.error("Market initialization failed: system admin permission required.");
            return fail("System admin permission required.");
        }

        if (!startupConfiguration.clearingServiceConfigured() || !isPaymentGatewayReachable()) {
            log.error("Market initialization failed: payment service unavailable.");
            return fail("Payment service unavailable.");
        }

        if (!startupConfiguration.supplyServiceConfigured() || !isSupplyGatewayReachable()) {
            log.error("Market initialization failed: supply service unavailable.");
            return fail("Supply service unavailable.");
        }

        startupConfiguration.openMarket();
        log.info("Market opened by system admin: {}", admin.getId());
        return MarketInitializationResponse.success("Market opened successfully.");
    }

    public boolean isMarketOpen() {
        return startupConfiguration.isMarketOpen();
    }

    private Admin authenticateSystemAdmin(String token) {
        if (!sessionTokenService.isValid(token)) {
            log.warn("Market initialization failed: invalid admin token");
            return null;
        }

        UUID adminId = sessionTokenService.extractMemberId(token);
        Set<String> permissions = sessionTokenService.extractPermissions(token);
        if (adminId == null || permissions == null || !permissions.contains(ADMIN_PERMISSION)) {
            log.warn("Market initialization failed: non-admin token");
            return null;
        }

        if (adminRepository.findAll().isEmpty()) {
            log.warn("Market initialization failed: no system admin in DB");
            return null;
        }

        return adminRepository.findById(adminId).orElseGet(() -> {
            log.warn("Market initialization failed: admin not found {}", adminId);
            return null;
        });
    }

    private boolean isPaymentGatewayReachable() {
        try {
            return paymentGateway.isReachable();
        } catch (RuntimeException ex) {
            return false;
        }
    }

    private boolean isSupplyGatewayReachable() {
        try {
            return supplyGateway.isReachable();
        } catch (RuntimeException ex) {
            return false;
        }
    }

    private MarketInitializationResponse fail(String message) {
        log.warn("Market initialization failed: {}", message);
        return MarketInitializationResponse.failure(message);
    }
}

