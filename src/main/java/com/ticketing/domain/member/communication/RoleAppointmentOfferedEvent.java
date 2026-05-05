package com.ticketing.domain.member.communication;

import java.util.UUID;

import com.ticketing.domain.event.IEvent;

public class RoleAppointmentOfferedEvent implements IEvent {
    private final UUID offerId;
    private final String companyName;
    private final UUID targetMemberId;

    public RoleAppointmentOfferedEvent(UUID offerId, String companyName, UUID targetMemberId) {
        this.offerId = offerId;
        this.companyName = companyName;
        this.targetMemberId = targetMemberId;
    }

    @Override
    public String getEventType() {
        return "RoleAppointmentOffered";
    }

    public UUID getOfferId() { return offerId; }
    public String getCompanyName() { return companyName; }
    public UUID getTargetMemberId() { return targetMemberId; }
}
