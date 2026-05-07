package com.ticketing.domain.member.communication;

import java.util.UUID;

import com.ticketing.domain.company.Company;
import com.ticketing.domain.event.IEvent;

public class RelinquishOwnershipEvent implements IEvent {
    private final Company company;
    private final UUID ownerId;

    public RelinquishOwnershipEvent(Company company, UUID ownerId) {
        this.company = company;
        this.ownerId = ownerId;
    }

    @Override
    public String getEventType() {
        return "RelinquishOwnership";
    }

    public Company getCompany() {
        return company;
    }

    public UUID getOwnerId() {
        return ownerId;
    }
}
