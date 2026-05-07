package com.ticketing.application;

public record MarketInitializationResponse(boolean success, String message) {

    public static MarketInitializationResponse success(String message) {
        return new MarketInitializationResponse(true, message);
    }

    public static MarketInitializationResponse failure(String message) {
        return new MarketInitializationResponse(false, message);
    }
}
