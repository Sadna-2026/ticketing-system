package com.ticketing.infrastructure.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.math.BigDecimal;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import com.ticketing.domain.gateway.PaymentDetails;
import com.ticketing.domain.gateway.PaymentResult;
import com.ticketing.domain.gateway.RefundResult;

/**
 * Verifies the real {@link HttpPaymentGateway} (V3-17) against a stubbed WSEP endpoint
 * ({@link MockRestServiceServer}) — never the live system. The gateway is exercised through the
 * real {@link HttpExternalSystemsClient} so the form-encoded {@code pay}/{@code refund} requests and
 * the {@code [10000,100000]} / {@code -1} response mapping are covered end-to-end.
 */
class HttpPaymentGatewayTest {

    private static final String BASE_URL = "http://external.test/api/";
    private static final HttpPaymentGateway.CardConfig CARD = new HttpPaymentGateway.CardConfig(
            "USD", "4111111111111111", "10", "2031", "TESTER", "999", "123456789");

    private static PaymentDetails details() {
        return new PaymentDetails(UUID.randomUUID(), UUID.randomUUID(), null, "buyer@test.com");
    }

    private HttpPaymentGateway gatewayFor(RestTemplate restTemplate) {
        return new HttpPaymentGateway(new HttpExternalSystemsClient(restTemplate, BASE_URL), CARD);
    }

    // ---- charge ----------------------------------------------------------------------------

    @Test
    void GivenApprovedTransactionId_WhenCharge_ThenSucceedsAndPostsPayParams() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.createServer(restTemplate);
        server.expect(requestTo(BASE_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_FORM_URLENCODED))
                .andExpect(content().string(containsString("action_type=pay")))
                .andExpect(content().string(containsString("amount=150.00")))
                .andExpect(content().string(containsString("currency=USD")))
                .andExpect(content().string(containsString("card_number=4111111111111111")))
                .andRespond(withSuccess("55000", MediaType.TEXT_PLAIN));

        PaymentResult result = gatewayFor(restTemplate).charge(new BigDecimal("150.00"), details());

        assertThat(result.success()).isTrue();
        assertThat(result.transactionId()).isEqualTo("55000");
        assertThat(result.errorMessage()).isNull();
        server.verify();
    }

    @Test
    void GivenBuyerCardDetails_WhenCharge_ThenBuyerCardOverridesConfiguredCard() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.createServer(restTemplate);
        server.expect(requestTo(BASE_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().string(containsString("currency=EUR")))
                .andExpect(content().string(containsString("card_number=5555666677778888")))
                .andExpect(content().string(containsString("cvv=321")))
                .andExpect(content().string(containsString("id=987654321")))
                .andRespond(withSuccess("55000", MediaType.TEXT_PLAIN));

        PaymentDetails buyerCard = new PaymentDetails(UUID.randomUUID(), UUID.randomUUID(), null, "buyer@test.com",
                "EUR", "5555666677778888", "4", "2029", "Real Buyer", "321", "987654321");
        PaymentResult result = gatewayFor(restTemplate).charge(new BigDecimal("150.00"), buyerCard);

        assertThat(result.success()).isTrue();
        server.verify();
    }

    @Test
    void GivenMinusOne_WhenCharge_ThenDeclined() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.createServer(restTemplate);
        server.expect(requestTo(BASE_URL)).andRespond(withSuccess("-1", MediaType.TEXT_PLAIN));

        PaymentResult result = gatewayFor(restTemplate).charge(new BigDecimal("150.00"), details());

        assertThat(result.success()).isFalse();
        assertThat(result.transactionId()).isNull();
        assertThat(result.errorMessage()).isNotNull();
        server.verify();
    }

    @Test
    void GivenIdsAtRangeBoundaries_WhenCharge_ThenSucceeds() {
        for (String id : new String[] {"10000", "100000"}) {
            RestTemplate restTemplate = new RestTemplate();
            MockRestServiceServer server = MockRestServiceServer.createServer(restTemplate);
            server.expect(requestTo(BASE_URL)).andRespond(withSuccess(id, MediaType.TEXT_PLAIN));

            PaymentResult result = gatewayFor(restTemplate).charge(new BigDecimal("10.00"), details());

            assertThat(result.success()).as("boundary id %s", id).isTrue();
            assertThat(result.transactionId()).isEqualTo(id);
            server.verify();
        }
    }

    @Test
    void GivenIdsOutsideValidRange_WhenCharge_ThenDeclined() {
        for (String id : new String[] {"9999", "100001", "0"}) {
            RestTemplate restTemplate = new RestTemplate();
            MockRestServiceServer server = MockRestServiceServer.createServer(restTemplate);
            server.expect(requestTo(BASE_URL)).andRespond(withSuccess(id, MediaType.TEXT_PLAIN));

            PaymentResult result = gatewayFor(restTemplate).charge(new BigDecimal("10.00"), details());

            assertThat(result.success()).as("out-of-range id %s", id).isFalse();
            assertThat(result.transactionId()).isNull();
            server.verify();
        }
    }

    @Test
    void GivenNonNumericResponse_WhenCharge_ThenDeclined() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.createServer(restTemplate);
        server.expect(requestTo(BASE_URL)).andRespond(withSuccess("APPROVED", MediaType.TEXT_PLAIN));

        PaymentResult result = gatewayFor(restTemplate).charge(new BigDecimal("10.00"), details());

        assertThat(result.success()).isFalse();
        server.verify();
    }

    @Test
    void GivenWhitespaceWrappedId_WhenCharge_ThenTrimmedAndSucceeds() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.createServer(restTemplate);
        server.expect(requestTo(BASE_URL)).andRespond(withSuccess("  12345 \n", MediaType.TEXT_PLAIN));

        PaymentResult result = gatewayFor(restTemplate).charge(new BigDecimal("10.00"), details());

        assertThat(result.success()).isTrue();
        assertThat(result.transactionId()).isEqualTo("12345");
        server.verify();
    }

    @Test
    void GivenEndpointUnavailable_WhenCharge_ThenFailsGracefully() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.createServer(restTemplate);
        server.expect(requestTo(BASE_URL)).andRespond(withServerError());

        PaymentResult result = gatewayFor(restTemplate).charge(new BigDecimal("10.00"), details());

        assertThat(result.success()).isFalse();
        assertThat(result.errorMessage()).isNotNull();
        server.verify();
    }

    // ---- refund ----------------------------------------------------------------------------

    @Test
    void GivenOne_WhenRefund_ThenSucceedsAndPostsRefundParams() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.createServer(restTemplate);
        server.expect(requestTo(BASE_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().string(containsString("action_type=refund")))
                .andExpect(content().string(containsString("transaction_id=55000")))
                .andRespond(withSuccess("1", MediaType.TEXT_PLAIN));

        RefundResult result = gatewayFor(restTemplate).refund("55000", 150.00);

        assertThat(result.success()).isTrue();
        assertThat(result.refundTransactionId()).isEqualTo("55000");
        assertThat(result.errorMessage()).isNull();
        server.verify();
    }

    @Test
    void GivenMinusOne_WhenRefund_ThenFails() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.createServer(restTemplate);
        server.expect(requestTo(BASE_URL)).andRespond(withSuccess("-1", MediaType.TEXT_PLAIN));

        RefundResult result = gatewayFor(restTemplate).refund("55000", 150.00);

        assertThat(result.success()).isFalse();
        assertThat(result.errorMessage()).isNotNull();
        server.verify();
    }

    @Test
    void GivenUnexpectedResponse_WhenRefund_ThenFails() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.createServer(restTemplate);
        server.expect(requestTo(BASE_URL)).andRespond(withSuccess("0", MediaType.TEXT_PLAIN));

        RefundResult result = gatewayFor(restTemplate).refund("55000", 150.00);

        assertThat(result.success()).isFalse();
        server.verify();
    }

    @Test
    void GivenEndpointUnavailable_WhenRefund_ThenFailsGracefully() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.createServer(restTemplate);
        server.expect(requestTo(BASE_URL)).andRespond(withServerError());

        RefundResult result = gatewayFor(restTemplate).refund("55000", 150.00);

        assertThat(result.success()).isFalse();
        assertThat(result.errorMessage()).isNotNull();
        server.verify();
    }
}
