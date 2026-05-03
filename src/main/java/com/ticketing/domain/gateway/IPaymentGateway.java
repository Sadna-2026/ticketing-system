package com.ticketing.domain.gateway;

public interface IPaymentGateway {
    PaymentResult charge(double amount, PaymentDetails details);
    RefundResult refund(String transactionId, double amount);
}
