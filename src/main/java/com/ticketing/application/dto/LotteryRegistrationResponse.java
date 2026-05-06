package com.ticketing.application.dto;

import java.time.Instant;
import java.util.UUID;

public record LotteryRegistrationResponse(boolean success, String message, UUID lotteryEntryId, Instant registeredAt) {

    public static LotteryRegistrationResponse success(UUID lotteryEntryId, Instant registeredAt) {
        return new LotteryRegistrationResponse(true, "Lottery registration successful.", lotteryEntryId, registeredAt);
    }

    public static LotteryRegistrationResponse failure(String message) {
        return new LotteryRegistrationResponse(false, message, null, null);
    }
}
