package com.ticketing.domain.event;

public class Event{

    private EventStatus status;

    public boolean isCancelled() { return status == EventStatus.CANCELLED; }
    public boolean isPublished() { return status == EventStatus.PUBLISHED; }
    public boolean isDraft() { return status == EventStatus.DRAFT; }
    public boolean isSoldOut() { return status == EventStatus.SOLD_OUT; }
    public boolean isPurchasable() { return status == EventStatus.PUBLISHED; }

}