package  com.ticketing.infrastructure.gateway;

import com.ticketing.domain.gateway.IPaymentGateway;
import com.ticketing.domain.gateway.PaymentDetails;
import com.ticketing.domain.gateway.PaymentResult;
import com.ticketing.domain.gateway.RefundResult;

import java.math.BigDecimal;
import java.util.UUID;

public class StubPaymentGateway implements IPaymentGateway {
    
    private boolean shouldFail = false;

    public void setShouldFail(boolean shouldFail) {
        this.shouldFail = shouldFail;
    }

    @Override
    public boolean isReachable() {
        return !shouldFail;
    }

    @Override
    public PaymentResult charge(BigDecimal amount, PaymentDetails details) {
        if (shouldFail) {
            return PaymentResult.failed("Payment declined by issuer.");
        }
        return PaymentResult.successful("TXN-" + UUID.randomUUID().toString().substring(0, 8));
    }

    @Override
    public RefundResult refund(String transactionId, double amount) {
        if (shouldFail) {
            return RefundResult.failed("Refund failed. Transaction not found or unsettled.");
        }
        return RefundResult.successful("REF-" + UUID.randomUUID().toString().substring(0, 8));
    }
}


