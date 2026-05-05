package com.ticketing.domain.event;

import java.util.UUID;

/**
 * Event published when a production company is opened.
 */
public class CompanyOpenedEvent implements IEvent {
    private static final String EVENT_TYPE = "CompanyOpened";
    
    private final String companyName;
    private final UUID founderId;

    public CompanyOpenedEvent(String companyName, UUID founderId) {
        this.companyName = companyName;
        this.founderId = founderId;
    }

    @Override
    public String getEventType() {
        return EVENT_TYPE;
    }

    public String getCompanyName() {
        return companyName;
    }

    public UUID getFounderId() {
        return founderId;
    }
}
