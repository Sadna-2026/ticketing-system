package com.ticketing.infrastructure.Interface;

import java.math.BigDecimal;

import com.ticketing.domain.gateway.PaymentDetails;
import com.ticketing.domain.gateway.PaymentResult;
import com.ticketing.domain.gateway.RefundResult;

public interface IPaymentGateway {
    PaymentResult charge(BigDecimal finalAmount, PaymentDetails details);
    RefundResult refund(String transactionId);
}

