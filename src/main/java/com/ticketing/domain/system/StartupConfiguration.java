package com.ticketing.domain.system;

/**
 * Minimal V1 startup settings used to bootstrap the platform.
 */
public class StartupConfiguration {

    private final String adminUsername;
    private final String adminEmail;
    private final String adminPassword;
    private final boolean clearingServiceConfigured;
    private final boolean supplyServiceConfigured;
    private boolean active;
    private boolean marketOpen;

    public StartupConfiguration() {
        this("admin", "admin@ticketing.local", "admin123", true, true);
    }

    public StartupConfiguration(
            String adminUsername,
            String adminEmail,
            String adminPassword,
            boolean clearingServiceConfigured,
            boolean supplyServiceConfigured
    ) {
        this.adminUsername = adminUsername;
        this.adminEmail = adminEmail;
        this.adminPassword = adminPassword;
        this.clearingServiceConfigured = clearingServiceConfigured;
        this.supplyServiceConfigured = supplyServiceConfigured;
    }

    public String adminUsername() {
        return adminUsername;
    }

    public String adminEmail() {
        return adminEmail;
    }

    public String adminPassword() {
        return adminPassword;
    }

    public boolean clearingServiceConfigured() {
        return clearingServiceConfigured;
    }

    public boolean supplyServiceConfigured() {
        return supplyServiceConfigured;
    }

    public boolean isActive() {
        return active;
    }

    public void activate() {
        this.active = true;
    }

    public boolean isMarketOpen() {
        return marketOpen;
    }

    public void openMarket() {
        this.marketOpen = true;
    }
}
