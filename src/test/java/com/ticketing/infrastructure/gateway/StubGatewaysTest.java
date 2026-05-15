package com.ticketing.infrastructure.gateway;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.ticketing.domain.gateway.CustomerInfo;
import com.ticketing.domain.gateway.PaymentDetails;
import com.ticketing.domain.gateway.PaymentResult;
import com.ticketing.domain.gateway.SupplyResult;
import com.ticketing.domain.gateway.TicketRequest;

class StubGatewaysTest {

    private StubPaymentGateway paymentGateway;
    private StubTicketSupplyGateway supplyGateway;

    @BeforeEach
    void setUp() {
        paymentGateway = new StubPaymentGateway();
        supplyGateway = new StubTicketSupplyGateway();
    }

    @Test
    void GivenSuccessMode_WhenProcessPayment_ThenApproved() {
        PaymentDetails details = new PaymentDetails(UUID.randomUUID(), UUID.randomUUID(), null, "buyer@test.com");
        PaymentResult result = paymentGateway.charge(new BigDecimal("150.00"), details);
        assertTrue(result.success());
        assertNotNull(result.transactionId());
    }

    @Test
    void GivenFailureMode_WhenProcessPayment_ThenDeclined() {
        paymentGateway.setShouldFail(true);
        PaymentDetails details = new PaymentDetails(UUID.randomUUID(), UUID.randomUUID(), null, "buyer@test.com");
        PaymentResult result = paymentGateway.charge(new BigDecimal("150.00"), details);
        assertFalse(result.success());
        assertNotNull(result.errorMessage());
    }

    @Test
    void GivenSuccessMode_WhenSupplyTickets_ThenSupplied() {
        List<TicketRequest> requests = List.of(new TicketRequest("evt_1", "tkt_1", "seat_A1"));
        CustomerInfo customer = new CustomerInfo("user_1", "test@test.com", "John Doe");
        
        SupplyResult result = supplyGateway.issueTickets(requests, customer);
        assertTrue(result.success());
        assertEquals(1, result.issuedTicketCodes().size());
    }
}
