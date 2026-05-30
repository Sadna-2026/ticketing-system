package com.ticketing.domain.gateway;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;

class GatewayResultTest {

    @Test
    void GivenGatewayInterfaces_WhenDefaultHealthCheckUsed_ThenReachable() {
        IPaymentGateway paymentGateway = new IPaymentGateway() {
            @Override
            public PaymentResult charge(BigDecimal finalAmount, PaymentDetails details) {
                return PaymentResult.successful("tx-1");
            }

            @Override
            public RefundResult refund(String transactionId, double amount) {
                return RefundResult.successful("refund-1");
            }
        };

        ITicketSupplyGateway ticketSupplyGateway = new ITicketSupplyGateway() {
            @Override
            public SupplyResult issueTickets(List<TicketRequest> tickets, CustomerInfo customer) {
                return SupplyResult.successful(List.of("ticket-1"));
            }

            @Override
            public CancelResult cancelTickets(List<String> ticketCodes) {
                return CancelResult.successful();
            }
        };

        assertAll(
                () -> assertTrue(paymentGateway.isReachable()),
                () -> assertTrue(ticketSupplyGateway.isReachable())
        );
    }

    @Test
    void GivenGatewayResultFactories_WhenCalled_ThenSuccessAndFailurePayloadsAreCorrect() {
        PaymentResult paymentSuccess = PaymentResult.successful("tx-1");
        PaymentResult paymentFailure = PaymentResult.failed("payment failed");
        RefundResult refundSuccess = RefundResult.successful("refund-1");
        RefundResult refundFailure = RefundResult.failed("refund failed");
        SupplyResult supplySuccess = SupplyResult.successful(List.of("ticket-1", "ticket-2"));
        SupplyResult supplyFailure = SupplyResult.failed("supply failed");
        CancelResult cancelSuccess = CancelResult.successful();
        CancelResult cancelFailure = CancelResult.failed("cancel failed");

        assertAll(
                () -> assertTrue(paymentSuccess.success()),
                () -> assertEquals("tx-1", paymentSuccess.transactionId()),
                () -> assertNull(paymentSuccess.errorMessage()),
                () -> assertFalse(paymentFailure.success()),
                () -> assertNull(paymentFailure.transactionId()),
                () -> assertEquals("payment failed", paymentFailure.errorMessage()),
                () -> assertTrue(refundSuccess.success()),
                () -> assertEquals("refund-1", refundSuccess.refundTransactionId()),
                () -> assertFalse(refundFailure.success()),
                () -> assertEquals("refund failed", refundFailure.errorMessage()),
                () -> assertTrue(supplySuccess.success()),
                () -> assertEquals(List.of("ticket-1", "ticket-2"), supplySuccess.issuedTicketCodes()),
                () -> assertFalse(supplyFailure.success()),
                () -> assertTrue(supplyFailure.issuedTicketCodes().isEmpty()),
                () -> assertEquals("supply failed", supplyFailure.errorMessage()),
                () -> assertTrue(cancelSuccess.success()),
                () -> assertNull(cancelSuccess.errorMessage()),
                () -> assertFalse(cancelFailure.success()),
                () -> assertEquals("cancel failed", cancelFailure.errorMessage())
        );
    }
}
