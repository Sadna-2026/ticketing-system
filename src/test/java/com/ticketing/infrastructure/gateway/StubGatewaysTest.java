package com.ticketing.infrastructure.gateway;

import com.ticketing.domain.gateway.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class StubGatewaysTest {

    private StubPaymentGateway paymentGateway;
    private StubTicketSupplyGateway supplyGateway;

    @BeforeEach
    void setUp() {
        paymentGateway = new StubPaymentGateway();
        supplyGateway = new StubTicketSupplyGateway();
    }

    @Test
    void testPaymentGateway_SuccessMode() {
        PaymentDetails details = new PaymentDetails("tok_123", "ILS");
        PaymentResult result = paymentGateway.charge(150.0, details);
        assertTrue(result.success());
        assertNotNull(result.transactionId());
    }

    @Test
    void testPaymentGateway_FailureMode() {
        paymentGateway.setShouldFail(true);
        PaymentDetails details = new PaymentDetails("tok_123", "ILS");
        PaymentResult result = paymentGateway.charge(150.0, details);
        assertFalse(result.success());
        assertNotNull(result.errorMessage());
    }

    @Test
    void testTicketSupplyGateway_SuccessMode() {
        List<TicketRequest> requests = List.of(new TicketRequest("evt_1", "tkt_1", "seat_A1"));
        CustomerInfo customer = new CustomerInfo("user_1", "test@test.com", "John Doe");
        
        SupplyResult result = supplyGateway.issueTickets(requests, customer);
        assertTrue(result.success());
        assertEquals(1, result.issuedTicketCodes().size());
    }
}
