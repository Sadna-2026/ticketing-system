package com.ticketing.application.dto;

import java.util.List;
import java.util.UUID;

/**
 * Data Transfer Object for a virtual queue associated with an event.
 */
public final class VirtualQueueDto {

    private final UUID id;
    private final UUID eventId;
    private final int threshold;
    private final int flowRate;
    private final boolean active;
    private final int currentActiveUsers;
    private final int waitingCount;
    private final List<QueueEntryDto> entries;

    public VirtualQueueDto(UUID id, UUID eventId, int threshold, int flowRate,
                           boolean active, int currentActiveUsers, int waitingCount,
                           List<QueueEntryDto> entries) {
        this.id = id;
        this.eventId = eventId;
        this.threshold = threshold;
        this.flowRate = flowRate;
        this.active = active;
        this.currentActiveUsers = currentActiveUsers;
        this.waitingCount = waitingCount;
        this.entries = entries;
    }

    public UUID getId() { return id; }
    public UUID getEventId() { return eventId; }
    public int getThreshold() { return threshold; }
    public int getFlowRate() { return flowRate; }
    public boolean isActive() { return active; }
    public int getCurrentActiveUsers() { return currentActiveUsers; }
    public int getWaitingCount() { return waitingCount; }
    public List<QueueEntryDto> getEntries() { return entries; }
}

