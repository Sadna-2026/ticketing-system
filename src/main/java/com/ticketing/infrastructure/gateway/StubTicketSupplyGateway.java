package com.ticketing.infrastructure.gateway;

import com.ticketing.domain.gateway.CancelResult;
import com.ticketing.domain.gateway.CustomerInfo;
import com.ticketing.domain.gateway.ITicketSupplyGateway;
import com.ticketing.domain.gateway.SupplyResult;
import com.ticketing.domain.gateway.TicketRequest;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

public class StubTicketSupplyGateway implements ITicketSupplyGateway {

    private boolean shouldFail = false;

    public void setShouldFail(boolean shouldFail) {
        this.shouldFail = shouldFail;
    }

    @Override
    public SupplyResult issueTickets(List<TicketRequest> tickets, CustomerInfo customer) {
        if (shouldFail) {
            return SupplyResult.failed("External ticket generation service unavailable.");
        }
        
        List<String> generatedCodes = tickets.stream()
                .map(t -> "TKT-" + UUID.randomUUID().toString().substring(0, 8))
                .collect(Collectors.toList());
                
        return SupplyResult.successful(generatedCodes);
    }

    @Override
    public CancelResult cancelTickets(List<String> ticketCodes) {
        if (shouldFail) {
            return CancelResult.failed("Failed to communicate cancellation to external service.");
        }
        return CancelResult.successful();
    }
}
