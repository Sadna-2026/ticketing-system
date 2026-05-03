package com.ticketing.domain.gateway;

public record RefundResult(boolean success, String refundTransactionId, String errorMessage) {
    public static RefundResult successful(String transactionId) {
        return new RefundResult(true, transactionId, null);
    }
    public static RefundResult failed(String errorMessage) {
        return new RefundResult(false, null, errorMessage);
    }
}
