package com.ticketing.domain.gateway;

import java.util.List;

public interface ITicketSupplyGateway {
    /**
     * Optional startup health check. Existing gateways are considered reachable unless overridden.
     */
    default boolean isReachable() {
        return true;
    }

    SupplyResult issueTickets(List<TicketRequest> tickets, CustomerInfo customer);
    CancelResult cancelTickets(List<String> ticketCodes);
}

