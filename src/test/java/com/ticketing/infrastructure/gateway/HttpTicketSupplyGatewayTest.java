package com.ticketing.infrastructure.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import com.ticketing.domain.gateway.CancelResult;
import com.ticketing.domain.gateway.CustomerInfo;
import com.ticketing.domain.gateway.SupplyResult;
import com.ticketing.domain.gateway.TicketRequest;

/**
 * Verifies the real {@link HttpTicketSupplyGateway} (V3-18) against a stubbed WSEP endpoint
 * ({@link MockRestServiceServer}) — never the live system. The gateway is exercised through the
 * real {@link HttpExternalSystemsClient} so the form-encoded {@code issue_ticket}/{@code cancel_ticket}
 * requests (GA {@code quantity} vs assigned {@code is_seating}/{@code seats}) and the
 * ticket-code / {@code -1} / {@code 1} response mapping are covered end-to-end.
 */
class HttpTicketSupplyGatewayTest {

    private static final String BASE_URL = "http://external.test/api/";
    private static final CustomerInfo CUSTOMER = new CustomerInfo("user-42", "buyer@test.com", "Jane Buyer");

    private static TicketRequest ga(String ticketId) {
        return new TicketRequest("evt-1", "zone-9", ticketId, null);
    }

    private static TicketRequest seat(String ticketId, String seatId) {
        return new TicketRequest("evt-1", "zone-9", ticketId, seatId);
    }

    private HttpTicketSupplyGateway gatewayFor(RestTemplate restTemplate) {
        return new HttpTicketSupplyGateway(new HttpExternalSystemsClient(restTemplate, BASE_URL));
    }

    // ---- issue -----------------------------------------------------------------------------

    @Test
    void GivenGeneralAdmission_WhenIssue_ThenPostsQuantityAndReturnsCode() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.createServer(restTemplate);
        server.expect(requestTo(BASE_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_FORM_URLENCODED))
                .andExpect(content().string(containsString("action_type=issue_ticket")))
                .andExpect(content().string(containsString("customer_id=user-42")))
                .andExpect(content().string(containsString("event_id=evt-1")))
                .andExpect(content().string(containsString("zone=zone-9")))
                .andExpect(content().string(containsString("quantity=1")))
                .andExpect(content().string(not(containsString("is_seating"))))
                .andRespond(withSuccess("TKT-1001", MediaType.TEXT_PLAIN));

        SupplyResult result = gatewayFor(restTemplate).issueTickets(List.of(ga("t-1")), CUSTOMER);

        assertThat(result.success()).isTrue();
        assertThat(result.issuedTicketCodes()).containsExactly("TKT-1001");
        assertThat(result.errorMessage()).isNull();
        server.verify();
    }

    @Test
    void GivenAssignedSeat_WhenIssue_ThenPostsSeatingPayloadAndReturnsCode() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.createServer(restTemplate);
        server.expect(requestTo(BASE_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().string(containsString("action_type=issue_ticket")))
                .andExpect(content().string(containsString("is_seating=true")))
                // seats is sent as a JSON array; URL-encoded the brackets/quotes survive as %5B%22..%22%5D
                .andExpect(content().string(containsString("seats=")))
                .andExpect(content().string(containsString("seat-A1")))
                .andExpect(content().string(not(containsString("quantity"))))
                .andRespond(withSuccess("TKT-2002", MediaType.TEXT_PLAIN));

        SupplyResult result = gatewayFor(restTemplate).issueTickets(List.of(seat("t-1", "seat-A1")), CUSTOMER);

        assertThat(result.success()).isTrue();
        assertThat(result.issuedTicketCodes()).containsExactly("TKT-2002");
        server.verify();
    }

    @Test
    void GivenMultipleTickets_WhenAllIssued_ThenAllCodesCollectedInOrder() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.createServer(restTemplate);
        server.expect(ExpectedCount.once(), requestTo(BASE_URL))
                .andRespond(withSuccess("TKT-1", MediaType.TEXT_PLAIN));
        server.expect(ExpectedCount.once(), requestTo(BASE_URL))
                .andRespond(withSuccess("TKT-2", MediaType.TEXT_PLAIN));

        SupplyResult result = gatewayFor(restTemplate)
                .issueTickets(List.of(ga("t-1"), seat("t-2", "seat-B2")), CUSTOMER);

        assertThat(result.success()).isTrue();
        assertThat(result.issuedTicketCodes()).containsExactly("TKT-1", "TKT-2");
        server.verify();
    }

    @Test
    void GivenMinusOne_WhenIssue_ThenFails() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.createServer(restTemplate);
        server.expect(requestTo(BASE_URL)).andRespond(withSuccess("-1", MediaType.TEXT_PLAIN));

        SupplyResult result = gatewayFor(restTemplate).issueTickets(List.of(ga("t-1")), CUSTOMER);

        assertThat(result.success()).isFalse();
        assertThat(result.issuedTicketCodes()).isEmpty();
        assertThat(result.errorMessage()).isNotNull();
        server.verify();
    }

    @Test
    void GivenEmptyBody_WhenIssue_ThenFails() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.createServer(restTemplate);
        server.expect(requestTo(BASE_URL)).andRespond(withSuccess("   ", MediaType.TEXT_PLAIN));

        SupplyResult result = gatewayFor(restTemplate).issueTickets(List.of(ga("t-1")), CUSTOMER);

        assertThat(result.success()).isFalse();
        server.verify();
    }

    @Test
    void GivenMalformedBody_WhenIssue_ThenFails() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.createServer(restTemplate);
        // Garbage HTML response instead of a ticket code
        server.expect(requestTo(BASE_URL)).andRespond(withSuccess("<html>502 Bad Gateway</html>", MediaType.TEXT_HTML));

        SupplyResult result = gatewayFor(restTemplate).issueTickets(List.of(ga("t-1")), CUSTOMER);

        assertThat(result.success()).isFalse();
        assertThat(result.issuedTicketCodes()).isEmpty();
        server.verify();
    }

    @Test
    void GivenSecondTicketRejected_WhenIssue_ThenReturnsPartialCodesForRollback() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.createServer(restTemplate);
        server.expect(ExpectedCount.once(), requestTo(BASE_URL))
                .andRespond(withSuccess("TKT-1", MediaType.TEXT_PLAIN));
        server.expect(ExpectedCount.once(), requestTo(BASE_URL))
                .andRespond(withSuccess("-1", MediaType.TEXT_PLAIN));

        SupplyResult result = gatewayFor(restTemplate)
                .issueTickets(List.of(ga("t-1"), ga("t-2")), CUSTOMER);

        assertThat(result.success()).isFalse();
        // The first ticket was issued before the second failed; its code is surfaced so the
        // caller's failover can cancel it. No third call is made.
        assertThat(result.issuedTicketCodes()).containsExactly("TKT-1");
        server.verify();
    }

    @Test
    void GivenEndpointUnavailable_WhenIssue_ThenFailsGracefully() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.createServer(restTemplate);
        server.expect(requestTo(BASE_URL)).andRespond(withServerError());

        SupplyResult result = gatewayFor(restTemplate).issueTickets(List.of(ga("t-1")), CUSTOMER);

        assertThat(result.success()).isFalse();
        assertThat(result.errorMessage()).isNotNull();
        server.verify();
    }

    // ---- cancel ----------------------------------------------------------------------------

    @Test
    void GivenOne_WhenCancel_ThenSucceedsAndPostsTicketId() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.createServer(restTemplate);
        server.expect(requestTo(BASE_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().string(containsString("action_type=cancel_ticket")))
                .andExpect(content().string(containsString("ticket_id=TKT-1")))
                .andRespond(withSuccess("1", MediaType.TEXT_PLAIN));

        CancelResult result = gatewayFor(restTemplate).cancelTickets(List.of("TKT-1"));

        assertThat(result.success()).isTrue();
        assertThat(result.errorMessage()).isNull();
        server.verify();
    }

    @Test
    void GivenAllOnes_WhenCancelMany_ThenSucceeds() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.createServer(restTemplate);
        server.expect(ExpectedCount.twice(), requestTo(BASE_URL))
                .andRespond(withSuccess("1", MediaType.TEXT_PLAIN));

        CancelResult result = gatewayFor(restTemplate).cancelTickets(List.of("TKT-1", "TKT-2"));

        assertThat(result.success()).isTrue();
        server.verify();
    }

    @Test
    void GivenOneRejected_WhenCancelMany_ThenFailsButAttemptsAll() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.createServer(restTemplate);
        server.expect(ExpectedCount.once(), requestTo(BASE_URL))
                .andRespond(withSuccess("1", MediaType.TEXT_PLAIN));
        server.expect(ExpectedCount.once(), requestTo(BASE_URL))
                .andRespond(withSuccess("-1", MediaType.TEXT_PLAIN));

        CancelResult result = gatewayFor(restTemplate).cancelTickets(List.of("TKT-1", "TKT-2"));

        assertThat(result.success()).isFalse();
        assertThat(result.errorMessage()).isNotNull();
        server.verify();
    }

    @Test
    void GivenEndpointUnavailable_WhenCancel_ThenFailsGracefully() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.createServer(restTemplate);
        server.expect(requestTo(BASE_URL)).andRespond(withServerError());

        CancelResult result = gatewayFor(restTemplate).cancelTickets(List.of("TKT-1"));

        assertThat(result.success()).isFalse();
        assertThat(result.errorMessage()).isNotNull();
        server.verify();
    }
}
