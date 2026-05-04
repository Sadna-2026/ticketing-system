package com.ticketing.infrastructure.Interface;

import com.ticketing.domain.gateway.PaymentDetails;
import com.ticketing.domain.gateway.PaymentResult;
import com.ticketing.domain.gateway.RefundResult;

public interface IPaymentGateway {
    PaymentResult charge(double amount, PaymentDetails details);
    RefundResult refund(String transactionId, double amount);
}

