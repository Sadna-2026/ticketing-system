package com.ticketing.domain.event;

import java.util.UUID;

/**
 * Event published when a production company is closed (temporarily or permanently).
 */
public class CompanyClosedEvent implements IEvent {
    private static final String EVENT_TYPE = "CompanyClosed";
    
    private final String companyName;
    private final boolean permanent;

    public CompanyClosedEvent(String companyName, boolean permanent) {
        this.companyName = companyName;
        this.permanent = permanent;
    }

    @Override
    public String getEventType() {
        return EVENT_TYPE;
    }

    public String getCompanyName() {
        return companyName;
    }

    public boolean isPermanent() {
        return permanent;
    }
}
