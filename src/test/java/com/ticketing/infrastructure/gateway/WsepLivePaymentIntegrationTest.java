package com.ticketing.infrastructure.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.web.client.RestTemplate;

import com.ticketing.domain.gateway.IExternalSystemsClient;
import com.ticketing.domain.gateway.PaymentDetails;
import com.ticketing.domain.gateway.PaymentResult;

/**
 * #530: complements {@link com.ticketing.application.TicketSupplyRobustnessTest} and
 * {@link com.ticketing.application.CheckoutRobustnessTest} (both mock-based) by exercising
 * the real {@link HttpPaymentGateway} against the actual WSEP sandbox at
 * {@code https://damp-lynna-wsep-1984852e.koyeb.app/}.
 *
 * <p>WSEP encodes its test outcomes in the {@code cvv} parameter — three sentinels are
 * known to produce deterministic responses:
 * <ul>
 *   <li>{@code cvv=100} → success: returns a transaction id in {@code [10000, 100000]}.</li>
 *   <li>{@code cvv=988} → declined: returns {@code -1}, the gateway maps to "declined".</li>
 *   <li>{@code cvv=986} → unresponsive: WSEP holds the connection open without sending a body;
 *       at the client this surfaces as a read timeout, which the gateway maps to "payment
 *       system unavailable". In an older WSEP version this returned a malformed body — either
 *       outcome is a valid robustness signal: the gateway returns a typed failure, never
 *       throws or returns a fake success.</li>
 * </ul>
 *
 * <p>These tests are <b>opt-in</b>: they hit a live external service and would flake when WSEP is
 * down. They run only when the {@code WSEP_LIVE_TESTS=true} environment variable is set —
 * exclude them from local quick runs, enable them on a scheduled job (or before submitting a
 * grading milestone) to validate the integration against the real sandbox.
 *
 * <p>To run: {@code WSEP_LIVE_TESTS=true mvn test -Dtest='WsepLivePaymentIntegrationTest'}.
 */
@DisplayName("WSEP live payment integration (#530)")
@EnabledIfEnvironmentVariable(named = "WSEP_LIVE_TESTS", matches = "true")
class WsepLivePaymentIntegrationTest {

    private static final String WSEP_URL = "https://damp-lynna-wsep-1984852e.koyeb.app/";

    private static HttpPaymentGateway liveGateway() {
        // Generous timeouts because WSEP runs on free-tier hosting that can cold-start
        // for several seconds, and the CVV=986 case in particular tends to delay before
        // sending its non-numeric response body.
        RestTemplate restTemplate = new RestTemplateBuilder()
                .setConnectTimeout(java.time.Duration.ofSeconds(15))
                .setReadTimeout(java.time.Duration.ofSeconds(30))
                .build();
        IExternalSystemsClient client = new HttpExternalSystemsClient(restTemplate, WSEP_URL);
        // Card config defaults (currency/cardNumber/etc.) are not what we vary — we vary CVV
        // per-test via PaymentDetails. The values here are sandbox placeholders consistent
        // with the WSEP example in the External Systems API document.
        HttpPaymentGateway.CardConfig sandboxCard = new HttpPaymentGateway.CardConfig(
                "USD", "2222333344445555", "4", "2030", "Ticketing System", "123", "20444444");
        return new HttpPaymentGateway(client, sandboxCard);
    }

    private static PaymentDetails detailsWithCvv(String cvv) {
        return new PaymentDetails(
                UUID.randomUUID(),    // orderId
                UUID.randomUUID(),    // eventId
                UUID.randomUUID(),    // memberId
                "live-buyer@test.example",
                "USD",
                "2222333344445555",
                "4",
                "2030",
                "Live Buyer",
                cvv,
                "20444444");
    }

    @Test
    @DisplayName("Given CVV 100, When charge, Then WSEP returns a transaction id and the result is successful")
    void GivenCvv100_WhenCharge_ThenLiveWsepReturnsTransactionIdAndResultSuccessful() {
        PaymentResult result = liveGateway().charge(new BigDecimal("150.00"), detailsWithCvv("100"));

        assertThat(result.success())
                .as("CVV 100 should be approved by WSEP")
                .isTrue();
        // The gateway echoes WSEP's transaction-id body in the receipt. Tighter than
        // "non-blank" because WSEP guarantees the id is in [10000, 100000].
        assertThat(result.transactionId())
                .as("WSEP returns a numeric transaction id; gateway preserves it verbatim")
                .matches("\\d+");
        int txnId = Integer.parseInt(result.transactionId());
        assertThat(txnId).isBetween(10000, 100000);
    }

    @Test
    @DisplayName("Given CVV 988, When charge, Then WSEP returns -1 and the gateway reports a declined payment")
    void GivenCvv988_WhenCharge_ThenLiveWsepDeclinesAndGatewayReportsFailure() {
        PaymentResult result = liveGateway().charge(new BigDecimal("150.00"), detailsWithCvv("988"));

        assertThat(result.success())
                .as("CVV 988 is the WSEP sentinel for an explicit decline (-1)")
                .isFalse();
        assertThat(result.errorMessage())
                .as("the gateway must surface a 'declined by external' message, not raw -1")
                .containsIgnoringCase("declined");
    }

    @Test
    @DisplayName("Given CVV 986, When charge, Then WSEP misbehaves (timeout or malformed body) and the gateway reports a graceful failure")
    void GivenCvv986_WhenCharge_ThenLiveWsepMisbehavesAndGatewayReportsGracefulFailure() {
        PaymentResult result = liveGateway().charge(new BigDecimal("150.00"), detailsWithCvv("986"));

        // The robustness contract: regardless of how WSEP misbehaves for this CVV (currently
        // a hung response → read timeout; previously a non-numeric body), the gateway must
        // surface a typed PaymentResult.failed — never an uncaught exception, never a silent
        // "success" with a junk id. Both "declined by external" and "system unavailable" are
        // legitimate user-facing categories the OrderService can act on.
        assertThat(result.success())
                .as("CVV 986 must never produce an approved payment")
                .isFalse();
        assertThat(result.errorMessage())
                .as("the gateway must produce a non-blank user-facing message even when WSEP misbehaves")
                .isNotBlank();
        assertThat(result.transactionId())
                .as("no transaction id is allocated when WSEP does not respond with a valid one")
                .isNull();
    }
}
