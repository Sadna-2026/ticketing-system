package com.ticketing.domain.member.communication;

import java.time.Instant;
import java.util.UUID;

public record MemberLoggedOutEvent(
        UUID memberId,
        UUID previousSessionId,
        Instant occurredAt
) {
    public MemberLoggedOutEvent(UUID memberId, UUID previousSessionId) {
        this(memberId, previousSessionId, Instant.now());
    }
}