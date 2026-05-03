package com.ticketing.domain.order;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class ActiveOrder{

    private final UUID id;
    private final UUID sessionId;
    private final UUID memberId;
    private final UUID eventId;
    private final Instant createdAt;
     
    /**
     * Creates an ActiveOrder without a memberId (guest order).
     *
     * @param id the order's unique identifier
     * @param sessionId the session that owns this order
     * @param eventId the event this order is for
     * @param createdAt the creation timestamp (used for lock expiration)
     */
    public ActiveOrder(UUID id, UUID sessionId, UUID eventId, Instant createdAt) {
        this(id, sessionId, null, eventId, createdAt);
    }

    /**
     * Creates an ActiveOrder. memberId may be null for guest orders.
     *
     * @param id the order's unique identifier
     * @param sessionId the session that owns this order
     * @param memberId the member who owns this order (null for guests)
     * @param eventId the event this order is for
     * @param createdAt the creation timestamp (used for lock expiration)
     */
    public ActiveOrder(UUID id, UUID sessionId, UUID memberId, UUID eventId, Instant createdAt) {
        if (id == null) throw new IllegalArgumentException("Order ID is required");
        if (sessionId == null) throw new IllegalArgumentException("Session ID is required");
        if (eventId == null) throw new IllegalArgumentException("Event ID is required");
        if (createdAt == null) throw new IllegalArgumentException("CreatedAt is required");
        this.id = id;
        this.sessionId = sessionId;
        this.memberId = memberId;
        this.eventId = eventId;
        this.createdAt = createdAt;
    }

    public UUID getId() { return id; }
    
}