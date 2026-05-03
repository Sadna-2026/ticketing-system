package com.ticketing.domain.event;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class Event{

    private final UUID id;
    private final UUID companyId;
    private String name;
    private String description;
    private String artist;
    private EventCategory category;
    private String region;
    private EventSchedule schedule;
    private EventStatus status;
    private LockTimerDuration lockTimerDuration;
    private final List<InventoryZone> zones;
    private final EventPurchasePolicy eventPurchasePolicy;
    private final EventDiscountPolicy eventDiscountPolicy;
    private int version;

    /**
     * Creates a new Event with required policies.
     * Policies must be provided at creation time and cannot be changed afterward in V1.
     *
     * @param id unique event identifier
     * @param companyId the production company that owns this event
     * @param name the event name
     * @param description event description (may be null)
     * @param category event category (may be null for draft)
     * @param schedule the event schedule (start time, end time, doors open)
     * @param lockTimerDuration the per-event lock timer duration for active orders
     * @param eventPurchasePolicy the event's purchase policy (required, per V1)
     * @param eventDiscountPolicy the event's discount policy (required, per V1)
     */
    public Event(UUID id, UUID companyId, String name, String description,
                 EventCategory category, EventSchedule schedule, LockTimerDuration lockTimerDuration,
                 EventPurchasePolicy eventPurchasePolicy, EventDiscountPolicy eventDiscountPolicy) {
        if (id == null) throw new IllegalArgumentException("Event ID is required");
        if (companyId == null) throw new IllegalArgumentException("Company ID is required");
        if (name == null || name.isBlank()) throw new IllegalArgumentException("Event name is required");
        if (schedule == null) throw new IllegalArgumentException("Event schedule is required");
        if (lockTimerDuration == null) throw new IllegalArgumentException("Lock timer duration is required");
        if (eventPurchasePolicy == null) throw new IllegalArgumentException("EventPurchasePolicy is required");
        if (eventDiscountPolicy == null) throw new IllegalArgumentException("EventDiscountPolicy is required");

        this.id = id;
        this.companyId = companyId;
        this.name = name;
        this.description = description;
        this.category = category;
        this.schedule = schedule;
        this.status = EventStatus.DRAFT;
        this.lockTimerDuration = lockTimerDuration;
        this.zones = new ArrayList<>();
        this.eventPurchasePolicy = eventPurchasePolicy;
        this.eventDiscountPolicy = eventDiscountPolicy;
        this.version = 0;
    }

    public LockTimerDuration getLockTimerDuration() { return lockTimerDuration; }

    public InventoryZone findZone(UUID zoneId) {
        return zones.stream()
                .filter(z -> z.getId().equals(zoneId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Zone not found: " + zoneId));
    }

    public boolean hasAvailableTickets() {
        return getTotalAvailableTickets() > 0;
    }

    public int getTotalAvailableTickets() {
        return zones.stream().mapToInt(InventoryZone::getAvailableCount).sum();
    }

    public boolean isPublished() { return status == EventStatus.PUBLISHED; }

    public void markSoldOut() {
        if (status != EventStatus.PUBLISHED) {
            throw new IllegalStateException("Can only mark a PUBLISHED event as sold out");
        }
        this.status = EventStatus.SOLD_OUT;
    }

    public UUID getId() {
        return id;
    }
    public void incrementVersion() { this.version++; }
    public int getVersion() { return this.version; }

}