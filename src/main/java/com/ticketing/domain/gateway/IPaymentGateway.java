package com.ticketing.domain.gateway;

import java.math.BigDecimal;

public interface IPaymentGateway {
    /**
     * Optional startup health check. Existing gateways are considered reachable unless overridden.
     */
    default boolean isReachable() {
        return true;
    }

    PaymentResult charge(BigDecimal finalAmount, PaymentDetails details);
    RefundResult refund(String transactionId, double amount);
}

