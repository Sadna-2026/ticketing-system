package com.ticketing.domain.auth;

import com.ticketing.domain.gateway.TicketRequest;
import com.ticketing.domain.gateway.CustomerInfo;
import com.ticketing.domain.gateway.SupplyResult;
import com.ticketing.domain.gateway.CancelResult;

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

