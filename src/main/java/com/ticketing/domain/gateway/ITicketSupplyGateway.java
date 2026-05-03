package com.ticketing.domain.gateway;

import java.util.List;

public interface ITicketSupplyGateway {
    SupplyResult issueTickets(List<TicketRequest> tickets, CustomerInfo customer);
    CancelResult cancelTickets(List<String> ticketCodes);
}
