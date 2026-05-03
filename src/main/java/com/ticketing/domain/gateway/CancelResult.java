package com.ticketing.domain.gateway;

public record CancelResult(boolean success, String errorMessage) {
    public static CancelResult successful() {
        return new CancelResult(true, null);
    }
    public static CancelResult failed(String errorMessage) {
        return new CancelResult(false, errorMessage);
    }
}
