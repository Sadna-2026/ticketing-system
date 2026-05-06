package com.ticketing.infrastructure.Interface;

import com.ticketing.domain.gateway.TicketRequest;
import com.ticketing.domain.gateway.CustomerInfo;
import com.ticketing.domain.gateway.SupplyResult;
import com.ticketing.domain.gateway.CancelResult;

import java.util.List;

public interface ITicketSupplyGateway {
    SupplyResult issueTickets(List<TicketRequest> tickets, CustomerInfo customer);
    CancelResult cancelTickets(List<String> ticketCodes);
}

