package com.ticketing.domain.gateway;

public record PaymentResult(boolean success, String transactionId, String errorMessage) {
    public static PaymentResult successful(String transactionId) {
        return new PaymentResult(true, transactionId, null);
    }
    public static PaymentResult failed(String errorMessage) {
        return new PaymentResult(false, null, errorMessage);
    }
}
