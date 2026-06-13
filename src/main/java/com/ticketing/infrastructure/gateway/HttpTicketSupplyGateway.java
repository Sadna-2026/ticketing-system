package com.ticketing.infrastructure.gateway;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;

import com.ticketing.domain.gateway.CancelResult;
import com.ticketing.domain.gateway.CustomerInfo;
import com.ticketing.domain.gateway.ExternalSystemsUnavailableException;
import com.ticketing.domain.gateway.IExternalSystemsClient;
import com.ticketing.domain.gateway.ITicketSupplyGateway;
import com.ticketing.domain.gateway.SupplyResult;
import com.ticketing.domain.gateway.TicketRequest;

/**
 * The real {@link ITicketSupplyGateway} (V3-18): delegates issue/cancel to the WSEP external ticket
 * system via {@link IExternalSystemsClient} ({@code action_type=issue_ticket} /
 * {@code action_type=cancel_ticket}, {@code application/x-www-form-urlencoded}).
 *
 * <p>One {@code issue_ticket} call is made per {@link TicketRequest} (the purchase flow already
 * expands a GA quantity into one request per ticket). General admission sends {@code quantity=1};
 * assigned seating sends {@code is_seating=true} with the seat as a JSON {@code seats} array. The
 * endpoint returns the ticket code on success or {@code -1} on rejection. {@code cancel_ticket}
 * returns {@code 1} on success, {@code -1} (or anything else) on failure.
 *
 * <p>If an issue fails mid-batch — a {@code -1}, an empty/unexpected body, or an unreachable
 * endpoint — the already-issued codes are returned alongside the failure so
 * {@code OrderService}'s failover can cancel them (it cancels any non-empty issued-code list on a
 * failed supply); no exception escapes the purchase flow.
 *
 * <p>Registered only when {@code ticketing.external.base-url} is configured — it then
 * <b>replaces</b> {@link StubTicketSupplyGateway} as the active production bean (the stub carries
 * the inverse condition), so the two are mutually exclusive and the always-succeeding stub can never
 * sit in {@code OrderService}'s supply-gateway failover and mask a real supply failure.
 */
@Component
@ConditionalOnExpression("'${ticketing.external.base-url:}'.trim() != ''")
public class HttpTicketSupplyGateway implements ITicketSupplyGateway {

    private static final Logger log = LoggerFactory.getLogger(HttpTicketSupplyGateway.class);

    private static final String ACTION_ISSUE = "issue_ticket";
    private static final String ACTION_CANCEL = "cancel_ticket";
    private static final String FAILED = "-1";
    private static final String CANCEL_OK = "1";

    private final IExternalSystemsClient client;

    @Autowired
    public HttpTicketSupplyGateway(IExternalSystemsClient client) {
        this.client = client;
    }

    @Override
    public SupplyResult issueTickets(List<TicketRequest> tickets, CustomerInfo customer) {
        List<String> issued = new ArrayList<>();
        for (TicketRequest ticket : tickets) {
            try {
                String code = issueOne(ticket, customer);
                if (code == null) {
                    return new SupplyResult(false, issued,
                            "External ticket system rejected an issue request.");
                }
                issued.add(code);
            } catch (ExternalSystemsUnavailableException ex) {
                log.error("External ticket system unavailable during issue: {}", ex.getMessage());
                return new SupplyResult(false, issued, "Ticket supply system is currently unavailable.");
            }
        }
        return SupplyResult.successful(issued);
    }

    private String issueOne(TicketRequest ticket, CustomerInfo customer) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("action_type", ACTION_ISSUE);
        if (customer != null && customer.userId() != null) {
            params.put("customer_id", customer.userId());
        }
        params.put("event_id", ticket.eventId());
        params.put("zone", ticket.zoneId());
        if (ticket.seatId() == null) {
            params.put("quantity", "1");
        } else {
            params.put("is_seating", "true");
            params.put("seats", "[\"" + ticket.seatId() + "\"]");
        }

        String body = client.send(params).trim();
        if (body.isEmpty() || FAILED.equals(body)) {
            log.warn("External ticket issue failed or returned an unexpected response: '{}'", body);
            return null;
        }
        return body;
    }

    @Override
    public CancelResult cancelTickets(List<String> ticketCodes) {
        boolean allCancelled = true;
        for (String code : ticketCodes) {
            Map<String, String> params = new LinkedHashMap<>();
            params.put("action_type", ACTION_CANCEL);
            params.put("ticket_id", code);
            try {
                String body = client.send(params).trim();
                if (!CANCEL_OK.equals(body)) {
                    allCancelled = false;
                    log.warn("External ticket cancellation rejected for '{}': '{}'", code, body);
                }
            } catch (ExternalSystemsUnavailableException ex) {
                allCancelled = false;
                log.error("External ticket system unavailable during cancel of '{}': {}", code, ex.getMessage());
            }
        }
        if (allCancelled) {
            return CancelResult.successful();
        }
        return CancelResult.failed("One or more ticket cancellations were rejected by the external system.");
    }
}
