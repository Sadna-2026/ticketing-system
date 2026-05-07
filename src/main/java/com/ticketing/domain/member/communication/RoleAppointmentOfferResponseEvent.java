package com.ticketing.domain.member.communication;

import java.util.UUID;

import com.ticketing.domain.event.IEvent;

public class RoleAppointmentOfferResponseEvent implements IEvent {
    private final UUID offerId;
    private final UUID targetMemberId;
    private final boolean accepted;

    public RoleAppointmentOfferResponseEvent(UUID offerId, UUID targetMemberId, boolean accepted) {
        this.offerId = offerId;
        this.targetMemberId = targetMemberId;
        this.accepted = accepted;
    }

    @Override
    public String getEventType() {
        return "RoleAppointmentOfferResponse";
    }

    public UUID getOfferId() { return offerId; }
    public UUID getTargetMemberId() { return targetMemberId; }
    public boolean isAccepted() { return accepted; }
}
