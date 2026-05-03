package com.ticketing.domain.event;

public record PolicyResult(boolean allowed, String errorCode, String reason) {
    public static PolicyResult success() {
        return new PolicyResult(true, null, null);
    }

    public static PolicyResult failure(String errorCode, String reason) {
        return new PolicyResult(false, errorCode, reason);
    }
}