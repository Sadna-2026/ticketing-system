package com.ticketing.domain.queue;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

import com.ticketing.application.dto.VirtualQueueDto;

/**
 * Aggregate Root: VirtualQueue
 *
 * Per-event virtual queue that manages load by routing users to a waiting room
 * when concurrent users exceed a configurable threshold.
 *
 * Invariants:
 * - One VirtualQueue per event.
 * - Entries are admitted in FIFO order.
 * - Configuration is controlled by System Admin.
 */
public class VirtualQueue {

    private final UUID id;
    private final UUID eventId;
    private QueueConfig config;
    private final List<QueueEntry> entries;
    private boolean active;
    private int currentActiveUsers;
    private int version;

    public VirtualQueue(UUID id, UUID eventId, QueueConfig config) {
        if (id == null) throw new IllegalArgumentException("VirtualQueue ID is required");
        if (eventId == null) throw new IllegalArgumentException("Event ID is required");
        if (config == null) throw new IllegalArgumentException("QueueConfig is required");
        this.id = id;
        this.eventId = eventId;
        this.config = config;
        this.entries = new ArrayList<>();
        this.active = true;
        this.currentActiveUsers = 0;
        this.version = 0;
    }

    private VirtualQueue(
            UUID id,
            UUID eventId,
            QueueConfig config,
            List<QueueEntry> entries,
            boolean active,
            int currentActiveUsers,
            int version
    ) {
        this(id, eventId, config);
        this.entries.addAll(entries.stream().map(QueueEntry::detachedCopy).toList());
        this.active = active;
        this.currentActiveUsers = currentActiveUsers;
        this.version = version;
    }

    public UUID getId() { return id; }
    public UUID getEventId() { return eventId; }
    public QueueConfig getConfig() { return config; }
    public List<QueueEntry> getEntries() { return Collections.unmodifiableList(entries); }
    public boolean isActive() { return active; }
    public int getCurrentActiveUsers() { return currentActiveUsers; }
    public int getVersion() { return version; }
    public void incrementVersion() { this.version++; }

    /**
     * Determines if a new user should be queued based on current load.
     */
    public boolean shouldQueue() {
        return active && currentActiveUsers >= config.getThreshold();
    }

    /**
     * Adds a user to the queue.
     */
    public QueueEntry enqueue(UUID sessionId, Instant now) {
        QueueEntry entry = new QueueEntry(UUID.randomUUID(), sessionId, now);
        entries.add(entry);
        return entry;
    }

    /**
     * Admits the next batch of users from the queue.
     */
    public List<QueueEntry> admitNextBatch() {
        int batchSize = config.getFlowRate();
        List<QueueEntry> waiting = entries.stream()
                .filter(QueueEntry::isWaiting)
                .sorted(Comparator.comparing(QueueEntry::getJoinedAt))
                .limit(batchSize)
                .collect(Collectors.toList());

        waiting.forEach(QueueEntry::admit);
        currentActiveUsers += waiting.size();
        return waiting;
    }

    /**
     * Records that a user has left the active purchasing phase.
     */
    public void userLeft() {
        if (currentActiveUsers > 0) {
            currentActiveUsers--;
        }
    }

    /**
     * Increments the active user count (for users who bypass the queue).
     */
    public void userEnteredDirectly() {
        currentActiveUsers++;
    }

    /**
     * Removes a specific session from the queue and releases any slot it held.
     * <ul>
     *   <li>An {@code ADMITTED} entry is marked {@code LEFT} and frees its active slot.</li>
     *   <li>A {@code WAITING} entry is marked {@code LEFT} (it held no active slot).</li>
     *   <li>A session with no current entry is treated as a direct-entry user that
     *       bypassed the queue, so its active slot is released.</li>
     * </ul>
     * Idempotent: an entry already {@code LEFT} is ignored, so a slot is never
     * released twice for the same enqueued/admitted session.
     */
    public void leave(UUID sessionId) {
        if (sessionId == null) {
            return;
        }
        List<QueueEntry> sessionEntries = entries.stream()
                .filter(e -> sessionId.equals(e.getSessionId()))
                .collect(Collectors.toList());
        QueueEntry active = sessionEntries.stream()
                .filter(e -> e.getStatus() != QueueEntryStatus.LEFT)
                .reduce((first, second) -> second)
                .orElse(null);
        if (active == null) {
            // No active entry. If the session was never enqueued it is a direct-entry
            // user releasing the slot it held; if it already has LEFT entries it has
            // left before, so this is an idempotent no-op (no second slot released).
            if (sessionEntries.isEmpty() && currentActiveUsers > 0) {
                currentActiveUsers--;
            }
            return;
        }
        if (active.getStatus() == QueueEntryStatus.ADMITTED && currentActiveUsers > 0) {
            currentActiveUsers--;
        }
        active.leave();
    }

    /**
     * Updates the queue configuration (Admin action).
     */
    public void updateConfig(QueueConfig newConfig) {
        if (newConfig == null) throw new IllegalArgumentException("Config cannot be null");
        this.config = newConfig;
    }

    /**
     * Flushes/clears the queue (Admin emergency action).
     */
    public void flush() {
        entries.stream().filter(QueueEntry::isWaiting).forEach(QueueEntry::leave);
    }

    public void deactivate() { this.active = false; }
    public void activate() { this.active = true; }

    public int getWaitingCount() {
        return (int) entries.stream().filter(QueueEntry::isWaiting).count();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        VirtualQueue that = (VirtualQueue) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    public VirtualQueueDto toVirtualQueueDto(){
        return new VirtualQueueDto(id, eventId, config.getThreshold(), config.getFlowRate(), active, currentActiveUsers, getWaitingCount(), entries.stream().map(QueueEntry::toQueueDto).toList());
    }

    public VirtualQueue detachedCopy() {
        return new VirtualQueue(id, eventId, config, entries, active, currentActiveUsers, version);
    }
}
