package com.ticketing.domain.event;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public class Event{

    private final UUID id;
    private final String companyName;
    private String name;
    private String description;
    private String artist;
    private EventCategory category;
    private String region;
    private EventSchedule schedule;
    private EventStatus status;
    private LockTimerDuration lockTimerDuration;
    private final List<InventoryZone> zones;
    private VenueMap venueMap;
    // V1 (UC-C.2 partial): every Event gets default policies; full edit API is V2.
    private IPurchasePolicy purchasePolicy;
    private IDiscountPolicy discountPolicy;
    private int version;

    public Event(UUID id, String companyName, String name, String description,
                 EventCategory category, EventSchedule schedule, LockTimerDuration lockTimerDuration) {
        if (id == null) throw new IllegalArgumentException("Event ID is required");
        if (companyName == null || companyName.isBlank()) throw new IllegalArgumentException("Company name is required");
        if (name == null || name.isBlank()) throw new IllegalArgumentException("Event name is required");
        if (schedule == null) throw new IllegalArgumentException("Event schedule is required");
        if (lockTimerDuration == null) throw new IllegalArgumentException("Lock timer duration is required");

        this.id = id;
        this.companyName = companyName;
        this.name = name;
        this.description = description;
        this.category = category;
        this.schedule = schedule;
        this.status = EventStatus.DRAFT;
        this.lockTimerDuration = lockTimerDuration;
        this.zones = new ArrayList<>();
        this.purchasePolicy = new AlwaysAllowPolicy();
        this.discountPolicy = new NoDiscountPolicy(java.util.Currency.getInstance("USD"));
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
    public String getCompanyName() { return companyName; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public String getArtist() { return artist; }
    public EventCategory getCategory() { return category; }
    public String getRegion() { return region; }
    public EventSchedule getSchedule() { return schedule; }
    public EventStatus getStatus() { return status; }
    public List<InventoryZone> getZones() { return java.util.Collections.unmodifiableList(zones); }
    public IPurchasePolicy getPurchasePolicy() { return purchasePolicy; }
    public IDiscountPolicy getDiscountPolicy() { return discountPolicy; }
    /** Repository-internal: called by InMemoryEventRepository.save() as part of the
     *  optimistic-lock flow. Service code should not call this directly. */
    public void incrementVersion() { this.version++; }
    public int getVersion() { return this.version; }

    public void addZone(InventoryZone zone) {
        validateModifiable();
        if (zone == null) throw new IllegalArgumentException("Zone cannot be null");
        zones.add(zone);
    }

    // Two distinct guards by design:
    //   validateModifiable() — DRAFT-only — gates STRUCTURAL changes (zones, venue map)
    //   rejectIfCancelled()  — not-CANCELLED — gates EDITORIAL changes (name, schedule, etc.)
    // After publish, the venue layout is locked but core details remain editable
    // (subject to the active-reservations check in EventService.editEvent).
    private void validateModifiable() {
        if (status != EventStatus.DRAFT) {
            throw new IllegalStateException("Cannot modify event in status: " + status);
        }
    }
    
    public boolean isCancelled() { return status == EventStatus.CANCELLED; }

    public void cancel() {
        if (status == EventStatus.CANCELLED) {
            throw new IllegalStateException("Event is already cancelled");
        }
        this.status = EventStatus.CANCELLED;
    }

    // --- core-detail setters (used by EventService.editEvent) ---

    public void setName(String name) {
        rejectIfCancelled();
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Event name cannot be blank");
        }
        this.name = name;
    }

    public void setDescription(String description) {
        rejectIfCancelled();
        this.description = description;
    }

    public void setArtist(String artist) {
        rejectIfCancelled();
        this.artist = artist;
    }

    public void setRegion(String region) {
        rejectIfCancelled();
        this.region = region;
    }

    public void setSchedule(EventSchedule schedule) {
        rejectIfCancelled();
        if (schedule == null) {
            throw new IllegalArgumentException("Schedule is required");
        }
        this.schedule = schedule;
    }

    private void rejectIfCancelled() {
        if (status == EventStatus.CANCELLED) {
            throw new IllegalStateException("Cannot edit a cancelled event");
        }
    }

    public void publish() {
        if (status != EventStatus.DRAFT) {
            throw new IllegalStateException("Can only publish a DRAFT event");
        }
        if (zones.isEmpty()) {
            throw new IllegalStateException("Event must have at least one inventory zone to publish");
        }
        this.status = EventStatus.PUBLISHED;
    }

    public void setVenueMap(VenueMap venueMap) {
        validateModifiable();
        if (venueMap == null) {
            throw new IllegalArgumentException("VenueMap cannot be null");
        }

        Set<UUID> mappedIds = venueMap.mappedZoneIds();
        Set<UUID> zoneIds = zones.stream()
                .map(InventoryZone::getId)
                .collect(Collectors.toSet());

        Set<UUID> unknown = new HashSet<>(mappedIds);
        unknown.removeAll(zoneIds);
        if (!unknown.isEmpty()) {
            throw new IllegalArgumentException(
                    "VenueMap references unknown zone ids: " + unknown);
        }

        Set<UUID> unmapped = new HashSet<>(zoneIds);
        unmapped.removeAll(mappedIds);
        if (!unmapped.isEmpty()) {
            throw new IllegalArgumentException(
                    "Zones not referenced by any venue map section: " + unmapped);
        }

        this.venueMap = venueMap;
    }

    public VenueMap getVenueMap() {
        return venueMap;
    }

}