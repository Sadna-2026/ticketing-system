package com.ticketing.application.services;

import com.ticketing.domain.admin.Admin;
import com.ticketing.domain.admin.IAdminRepository;
import com.ticketing.domain.gateway.IPaymentGateway;
import com.ticketing.domain.gateway.ITicketSupplyGateway;
import com.ticketing.domain.system.StartupConfiguration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PlatformInitializationService {

    private static final Logger log = LoggerFactory.getLogger(PlatformInitializationService.class);

    private final IAdminRepository adminRepository;
    private final IPaymentGateway paymentGateway;
    private final ITicketSupplyGateway supplyGateway;
    private final StartupConfiguration startupConfiguration;
    private final List<String> eventLog = new ArrayList<>();

    public PlatformInitializationService(
            IAdminRepository adminRepository,
            IPaymentGateway paymentGateway,
            ITicketSupplyGateway supplyGateway,
            StartupConfiguration startupConfiguration
    ) {
        if (adminRepository == null || paymentGateway == null || supplyGateway == null || startupConfiguration == null) {
            throw new IllegalArgumentException("All dependencies are required");
        }
        this.adminRepository = adminRepository;
        this.paymentGateway = paymentGateway;
        this.supplyGateway = supplyGateway;
        this.startupConfiguration = startupConfiguration;
    }

    public synchronized InitializationResult initialize() {
        recordEvent("Platform initialization started");

        InitializationResult validation = validateStartupConfiguration();
        if (!validation.success()) {
            return halt(validation.message());
        }

        if (!startupConfiguration.clearingServiceConfigured() || !isPaymentGatewayReachable()) {
            return halt("Unable to connect to clearing service");
        }

        if (!startupConfiguration.supplyServiceConfigured() || !isSupplyGatewayReachable()) {
            return halt("Unable to connect to supply service");
        }

        registerSystemAdmin();
        startupConfiguration.activate();
        recordEvent("Platform initialization succeeded");
        log.info("Platform initialized successfully");
        return InitializationResult.success("Platform initialized successfully");
    }

    public boolean isActive() {
        return startupConfiguration.isActive();
    }

    public List<String> eventLog() {
        return Collections.unmodifiableList(eventLog);
    }

    private InitializationResult validateStartupConfiguration() {
        if (startupConfiguration.adminUsername() == null || startupConfiguration.adminUsername().isBlank()) {
            return InitializationResult.failure("Invalid admin credentials");
        }
        if (startupConfiguration.adminEmail() == null
                || startupConfiguration.adminEmail().isBlank()
                || !startupConfiguration.adminEmail().contains("@")) {
            return InitializationResult.failure("Invalid admin credentials");
        }
        if (startupConfiguration.adminPassword() == null || startupConfiguration.adminPassword().length() < 6) {
            return InitializationResult.failure("Invalid admin credentials");
        }
        return InitializationResult.success("Startup configuration valid");
    }

    private void registerSystemAdmin() {
        if (adminRepository.existsByUsername(startupConfiguration.adminUsername())) {
            return;
        }

        adminRepository.save(new Admin(
                UUID.randomUUID(),
                startupConfiguration.adminUsername().trim(),
                startupConfiguration.adminEmail().trim().toLowerCase()
        ));
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

    private InitializationResult halt(String message) {
        recordEvent("Platform initialization failed: " + message);
        log.warn("Platform initialization failed: {}", message);
        return InitializationResult.failure(message);
    }

    private void recordEvent(String message) {
        eventLog.add(message);
    }

    public record InitializationResult(boolean success, String message) {
        public static InitializationResult success(String message) {
            return new InitializationResult(true, message);
        }

        public static InitializationResult failure(String message) {
            return new InitializationResult(false, message);
        }
    }
}
