package com.ticketing.domain.member.communication;

import java.util.UUID;

import com.ticketing.domain.company.Company;
import com.ticketing.domain.event.IEvent;

public class RevokePersonnelEvent implements IEvent {
    private final Company company;
    private final UUID revokerId;
    private final UUID targetMemberId;

    public RevokePersonnelEvent(Company company, UUID revokerId, UUID targetMemberId) {
        this.company = company;
        this.revokerId = revokerId;
        this.targetMemberId = targetMemberId;
    }

    @Override
    public String getEventType() {
        return "RevokePersonnel";
    }

    public Company getCompany() { return company; }
    public UUID getRevokerId() { return revokerId; }
    public UUID getTargetMemberId() { return targetMemberId; }

}
